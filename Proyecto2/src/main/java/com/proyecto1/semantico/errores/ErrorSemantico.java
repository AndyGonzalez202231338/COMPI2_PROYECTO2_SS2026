package com.proyecto1.semantico.errores;

/**
 * Un error semántico reportado por CUALQUIER nodo del AST durante su propio
 * verificar(...). No se sabe (ni le importa a esta clase) qué nodo lo generó el
 * nodo que detecta el problema es responsable de armar un mensaje que se entienda
 * solo, con su línea/columna exacta.
 */
public final class ErrorSemantico {

    private final int linea;
    private final int columna;
    private final String mensaje;

    public ErrorSemantico(int linea, int columna, String mensaje) {
        this.linea = linea;
        this.columna = columna;
        this.mensaje = mensaje;
    }

    public int getLinea() { return linea; }
    public int getColumna() { return columna; }
    public String getMensaje() { return mensaje; }

    @Override
    public String toString() {
        return "[línea " + linea + ":" + columna + "] " + mensaje;
    }
}
