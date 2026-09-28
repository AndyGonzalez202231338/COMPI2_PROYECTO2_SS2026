package com.proyecto1.semantico.ast.cuadruplas;

/** destino = valor. */
public record CuadruplaAsignacion(String valor, String destino) implements Cuadrupla {
    @Override
    public String toStringLegible() { return destino + " = " + valor; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
