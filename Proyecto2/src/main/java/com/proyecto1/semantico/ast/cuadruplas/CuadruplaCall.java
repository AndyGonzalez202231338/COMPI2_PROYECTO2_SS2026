package com.proyecto1.semantico.ast.cuadruplas;

// destino = call funcion(nArgs argumentos). "funcion" ya viene con el mangling resuelto.
// "destino" puede ser null si no se usa el valor de retorno.
// tipoRetorno es opcional: "flotante", "entero", "cadena", etc. Si es null el backend
// trata el retorno como entero.
public record CuadruplaCall(String funcion, int nArgs, String destino, String tipoRetorno) implements Cuadrupla {

    // Compatibilidad: sin tipo de retorno.
    public CuadruplaCall(String funcion, int nArgs, String destino) {
        this(funcion, nArgs, destino, null);
    }

    @Override
    public String toStringLegible() {
        String base = "call " + funcion + ", " + nArgs + (destino != null ? " -> " + destino : "");
        return tipoRetorno != null ? base + "  [" + tipoRetorno + "]" : base;
    }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}