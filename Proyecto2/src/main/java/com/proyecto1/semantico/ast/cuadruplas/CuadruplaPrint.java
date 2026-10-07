package com.proyecto1.semantico.ast.cuadruplas;

public record CuadruplaPrint(String valor, boolean nuevaLinea) implements Cuadrupla {

    /** Compatibilidad: print sin salto de linea. */
    public CuadruplaPrint(String valor) { this(valor, false); }

    @Override
    public String toStringLegible() { return (nuevaLinea ? "println " : "print ") + valor; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
