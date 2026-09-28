package com.proyecto1.semantico.ast.cuadruplas;

/** destino = new clase lo traduce a malloc(sizeof(clase)). */
public record CuadruplaNew(String clase, String destino) implements Cuadrupla {
    @Override
    public String toStringLegible() { return destino + " = new " + clase; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
