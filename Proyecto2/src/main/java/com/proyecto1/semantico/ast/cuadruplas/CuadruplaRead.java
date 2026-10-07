package com.proyecto1.semantico.ast.cuadruplas;

public record CuadruplaRead(String destino, String tipo) implements Cuadrupla {
    /** Compatibilidad: lectura sin tipo conocido = cadena. */
    public CuadruplaRead(String destino) { this(destino, "cadena"); }

    @Override
    public String toStringLegible() { return "read " + destino; }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
