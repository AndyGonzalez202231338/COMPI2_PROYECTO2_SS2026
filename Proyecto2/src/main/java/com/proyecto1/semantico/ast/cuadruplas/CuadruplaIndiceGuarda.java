package com.proyecto1.semantico.ast.cuadruplas;

/**
 * arreglo[indice] = valor. a diferencia de la mayoría, el tercer campo
 * es el VALOR a guardar, no un destino. antes esto vivía silenciosamente en el
 * campo "resultado" de la cuádrupla genérica; ahora el nombre del campo
 * ("valor") lo deja explícito.
 */
public record CuadruplaIndiceGuarda(String arreglo, String indice, String valor) implements Cuadrupla {
    @Override
    public String toStringLegible() { return arreglo + "[" + indice + "] = " + valor; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
