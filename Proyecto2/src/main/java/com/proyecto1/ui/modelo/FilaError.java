package com.proyecto1.ui.modelo;

/**
 * Una fila de la tabla de errores del IDE: tipo, fila, columna y descripcion.
 *
 * Fila y columna se guardan YA en base 1 (igual que la barra de estado del editor). Los
 * errores del backend traen la columna en base 0 (asi la entrega ANTLR), por eso
 * {@link #desde(Tipo, int, int, String)} le suma 1. Un valor <= 0 significa "sin posicion"
 * (p. ej. un error interno) y la tabla lo muestra como "-".
 */
public final class FilaError {

    public enum Tipo {
        LEXICO("Léxico"),
        SINTACTICO("Sintáctico"),
        SEMANTICO("Semántico"),
        ADVERTENCIA("Advertencia");

        private final String etiqueta;
        Tipo(String etiqueta) { this.etiqueta = etiqueta; }
        public String getEtiqueta() { return etiqueta; }
    }

    private final Tipo tipo;
    private final int fila;
    private final int columna;
    private final String descripcion;

    public FilaError(Tipo tipo, int fila, int columna, String descripcion) {
        this.tipo = tipo;
        this.fila = fila;
        this.columna = columna;
        this.descripcion = descripcion;
    }

    /** Crea una fila a partir de la posicion tal cual la reporta el backend (linea base 1, columna base 0). */
    public static FilaError desde(Tipo tipo, int linea, int columnaBase0, String descripcion) {
        boolean tienePosicion = linea > 0;
        return new FilaError(tipo, tienePosicion ? linea : 0, tienePosicion ? columnaBase0 + 1 : 0, descripcion);
    }

    public Tipo getTipo() { return tipo; }
    public int getFila() { return fila; }
    public int getColumna() { return columna; }
    public String getDescripcion() { return descripcion; }
}