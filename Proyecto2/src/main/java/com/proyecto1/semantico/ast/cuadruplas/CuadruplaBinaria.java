package com.proyecto1.semantico.ast.cuadruplas;

/** t = a op b. "operador" es el símbolo tal cual ("+", "==", "&&", ...).
 * tipoDato indica el tipo de la operacion: "entero", "flotante", "bool", etc.
 * Si es null el backend debe inferirlo (compatibilidad con la Fase 1).
 * */
public record CuadruplaBinaria(String operador, String a, String b, String t, String tipoDato) implements Cuadrupla {
    // Compatibilidad: sin tipo (Fase 1).
    public CuadruplaBinaria(String operador, String a, String b, String t) {
        this(operador, a, b, t, null);
    }

    @Override
    public String toStringLegible() {
        String base = t + " = " + a + " " + operador + " " + b;
        return tipoDato != null ? base + "  [" + tipoDato + "]" : base;
    }

    @Override
    public <T2> T2 aceptar(VisitanteCuadrupla<T2> visitante) { return visitante.visitar(this); }
}
