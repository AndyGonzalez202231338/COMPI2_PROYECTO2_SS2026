package com.proyecto1.semantico.ast.cuadruplas;

/**
 *  tipoDato indica que tipo imprimir: "entero", "flotante", "cadena", "caracter", "bool".
 *  Si es null el backend debe inferirlo.
 */
public record CuadruplaPrint(String valor, boolean nuevaLinea, String tipoDato) implements Cuadrupla {

    // Compatibilidad: sin tipo.
    public CuadruplaPrint(String valor, boolean nuevaLinea) {
        this(valor, nuevaLinea, null);
    }

    // Compatibilidad: sin tipo y sin salto de linea.
    public CuadruplaPrint(String valor) {
        this(valor, false, null);
    }

    @Override
    public String toStringLegible() {
        String base = (nuevaLinea ? "println " : "print ") + valor;
        return tipoDato != null ? base + "  [" + tipoDato + "]" : base;
    }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}