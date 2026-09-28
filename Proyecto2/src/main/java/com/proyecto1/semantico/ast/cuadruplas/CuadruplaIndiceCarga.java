package com.proyecto1.semantico.ast.cuadruplas;

/** destino = arreglo[indice]. */
public record CuadruplaIndiceCarga(String arreglo, String indice, String destino) implements Cuadrupla {
    @Override
    public String toStringLegible() { return destino + " = " + arreglo + "[" + indice + "]"; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
