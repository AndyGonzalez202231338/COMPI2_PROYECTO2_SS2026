package com.proyecto1.semantico.ast.cuadruplas;

public record CuadruplaPrint(String valor) implements Cuadrupla {
    @Override
    public String toStringLegible() { return "print " + valor; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
