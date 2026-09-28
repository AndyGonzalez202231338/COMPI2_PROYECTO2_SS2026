package com.proyecto1.ui.modelo;

import java.io.File;

/** Estado del proyecto (carpeta raiz) actualmente abierto. */
public class ProyectoUI {

    private File carpetaRaiz;

    public ProyectoUI() { this.carpetaRaiz = null; }
    public ProyectoUI(File carpetaRaiz) { this.carpetaRaiz = carpetaRaiz; }

    public File getCarpetaRaiz() { return carpetaRaiz; }
    public void setCarpetaRaiz(File carpetaRaiz) { this.carpetaRaiz = carpetaRaiz; }

    public boolean hayProyectoAbierto() {
        return carpetaRaiz != null && carpetaRaiz.isDirectory();
    }

    public String getNombreProyecto() {
        if (!hayProyectoAbierto()) return "(Sin proyecto abierto)";
        return carpetaRaiz.getName();
    }
}