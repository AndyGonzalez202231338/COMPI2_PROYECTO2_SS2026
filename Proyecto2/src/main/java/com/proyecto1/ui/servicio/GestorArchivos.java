package com.proyecto1.ui.servicio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Operaciones sobre el sistema de ficheros real. */
public final class GestorArchivos {

    private static final Set<String> EXTENSIONES_SOPORTADAS = Set.of("y", "z", "pig");

    private GestorArchivos() {}

    public static String abrirArchivo(File archivo) throws IOException {
        byte[] bytes = Files.readAllBytes(archivo.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void guardarArchivo(File archivo, String contenido) throws IOException {
        Files.write(archivo.toPath(), contenido.getBytes(StandardCharsets.UTF_8));
    }

    public static void crearArchivo(File archivo, String contenido) throws IOException {
        if (archivo.exists()) {
            throw new IOException("El archivo ya existe: " + archivo.getAbsolutePath());
        }
        File carpetaPadre = archivo.getParentFile();
        if (carpetaPadre != null && !carpetaPadre.exists()) {
            Files.createDirectories(carpetaPadre.toPath());
        }
        Files.write(archivo.toPath(), contenido.getBytes(StandardCharsets.UTF_8));
    }

    public static void crearCarpeta(File carpeta) throws IOException {
        if (carpeta.exists()) {
            throw new IOException("La carpeta ya existe: " + carpeta.getAbsolutePath());
        }
        Files.createDirectories(carpeta.toPath());
    }

    public static void eliminar(File archivoOCarpeta) throws IOException {
        if (!archivoOCarpeta.exists()) return;
        if (archivoOCarpeta.isDirectory()) {
            File[] hijos = archivoOCarpeta.listFiles();
            if (hijos != null) {
                for (File hijo : hijos) eliminar(hijo);
            }
        }
        Files.delete(archivoOCarpeta.toPath());
    }

    public static File renombrar(File archivoOCarpeta, String nuevoNombre) throws IOException {
        File destino = new File(archivoOCarpeta.getParentFile(), nuevoNombre);
        if (destino.exists()) {
            throw new IOException("Ya existe un elemento con ese nombre: " + destino.getAbsolutePath());
        }
        Files.move(archivoOCarpeta.toPath(), destino.toPath(), StandardCopyOption.ATOMIC_MOVE);
        return destino;
    }

    public static File mover(File origen, File carpetaDestino) throws IOException {
        if (!carpetaDestino.isDirectory()) {
            throw new IOException("El destino no es una carpeta: " + carpetaDestino.getAbsolutePath());
        }
        File destino = new File(carpetaDestino, origen.getName());
        if (destino.exists()) {
            throw new IOException("Ya existe un elemento con ese nombre en el destino: " + destino.getAbsolutePath());
        }
        Path rutaFinal = Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.ATOMIC_MOVE);
        return rutaFinal.toFile();
    }

    public static File duplicar(File archivo) throws IOException {
        String nombre = archivo.getName();
        String base = nombre;
        String extension = "";
        int idx = nombre.lastIndexOf('.');
        if (idx > 0) {
            base = nombre.substring(0, idx);
            extension = nombre.substring(idx);
        }
        File carpetaPadre = archivo.getParentFile();
        File destino = new File(carpetaPadre, base + "_copia" + extension);
        int contador = 1;
        while (destino.exists()) {
            contador++;
            destino = new File(carpetaPadre, base + "_copia" + contador + extension);
        }
        Files.copy(archivo.toPath(), destino.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return destino;
    }

    public static List<File> listarHijos(File carpeta) {
        File[] hijos = carpeta.listFiles();
        if (hijos == null) return new ArrayList<>();
        List<File> lista = new ArrayList<>(Arrays.asList(hijos));
        lista.sort(Comparator
                .comparing(File::isFile)
                .thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        return lista;
    }

    public static boolean esArchivoSoportado(File archivo) {
        String nombre = archivo.getName();
        int idx = nombre.lastIndexOf('.');
        if (idx < 0 || idx == nombre.length() - 1) return false;
        return EXTENSIONES_SOPORTADAS.contains(nombre.substring(idx + 1).toLowerCase(Locale.ROOT));
    }
}