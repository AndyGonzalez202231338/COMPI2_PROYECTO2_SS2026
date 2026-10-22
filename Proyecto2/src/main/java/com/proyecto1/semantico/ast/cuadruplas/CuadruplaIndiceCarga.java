package com.proyecto1.semantico.ast.cuadruplas;

public record CuadruplaIndiceCarga(String arreglo, String indice, String destino, String tipoElemento) implements Cuadrupla {

    public CuadruplaIndiceCarga(String arreglo, String indice, String destino) {
        this(arreglo, indice, destino, null);
    }

    @Override
    public String toStringLegible() {
        String base = destino + " = " + arreglo + "[" + indice + "]";
        return tipoElemento != null ? base + "  [" + tipoElemento + "]" : base;
    }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}