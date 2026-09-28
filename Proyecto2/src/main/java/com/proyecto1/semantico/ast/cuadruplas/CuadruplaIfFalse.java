package com.proyecto1.semantico.ast.cuadruplas;

/** if_false condicion goto etiqueta. */
public record CuadruplaIfFalse(String condicion, String etiqueta) implements CuadruplaSalto {
    @Override
    public String getEtiquetaDestino() { return etiqueta; }

    @Override
    public CuadruplaSalto conEtiquetaDestino(String nuevaEtiquetaDestino) {
        return new CuadruplaIfFalse(condicion, nuevaEtiquetaDestino);
    }

    @Override
    public String toStringLegible() { return "if_false " + condicion + " goto " + etiqueta; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}