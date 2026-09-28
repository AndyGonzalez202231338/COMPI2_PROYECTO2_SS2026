package com.proyecto1.semantico.ast.cuadruplas;

/**
 * begin_func nombre, nArgs. nArgs es SIEMPRE conocido al generar C3D
 * (parametros.size(), +1 si hay "this" implícito en Z), por eso es int.
 */
public record CuadruplaBeginFunc(String nombre, int nArgs) implements Cuadrupla {
    @Override
    public String toStringLegible() { return "begin_func " + nombre + ", " + nArgs; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
