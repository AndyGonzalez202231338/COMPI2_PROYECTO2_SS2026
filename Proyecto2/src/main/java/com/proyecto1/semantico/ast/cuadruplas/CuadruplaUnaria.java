package com.proyecto1.semantico.ast.cuadruplas;

/** t = op a (p. ej. "neg", "not"). */
public record CuadruplaUnaria(String operador, String a, String t) implements Cuadrupla {
    @Override
    public String toStringLegible() { return t + " = " + operador + " " + a; }

    @Override
    public <T2> T2 aceptar(VisitanteCuadrupla<T2> visitante) { return visitante.visitar(this); }
}
