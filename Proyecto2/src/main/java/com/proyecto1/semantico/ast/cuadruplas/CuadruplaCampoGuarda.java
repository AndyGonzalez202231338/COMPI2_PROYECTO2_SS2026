package com.proyecto1.semantico.ast.cuadruplas;

/** objeto.campo = valor. Mismo caso especial que CuadruplaIndiceGuarda: el tercer campo es el valor. */
public record CuadruplaCampoGuarda(String objeto, String campo, String valor) implements Cuadrupla {
    @Override
    public String toStringLegible() { return objeto + "." + campo + " = " + valor; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
