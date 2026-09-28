package com.proyecto1.semantico.ast.cuadruplas;

/** if_true condicion goto etiqueta. */
public record CuadruplaIfTrue(String condicion, String etiqueta) implements CuadruplaSalto {
    @Override
    public String getEtiquetaDestino() { return etiqueta; }

    @Override
    public CuadruplaSalto conEtiquetaDestino(String nuevaEtiquetaDestino) {
        return new CuadruplaIfTrue(condicion, nuevaEtiquetaDestino);
    }

    @Override
    public String toStringLegible() { return "if_true " + condicion + " goto " + etiqueta; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}