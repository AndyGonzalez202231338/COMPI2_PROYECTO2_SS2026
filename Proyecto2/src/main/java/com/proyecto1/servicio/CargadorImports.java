package com.proyecto1.servicio;

import com.proyecto1.semantico.ast.piglatin.Importacion;
import com.proyecto1.semantico.errores.ErrorSemantico;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CargadorImports {

    /** Resultado de cargar los imports: el ámbito con todos los símbolos importados + los errores encontrados. */
    public static final class Resultado {
        private final AmbitoGlobal ambitoGlobal;
        private final List<ErrorSemantico> errores;
        private final List<ErrorSemantico> advertencias;

        private Resultado(AmbitoGlobal ambitoGlobal, List<ErrorSemantico> errores, List<ErrorSemantico> advertencias) {
            this.ambitoGlobal = ambitoGlobal;
            this.errores = errores;
            this.advertencias = advertencias;
        }

        public AmbitoGlobal getAmbitoGlobal() { return ambitoGlobal; }
        public List<ErrorSemantico> getErrores() { return errores; }
        /** Avisos que no impiden compilar (p. ej. una estructura de Y y una clase de Z con el mismo nombre). */
        public List<ErrorSemantico> getAdvertencias() { return advertencias; }
    }

    private static final String[] EXTENSIONES = {"y", "z"};
    private static final int PROFUNDIDAD_MAXIMA = 6;
    private static final Set<String> CARPETAS_IGNORADAS = Set.of("target", "node_modules", "build", "out");

    private final ServicioAnalisis servicio;
    private final File raizProyecto; // puede ser null

    public CargadorImports(ServicioAnalisis servicio, File raizProyecto) {
        this.servicio = servicio;
        this.raizProyecto = raizProyecto;
    }

    public Resultado cargar(List<Importacion> importaciones, File archivoPig) {
        AmbitoGlobal combinado = new AmbitoGlobal();
        List<ErrorSemantico> errores = new ArrayList<>();
        List<ErrorSemantico> advertencias = new ArrayList<>();
        Set<Path> yaCargados = new HashSet<>();
        Map<String, String> archivoDeCadaSimbolo = new HashMap<>();

        File carpetaDelPig = archivoPig.getAbsoluteFile().getParentFile();

        for (Importacion imp : importaciones) {
            String nombreImport = String.join(".", imp.getSegmentos());

            // "import Utilidades.y": el ultimo segmento es la extension, no una subcarpeta.
            List<String> segmentos = imp.getSegmentos();
            String[] extensiones = EXTENSIONES;
            if (segmentos.size() > 1 && esExtension(segmentos.get(segmentos.size() - 1))) {
                extensiones = new String[] {segmentos.get(segmentos.size() - 1).toLowerCase()};
                segmentos = segmentos.subList(0, segmentos.size() - 1);
            }

            List<File> archivos = localizar(segmentos, extensiones, carpetaDelPig);

            if (archivos.isEmpty()) {
                String ultimo = segmentos.get(segmentos.size() - 1);
                StringBuilder buscados = new StringBuilder();
                for (String ext : extensiones) {
                    if (buscados.length() > 0) buscados.append(" y ");
                    buscados.append(ultimo).append('.').append(ext);
                }
                errores.add(new ErrorSemantico(imp.getLinea(), imp.getColumna(),
                        "No se encontró el archivo importado '" + nombreImport + "' (se buscó "
                                + buscados + " junto al archivo .pig y en el proyecto)."));
                continue;
            }
            for (File archivo : archivos) {
                Path clave = archivo.toPath().toAbsolutePath().normalize();
                if (!yaCargados.add(clave)) continue; // el mismo archivo importado dos veces: se carga una sola
                incorporar(archivo, imp, combinado, archivoDeCadaSimbolo, errores, advertencias);
            }
        }
        return new Resultado(combinado, errores, advertencias);
    }

    /** Analiza un archivo importado y vuelca sus símbolos en el ámbito combinado. */
    private void incorporar(File archivo, Importacion imp, AmbitoGlobal combinado,
                            Map<String, String> archivoDeCadaSimbolo, List<ErrorSemantico> errores,
                            List<ErrorSemantico> advertencias) {
        String texto;
        try {
            texto = new String(Files.readAllBytes(archivo.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            errores.add(new ErrorSemantico(imp.getLinea(), imp.getColumna(),
                    "No se pudo leer el archivo importado '" + archivo.getName() + "': " + ex.getMessage()));
            return;
        }

        ResultadoAnalisis analisis = servicio.analizar(archivo, texto);

        if (!analisis.isExito()) {
            errores.add(new ErrorSemantico(imp.getLinea(), imp.getColumna(),
                    "El archivo importado '" + archivo.getName() + "' tiene " + analisis.getTotalErrores()
                            + " error(es); corríjalo primero. " + primerError(analisis)));
        }

        AmbitoGlobal global = analisis.getAmbitoGlobal();
        if (global == null) return; // sin AST no hay símbolos que importar (errores de sintaxis)

        for (Simbolo simbolo : global.simbolosLocales()) {
            Simbolo previo = combinado.resolverLocal(simbolo.getNombre());
            if (previo == null) {
                combinado.declarar(simbolo);
                archivoDeCadaSimbolo.put(simbolo.getNombre(), archivo.getName());
                continue;
            }

            String archivoPrevio = archivoDeCadaSimbolo.get(simbolo.getNombre());
            boolean estructuraYClase =
                    (previo.getCategoria() == CategoriaSimbolo.ESTRUCTURA && simbolo.getCategoria() == CategoriaSimbolo.CLASE)
                            || (previo.getCategoria() == CategoriaSimbolo.CLASE && simbolo.getCategoria() == CategoriaSimbolo.ESTRUCTURA);

            if (estructuraYClase) {
                // Una estructura de un .y y una clase de un .z con el mismo nombre NO es un error:
                // en el .pig gana la clase (es la unica que se puede instanciar con "novus X(...)"
                // y que tiene metodos). La estructura sigue funcionando dentro de su propio .y.
                boolean nuevoEsClase = simbolo.getCategoria() == CategoriaSimbolo.CLASE;
                String archivoEstructura = nuevoEsClase ? archivoPrevio : archivo.getName();
                String archivoClase = nuevoEsClase ? archivo.getName() : archivoPrevio;
                if (nuevoEsClase) {
                    combinado.reemplazar(simbolo);
                    archivoDeCadaSimbolo.put(simbolo.getNombre(), archivo.getName());
                }
                advertencias.add(new ErrorSemantico(imp.getLinea(), imp.getColumna(),
                        "'" + simbolo.getNombre() + "' está definido como estructura en '" + archivoEstructura
                                + "' y como clase en '" + archivoClase + "'; en este .pig se usa la clase."));
            } else {
                errores.add(new ErrorSemantico(imp.getLinea(), imp.getColumna(),
                        "'" + simbolo.getNombre() + "' de '" + archivo.getName() + "' ya fue importado desde '"
                                + archivoPrevio + "'."));
            }
        }
    }

    private String primerError(ResultadoAnalisis r) {
        ErrorSemantico primero = null;
        if (!r.getErroresLexicos().isEmpty()) primero = r.getErroresLexicos().get(0);
        else if (!r.getErroresSintacticos().isEmpty()) primero = r.getErroresSintacticos().get(0);
        else if (!r.getErroresSemanticos().isEmpty()) primero = r.getErroresSemanticos().get(0);
        if (primero == null) return "";
        return "Primer error: línea " + primero.getLinea() + " - " + primero.getMensaje();
    }


    // Búsqueda de archivos

    private static boolean esExtension(String segmento) {
        for (String ext : EXTENSIONES) if (ext.equalsIgnoreCase(segmento)) return true;
        return false;
    }

    private List<File> localizar(List<String> segmentos, String[] extensiones, File carpetaDelPig) {
        List<File> raices = new ArrayList<>();
        if (carpetaDelPig != null) raices.add(carpetaDelPig);
        if (raizProyecto != null && !raizProyecto.getAbsoluteFile().equals(carpetaDelPig)) {
            raices.add(raizProyecto.getAbsoluteFile());
        }

        String ultimo = segmentos.get(segmentos.size() - 1);
        for (File raiz : raices) {
            List<File> encontrados = new ArrayList<>();
            for (String ext : extensiones) {
                File directo = raiz;
                for (String segmento : segmentos) directo = new File(directo, segmento);
                directo = new File(directo.getPath() + "." + ext);
                if (directo.isFile()) {
                    encontrados.add(directo);
                    continue;
                }
                File enSubcarpeta = buscarPorNombre(raiz, ultimo + "." + ext);
                if (enSubcarpeta != null) encontrados.add(enSubcarpeta);
            }
            if (!encontrados.isEmpty()) return encontrados;
        }
        return List.of();
    }

    /** Busca un archivo por nombre exacto bajo {@code raiz}; si hay varios, gana el menos profundo. */
    private File buscarPorNombre(File raiz, String nombreArchivo) {
        Path inicio = raiz.toPath();
        List<Path> coincidencias = new ArrayList<>();
        try {
            Files.walkFileTree(inicio, EnumSet.noneOf(java.nio.file.FileVisitOption.class), PROFUNDIDAD_MAXIMA,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (!dir.equals(inicio)) {
                                String nombre = dir.getFileName().toString();
                                if (nombre.startsWith(".") || CARPETAS_IGNORADAS.contains(nombre)) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path archivo, BasicFileAttributes attrs) {
                            if (archivo.getFileName().toString().equals(nombreArchivo)) coincidencias.add(archivo);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path archivo, IOException ex) {
                            return FileVisitResult.CONTINUE; // sin permisos, etc.: se ignora
                        }
                    });
        } catch (IOException ex) {
            return null;
        }
        return coincidencias.stream()
                .min(Comparator.<Path>comparingInt(Path::getNameCount).thenComparing(Path::toString))
                .map(Path::toFile)
                .orElse(null);
    }
}