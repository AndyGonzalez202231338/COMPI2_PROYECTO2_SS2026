package com.proyecto1.semantico.ast.cuadruplas;

/**
 * t = strcmp a, b   (devuelve 1 si iguales, 0 si distintas)
 * t = strneq a, b   (devuelve 1 si distintas, 0 si iguales)
 * operador es "==" o "!="
 * @param operador
 * @param a
 * @param b
 * @param t
 */
public record CuadruplaCompCadena(String operador, String a, String b, String t) implements Cuadrupla {
    @Override
    public String toStringLegible() { return t + " = strcmp(" + operador + ") " + a + ", " + b; }
    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}