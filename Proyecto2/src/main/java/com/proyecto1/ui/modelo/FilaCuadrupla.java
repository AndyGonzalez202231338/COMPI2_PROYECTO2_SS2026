package com.proyecto1.ui.modelo;

/**
 * Una fila de la tabla de cuadruplas del IDE. Descompone una cuadrupla en sus cuatro
 * campos clasicos (operador, operando 1, operando 2, resultado) para mostrarse
 * ordenada en una TableView, ademas de un indice y la representacion legible completa.
 */
public final class FilaCuadrupla {

    private final int indice;
    private final String operador;
    private final String operando1;
    private final String operando2;
    private final String resultado;
    private final String legible;   // la cuadrupla tal como la imprime toStringLegible()

    public FilaCuadrupla(int indice, String operador, String operando1,
                         String operando2, String resultado, String legible) {
        this.indice = indice;
        this.operador = operador;
        this.operando1 = operando1;
        this.operando2 = operando2;
        this.resultado = resultado;
        this.legible = legible;
    }

    public int getIndice()        { return indice; }
    public String getOperador()   { return operador; }
    public String getOperando1()  { return operando1; }
    public String getOperando2()  { return operando2; }
    public String getResultado()  { return resultado; }
    public String getLegible()    { return legible; }
}
