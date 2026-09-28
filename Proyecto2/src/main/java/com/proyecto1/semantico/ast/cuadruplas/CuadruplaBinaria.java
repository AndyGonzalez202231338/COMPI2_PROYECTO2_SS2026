package com.proyecto1.semantico.ast.cuadruplas;

/** t = a op b. "operador" es el símbolo tal cual ("+", "==", "&&", ...). */
public record CuadruplaBinaria(String operador, String a, String b, String t) implements Cuadrupla {
    @Override
    public String toStringLegible() { return t + " = " + a + " " + operador + " " + b; }

    @Override
    public <T2> T2 aceptar(VisitanteCuadrupla<T2> visitante) { return visitante.visitar(this); }
}
