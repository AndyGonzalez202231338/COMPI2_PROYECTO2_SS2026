package com.proyecto1.semantico.ast.cuadruplas;

/** t = op a (p. ej. "neg", "not").
 *  tipoDato indica el tipo del operando/resultado. null si no se conoce.
 */
public record CuadruplaUnaria(String operador, String a, String t, String tipoDato) implements Cuadrupla {

    // Compatibilidad: sin tipo.
    public CuadruplaUnaria(String operador, String a, String t) {
        this(operador, a, t, null);
    }

    @Override
    public String toStringLegible() {
        String base = t + " = " + operador + " " + a;
        return tipoDato != null ? base + "  [" + tipoDato + "]" : base;
    }

    @Override
    public <T2> T2 aceptar(VisitanteCuadrupla<T2> visitante) { return visitante.visitar(this); }
}