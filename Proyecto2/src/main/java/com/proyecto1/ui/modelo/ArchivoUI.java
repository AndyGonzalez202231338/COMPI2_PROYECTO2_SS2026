package com.proyecto1.ui.modelo;

import java.io.File;
import java.util.Locale;

/**
 * Envuelve un archivo del disco con informacion util para la UI
 * (estado modificado, lenguaje detectado segun extension).
 */
public class ArchivoUI {

    public enum Lenguaje {
        Y_INTERROGACION("Y?"),
        ZETARIANO("Zetariano"),
        PIG_LATIN("PigLatin"),
        DESCONOCIDO("Desconocido");

        private final String nombreVisible;
        Lenguaje(String nombreVisible) { this.nombreVisible = nombreVisible; }
        public String getNombreVisible() { return nombreVisible; }
    }

    private File archivo;
    private boolean modificado;

    public ArchivoUI(File archivo) {
        this.archivo = archivo;
        this.modificado = false;
    }

    public File getArchivo() { return archivo; }
    public void setArchivo(File archivo) { this.archivo = archivo; }
    public String getNombre() { return archivo.getName(); }
    public String getRutaAbsoluta() { return archivo.getAbsolutePath(); }
    public boolean isModificado() { return modificado; }
    public void setModificado(boolean modificado) { this.modificado = modificado; }

    public String getExtension() {
        String nombre = archivo.getName();
        int idx = nombre.lastIndexOf('.');
        if (idx < 0 || idx == nombre.length() - 1) return "";
        return nombre.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    public Lenguaje getLenguaje() {
        switch (getExtension()) {
            case "y":   return Lenguaje.Y_INTERROGACION;
            case "z":   return Lenguaje.ZETARIANO;
            case "pig": return Lenguaje.PIG_LATIN;
            default:    return Lenguaje.DESCONOCIDO;
        }
    }

    public String getNombreParaMostrar() {
        return modificado ? getNombre() + " *" : getNombre();
    }

    @Override
    public String toString() { return getNombreParaMostrar(); }
}