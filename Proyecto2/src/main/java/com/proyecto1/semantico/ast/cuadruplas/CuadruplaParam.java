package com.proyecto1.semantico.ast.cuadruplas;

/** param valor empuja valor como argumento de la próxima call, en orden fuente. */
public record CuadruplaParam(String valor) implements Cuadrupla {
    @Override
    public String toStringLegible() { return "param " + valor; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
