package com.proyecto1.ui.modelo;

/**
 * Una fila de la tabla de simbolos del IDE. Es una vista plana (solo texto) de un
 * Simbolo de la tabla de simbolos del compilador, pensada para mostrarse en una
 * TableView. El recolector (RecolectorSimbolos) decide que poner en cada columna.
 */
public final class FilaSimbolo {

    private final String nombre;       // nombre en el codigo fuente
    private final String categoria;    // VARIABLE, PARAMETRO, FUNCION, CLASE, ...
    private final String tipo;         // tipo de dato o de retorno
    private final String ambito;       // donde vive (global, nombre de clase, nombre de funcion)
    private final String modificador;  // public/private/protected/default o "-"
    private final int fila;            // linea de declaracion (base 1); 0 = sin posicion
    private final int columna;         // columna de declaracion (base 1); 0 = sin posicion
    private final String extra;        // info adicional segun la categoria (offset, tamano, classId, ...)

    public FilaSimbolo(String nombre, String categoria, String tipo, String ambito,
                       String modificador, int fila, int columna, String extra) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.tipo = tipo;
        this.ambito = ambito;
        this.modificador = modificador;
        this.fila = fila;
        this.columna = columna;
        this.extra = extra;
    }

    public String getNombre()      { return nombre; }
    public String getCategoria()   { return categoria; }
    public String getTipo()        { return tipo; }
    public String getAmbito()      { return ambito; }
    public String getModificador() { return modificador; }
    public int getFila()           { return fila; }
    public int getColumna()        { return columna; }
    public String getExtra()       { return extra; }
}
