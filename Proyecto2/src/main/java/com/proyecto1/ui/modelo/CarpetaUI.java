package com.proyecto1.ui.modelo;

import java.io.File;

/** Envuelve una carpeta del disco para el arbol de trabajo. */
public class CarpetaUI {

    private File carpeta;

    public CarpetaUI(File carpeta) { this.carpeta = carpeta; }

    public File getCarpeta() { return carpeta; }
    public void setCarpeta(File carpeta) { this.carpeta = carpeta; }
    public String getNombre() { return carpeta.getName(); }
    public String getRutaAbsoluta() { return carpeta.getAbsolutePath(); }

    @Override
    public String toString() { return getNombre(); }
}