package com.proyecto1.servicio;

import com.proyecto1.GramaticaPigLatin;
import com.proyecto1.GramaticaY;
import com.proyecto1.GramaticaZ;
import com.proyecto1.IndentTokenStream;
import com.proyecto1.LenguajeLexer;
import com.proyecto1.semantico.AnalizadorSemanticoPigLatin;
import com.proyecto1.semantico.AnalizadorSemanticoY;
import com.proyecto1.semantico.AnalizadorSemanticoZ;
import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.errores.ErrorSemantico;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.piglatin.ASTBuilderPigLatin;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.y.ASTBuilderY;
import com.proyecto1.semantico.z.ASTBuilderZ;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.proyecto1.codigo.c.OrquestadorC3DaC;
import com.proyecto1.semantico.ast.cuadruplas.Cuadrupla;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orquesta el pipeline completo (lexer -> parser -> AST -> semántico -> C3D) para
 * UN archivo, sin duplicar nada de esa lógica: solo instancia y encadena las piezas
 * que ya existen (patrón Strategy por extensión: un método privado por lenguaje,
 * todos con la misma forma).
 *
 */
public class ServicioAnalisis {

    private final File raizProyecto;

    /**
     * Acumulador del C3D consolidado del proyecto. Se resetea al empezar el análisis
     * de un .pig (que es el punto de entrada del proyecto) y se vuelca a un archivo
     * al terminarlo. Es ThreadLocal para no mezclar análisis concurrentes; estático
     * para compartirse entre instancias de ServicioAnalisis (CargadorImports crea
     * la suya, pero el acumulador es el mismo).
     */
    private static final ThreadLocal<StringBuilder> acumuladorC3D =
            ThreadLocal.withInitial(StringBuilder::new);

    public ServicioAnalisis() {
        this(null);
    }

    public ServicioAnalisis(File raizProyecto) {
        this.raizProyecto = raizProyecto;
    }

    public ResultadoAnalisis analizar(File archivo, String texto) {
        String extension = extensionDe(archivo);
        boolean esRaiz = "pig".equals(extension);
        if (esRaiz) {
            acumuladorC3D.get().setLength(0);   // arranca el consolidado del proyecto
        }
        try {
            ResultadoAnalisis resultado = switch (extension) {
                case "y" -> analizarY(texto, archivo);
                case "z" -> analizarZ(texto, archivo);
                case "pig" -> analizarPigLatin(texto, archivo);
                default -> ResultadoAnalisis.extensionNoSoportada(archivo.getName());
            };
            if (esRaiz) {
                escribirConsolidadoC3D(archivo);
            }
            return resultado;
        } catch (Exception ex) {
            String detalle = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            if (esRaiz) {
                escribirConsolidadoC3D(archivo);
            }
            return ResultadoAnalisis.errorInterno(etiquetaLenguaje(extension), detalle);
        }
    }

    private ResultadoAnalisis analizarY(String texto, File archivo) {
        ListenerErroresANTLR listener = new ListenerErroresANTLR();

        LenguajeLexer lexer = new LenguajeLexer(CharStreams.fromString(texto));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        IndentTokenStream tokens = new IndentTokenStream(lexer);

        GramaticaY parser = new GramaticaY(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        GramaticaY.ProgramaContext arbol = parser.programa();

        List<ErrorSemantico> erroresSemanticos = List.of();
        AmbitoGlobal ambitoGlobal = null;
        GeneradorC3D generadorC3D = null;
        if (!listener.tieneErrores()) {
            com.proyecto1.semantico.ast.y.Programa programa = new ASTBuilderY().construir(arbol);
            ambitoGlobal = new AmbitoGlobal();
            ManejadorErrores errores = new AnalizadorSemanticoY().analizar(programa, ambitoGlobal);
            erroresSemanticos = errores.obtenerErrores();

            if (erroresSemanticos.isEmpty()) {
                try {
                    generadorC3D = new GeneradorC3D(ambitoGlobal);
                    programa.generarC3D(generadorC3D);
                    System.out.println("[C3D] Generado correctamente (Y?): "
                            + generadorC3D.getCuadruplas().size() + " cuádruplas, "
                            + generadorC3D.getFirmas().size() + " función(es).");

                    imprimirCuadruplas(generadorC3D, "Y?", archivo);
                    generarArchivoC(generadorC3D, archivo, "_", null, ambitoGlobal, Map.of());

                } catch (Exception exC3D) {
                    String detalle = exC3D.getMessage() != null ? exC3D.getMessage() : exC3D.getClass().getSimpleName();
                    System.out.println("[C3D] Error generando código: " + detalle);
                    generadorC3D = null;
                }
            }
        }

        ResultadoAnalisis base = ResultadoAnalisis.conErrores("Y?", listener.getErroresLexicos(),
                listener.getErroresSintacticos(), erroresSemanticos, List.of(), contarLineas(texto), ambitoGlobal);

        return (generadorC3D != null) ? ResultadoAnalisis.conC3D(base, generadorC3D) : base;
    }

    private ResultadoAnalisis analizarZ(String texto, File archivo) {
        String nombreArchivo = archivo.getName();
        ListenerErroresANTLR listener = new ListenerErroresANTLR();

        LenguajeLexer lexer = new LenguajeLexer(CharStreams.fromString(texto));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GramaticaZ parser = new GramaticaZ(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        GramaticaZ.CompilationUnitContext arbol = parser.compilationUnit();

        List<ErrorSemantico> erroresSemanticos = List.of();
        List<ErrorSemantico> advertencias = List.of();
        AmbitoGlobal ambitoGlobalExportado = null;
        GeneradorC3D generadorC3D = null;
        if (!listener.tieneErrores()) {
            com.proyecto1.semantico.ast.z.Clase clase = new ASTBuilderZ().construir(arbol);
            AmbitoGlobal ambitoInterno = new CargadorClasesZ().cargar(archivo, raizProyecto);
            ManejadorErrores errores = new AnalizadorSemanticoZ().analizar(clase, ambitoInterno);
            erroresSemanticos = errores.obtenerErrores();

            Simbolo sClase = ambitoInterno.resolverLocal(clase.getNombre());
            if (sClase != null) {
                ambitoGlobalExportado = new AmbitoGlobal();
                ambitoGlobalExportado.declarar(sClase);
            }

            String nombreEsperado = clase.getNombre() + ".z";
            if (!nombreEsperado.equals(nombreArchivo)) {
                advertencias = List.of(new ErrorSemantico(clase.getLinea(), clase.getColumna(),
                        "El nombre del archivo ('" + nombreArchivo + "') no coincide con el de la clase pública ('"
                                + nombreEsperado + "')."));
            }

            if (erroresSemanticos.isEmpty()) {
                try {
                    generadorC3D = new GeneradorC3D(ambitoInterno);
                    clase.generarC3D(generadorC3D);
                    System.out.println("[C3D] Generado correctamente (Zetariano, clase '"
                            + clase.getNombre() + "'): " + generadorC3D.getCuadruplas().size()
                            + " cuádruplas, " + generadorC3D.getFirmas().size() + " método(s)/constructor(es).");

                    // Firmas de las clases HERMANAS (todas las clases de ambitoInterno
                    // menos la actual). Se emiten como prototipos para que este .c
                    // pueda referenciar métodos de la otra clase (Nodo <-> Pila) sin
                    // que el compilador de C falle con "implicit declaration".
                    Map<String, GeneradorC3D.Firma> firmasHermanas =
                            recolectarFirmasImportadas(ambitoInterno, clase.getNombre());

                    imprimirCuadruplas(generadorC3D, "Zetariano", archivo);
                    generarArchivoC(generadorC3D, archivo, "_", null, ambitoInterno, firmasHermanas);

                } catch (Exception exC3D) {
                    String detalle = exC3D.getMessage() != null ? exC3D.getMessage() : exC3D.getClass().getSimpleName();
                    System.out.println("[C3D] Error generando código: " + detalle);
                    generadorC3D = null;
                }
            }
        }

        ResultadoAnalisis base = ResultadoAnalisis.conErrores("Zetariano", listener.getErroresLexicos(),
                listener.getErroresSintacticos(), erroresSemanticos, advertencias, contarLineas(texto), ambitoGlobalExportado);

        return (generadorC3D != null) ? ResultadoAnalisis.conC3D(base, generadorC3D) : base;
    }

    private ResultadoAnalisis analizarPigLatin(String texto, File archivo) {
        ListenerErroresANTLR listener = new ListenerErroresANTLR();

        LenguajeLexer lexer = new LenguajeLexer(CharStreams.fromString(texto));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GramaticaPigLatin parser = new GramaticaPigLatin(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        GramaticaPigLatin.ProgramaContext arbol = parser.programa();

        List<ErrorSemantico> erroresSemanticos = List.of();
        List<ErrorSemantico> advertencias = List.of();
        GeneradorC3D generadorC3D = null;
        if (!listener.tieneErrores()) {
            com.proyecto1.semantico.ast.piglatin.Programa programa = new ASTBuilderPigLatin().construir(arbol);
            CargadorImports.Resultado imports =
                    new CargadorImports(this, raizProyecto).cargar(programa.getImportaciones(), archivo);
            AnalizadorSemanticoPigLatin.Resultado resultadoPig =
                    new AnalizadorSemanticoPigLatin().analizar(programa, imports.getAmbitoGlobal());
            ManejadorErrores errores = resultadoPig.errores();
            AmbitoGlobal globalPig = resultadoPig.globalPig();

            List<ErrorSemantico> todos = new ArrayList<>(imports.getErrores());
            todos.addAll(errores.obtenerErrores());
            todos.sort(Comparator.comparingInt(ErrorSemantico::getLinea).thenComparingInt(ErrorSemantico::getColumna));
            erroresSemanticos = todos;
            advertencias = imports.getAdvertencias();

            if (todos.isEmpty()) {
                try {
                    generadorC3D = new GeneradorC3D(globalPig);
                    programa.generarC3D(generadorC3D);
                    System.out.println("[C3D] Generado correctamente (PigLatin): "
                            + generadorC3D.getCuadruplas().size() + " cuádruplas, "
                            + generadorC3D.getFirmas().size() + " función(es).");

                    Map<String, GeneradorC3D.Firma> firmasExternas =
                            recolectarFirmasImportadas(imports.getAmbitoGlobal(), null);

                    imprimirCuadruplas(generadorC3D, "PigLatin", archivo);
                    // Ahora pasamos globalPig (con las globales del .pig + padre de imports)
                    // como ambitoGlobalUsado. Ese es el ámbito que OrquestadorC3DaC usa para
                    // declarar las variables globales y como fuente de tipos para InferenciaTiposC.
                    generarArchivoC(generadorC3D, archivo, "_", "main",
                            globalPig, firmasExternas);

                } catch (Exception exC3D) {
                    String detalle = exC3D.getMessage() != null ? exC3D.getMessage() : exC3D.getClass().getSimpleName();
                    System.out.println("[C3D] Error generando código: " + detalle);
                    generadorC3D = null;
                }
            }
        }

        ResultadoAnalisis base = ResultadoAnalisis.conErrores("PigLatin", listener.getErroresLexicos(),
                listener.getErroresSintacticos(), erroresSemanticos, advertencias, contarLineas(texto));

        return (generadorC3D != null) ? ResultadoAnalisis.conC3D(base, generadorC3D) : base;
    }

    private String extensionDe(File archivo) {
        String nombre = archivo.getName();
        int punto = nombre.lastIndexOf('.');
        return punto >= 0 ? nombre.substring(punto + 1).toLowerCase() : "";
    }

    private String etiquetaLenguaje(String extension) {
        return switch (extension) {
            case "y" -> "Y?";
            case "z" -> "Zetariano";
            case "pig" -> "PigLatin";
            default -> "Desconocido";
        };
    }

    private int contarLineas(String texto) {
        if (texto.isEmpty()) return 0;
        int lineas = 1;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') lineas++;
        }
        return lineas;
    }

    /**
     * Imprime la tabla de cuádruplas en stdout Y la acumula en el buffer global para
     * el archivo consolidado del proyecto. Se llama una vez por cada archivo
     * analizado (.y, .z, .pig) — el resultado final en el .txt tiene una sección por
     * archivo, con el lenguaje y el nombre del archivo en el encabezado.
     */
    private static void imprimirCuadruplas(GeneradorC3D generadorC3D, String lenguaje, File archivo) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Cuádruplas (").append(lenguaje).append(" — ")
                .append(archivo.getName()).append(") ===\n");
        int k = 0;
        for (Cuadrupla c : generadorC3D.getCuadruplas()) {
            sb.append(String.format("%3d: %s%n", k++, c.toStringLegible()));
        }
        sb.append("=== Fin (").append(generadorC3D.getCuadruplas().size())
                .append(" cuádruplas) ===\n\n");

        // A consola (como antes).
        System.out.print(sb);
        // Al acumulador del proyecto.
        acumuladorC3D.get().append(sb);
    }

    /**
     * Escribe el C3D acumulado de TODO el proyecto (los .y/.z importados + el .pig)
     * en un único archivo {@code <nombrePig>_C3D.txt} al lado del .pig.
     *
     * Se invoca desde #analizar(File, String) cuando el archivo analizado
     * es un .pig (el punto de entrada del proyecto) — es el único que tiene la
     * visión completa de los imports.
     */
    private static void escribirConsolidadoC3D(File archivoPig) {
        try {
            String ruta = archivoPig.getAbsolutePath();
            int punto = ruta.lastIndexOf('.');
            String salida = (punto >= 0 ? ruta.substring(0, punto) : ruta) + "_C3D.txt";
            Files.writeString(Path.of(salida), acumuladorC3D.get().toString());
            System.out.println("[C3D] Archivo consolidado: " + salida);
        } catch (Exception e) {
            System.out.println("[C3D] Error al escribir consolidado: " + e.getMessage());
        }
    }

    /**
     * Genera el archivo .c al lado del fuente.
     *
     * @param prefijoLenguaje       prefijo que se aplica a los nombres de función.
     * @param nombreFuncionEntrada  nombre del entry point en el C3D ("main" en PigLatin,
     *                              null en Y/Z que no tienen main propio).
     * @param ambitoGlobalUsado     ámbito del que se sacan los typedefs (structs/clases).
     * @param firmasExternas        firmas de funciones importadas (para prototipos).
     */
    private static void generarArchivoC(GeneradorC3D generadorC3D, File archivo,
                                        String prefijoLenguaje, String nombreFuncionEntrada,
                                        AmbitoGlobal ambitoGlobalUsado,
                                        Map<String, GeneradorC3D.Firma> firmasExternas) {
        try {
            List<Simbolo> tipos = (ambitoGlobalUsado != null)
                    ? ambitoGlobalUsado.getTiposDefinidos()
                    : List.of();
            Map<String, GeneradorC3D.Firma> externas = (firmasExternas != null)
                    ? firmasExternas
                    : Map.of();

            OrquestadorC3DaC orch = new OrquestadorC3DaC(
                    generadorC3D.getCuadruplas(),
                    generadorC3D.getFirmas(),
                    prefijoLenguaje,
                    nombreFuncionEntrada,
                    tipos,
                    externas,
                    ambitoGlobalUsado
            );
            String codigoC = orch.generarArchivoCompleto();

            String ruta = archivo.getAbsolutePath();
            int punto = ruta.lastIndexOf('.');
            String salida = (punto >= 0 ? ruta.substring(0, punto) : ruta) + ".c";

            Files.writeString(Path.of(salida), codigoC);
            System.out.println("[C] Archivo C generado: " + salida);
        } catch (Exception exC) {
            String detalle = exC.getMessage() != null ? exC.getMessage() : exC.getClass().getSimpleName();
            System.out.println("[C] Error generando C: " + detalle);
        }
    }

    // ---------- Recolección de firmas importadas / hermanas ----------

    /**
     * Construye el mapa etiqueta -> Firma de todas las funciones, métodos y
     * constructores declarados en un ámbito (típicamente el de imports de PigLatin,
     * o el de clases hermanas de Z).
     *
     * Cada firma trae la etiqueta YA MANGLADA porque el símbolo individual guarda
     * el nombre plano, no la etiqueta que usa el C3D:
     *   Función de Y: la etiqueta es s.getNombre() (sin sufijos {@code #}).
     *   Método de Z: {@code Clase_metodo}.
     *   Constructor de Z: {@code Clase_init_aN} (usa {@link GeneradorC3D#etiquetaConstructor}).
     *
     * @param nombreClaseExcluir si no es null, se omite la clase con ese nombre.
     *                           Se usa al generar el .c de una clase Z: sus propias
     *                           firmas ya van en el bloque de prototipos del archivo,
     *                           no hace falta duplicarlas como "externas".
     */
    private static Map<String, GeneradorC3D.Firma> recolectarFirmasImportadas(AmbitoGlobal ambito,
                                                                              String nombreClaseExcluir) {
        Map<String, GeneradorC3D.Firma> out = new HashMap<>();
        if (ambito == null) return out;

        for (Simbolo s : ambito.simbolosLocales()) {
            if (s.getCategoria() == CategoriaSimbolo.FUNCION) {
                String etiqueta = s.getNombre().split("#")[0];
                out.put(etiqueta, firmaDeFuncion(etiqueta, s));
            } else if (s.getCategoria() == CategoriaSimbolo.CLASE) {
                if (nombreClaseExcluir != null && nombreClaseExcluir.equals(s.getNombre())) continue;
                String nombreClase = s.getNombre();
                Tipo tipoThis = new com.proyecto1.semantico.tipos.TipoClase(s);

                for (Simbolo m : s.getMiembros().valores()) {
                    if (m.getCategoria() == CategoriaSimbolo.METODO) {
                        // Tipos formales del método (excluye "this").
                        List<com.proyecto1.semantico.tipos.Tipo> tiposM = new java.util.ArrayList<>();
                        for (Simbolo p : m.getParametros()) {
                            tiposM.add(p.getTipo() != null
                                    ? p.getTipo()
                                    : com.proyecto1.semantico.tipos.TipoPrimitivo.DESCONOCIDO);
                        }
                        String nombrePlanoM = m.getNombre().split("#")[0];
                        String etiquetaMetodo = GeneradorC3D.etiquetaMetodo(nombreClase, nombrePlanoM, tiposM);
                        out.put(etiquetaMetodo,
                                firmaDeMetodoOConstructor(etiquetaMetodo, m, tipoThis, m.getTipo()));
                    } else if (m.getCategoria() == CategoriaSimbolo.CONSTRUCTOR) {
                        // Tipos formales del constructor (excluye "this").
                        List<com.proyecto1.semantico.tipos.Tipo> tiposC = new java.util.ArrayList<>();
                        for (Simbolo p : m.getParametros()) {
                            tiposC.add(p.getTipo() != null
                                    ? p.getTipo()
                                    : com.proyecto1.semantico.tipos.TipoPrimitivo.DESCONOCIDO);
                        }
                        String etiquetaCtor = GeneradorC3D.etiquetaConstructor(nombreClase, tiposC);
                        out.put(etiquetaCtor,
                                firmaDeMetodoOConstructor(etiquetaCtor, m, tipoThis, TipoPrimitivo.VOID));
                    }
                }
            }
        }
        return out;
    }

    /** Firma de una función suelta de Y (sin "this"). */
    private static GeneradorC3D.Firma firmaDeFuncion(String etiqueta, Simbolo f) {
        List<GeneradorC3D.ParametroFirma> params = new ArrayList<>();
        for (Simbolo p : f.getParametros()) {
            params.add(new GeneradorC3D.ParametroFirma(p.getNombre(), p.getTipo()));
        }
        return new GeneradorC3D.Firma(etiqueta, params, f.getTipo(), false);
    }

    /** Firma de un método o constructor de Z (con "this" como primer parámetro). */
    private static GeneradorC3D.Firma firmaDeMetodoOConstructor(String etiqueta, Simbolo m,
                                                                Tipo tipoThis, Tipo tipoRetorno) {
        List<GeneradorC3D.ParametroFirma> params = new ArrayList<>();
        params.add(new GeneradorC3D.ParametroFirma("this", tipoThis));
        for (Simbolo p : m.getParametros()) {
            params.add(new GeneradorC3D.ParametroFirma(p.getNombre(), p.getTipo()));
        }
        return new GeneradorC3D.Firma(etiqueta, params, tipoRetorno, true);
    }
}