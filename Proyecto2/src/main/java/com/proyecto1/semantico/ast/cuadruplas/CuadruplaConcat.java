package com.proyecto1.semantico.ast.cuadruplas;

/**
 * t = concat a, b
 * Concatenacion de cadenas. Ambos operandos deben ser cadena; la conversion de
 * entero/flotante/char a cadena la emite quien llama (con CuadruplaConversion).
 * @param a
 * @param b
 * @param t
 */
public record CuadruplaConcat(String a, String b, String t) implements Cuadrupla {
    @Override
    public String toStringLegible() { return t + " = concat " + a + ", " + b; }
    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}