package com.proyecto1.semantico.ast.cuadruplas;

/** etiqueta: la DEFINICIÓN del label (no se backpatchea, se emite directo). */
public record CuadruplaEtiqueta(String etiqueta) implements Cuadrupla {
    @Override
    public String toStringLegible() { return etiqueta + ":"; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
