package com.proyecto1.semantico.ast.cuadruplas;

/** end_func sin campos propios, cierra el begin_func más reciente. */
public record CuadruplaEndFunc() implements Cuadrupla {
    @Override
    public String toStringLegible() { return "end_func"; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
