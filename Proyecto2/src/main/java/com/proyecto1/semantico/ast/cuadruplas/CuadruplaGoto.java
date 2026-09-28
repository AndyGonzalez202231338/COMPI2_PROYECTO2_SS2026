package com.proyecto1.semantico.ast.cuadruplas;

/** goto etiqueta, incondicional. */
public record CuadruplaGoto(String etiqueta) implements CuadruplaSalto {
    @Override
    public String getEtiquetaDestino() { return etiqueta; }

    @Override
    public CuadruplaSalto conEtiquetaDestino(String nuevaEtiquetaDestino) {
        return new CuadruplaGoto(nuevaEtiquetaDestino);
    }

    @Override
    public String toStringLegible() { return "goto " + etiqueta; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}