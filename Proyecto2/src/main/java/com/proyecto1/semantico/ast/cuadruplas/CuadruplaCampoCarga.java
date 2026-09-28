package com.proyecto1.semantico.ast.cuadruplas;

/** destino = objeto.campo. El campo va por NOMBRE, no por offset numérico. */
public record CuadruplaCampoCarga(String objeto, String campo, String destino) implements Cuadrupla {
    @Override
    public String toStringLegible() { return destino + " = " + objeto + "." + campo; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
