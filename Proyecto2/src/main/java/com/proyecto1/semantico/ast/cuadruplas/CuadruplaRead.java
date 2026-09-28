package com.proyecto1.semantico.ast.cuadruplas;

public record CuadruplaRead(String destino) implements Cuadrupla {
    @Override
    public String toStringLegible() { return "read " + destino; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
