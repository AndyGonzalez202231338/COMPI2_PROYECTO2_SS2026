package com.proyecto1.semantico.ast.cuadruplas;

/**
 * destino = call funcion(nArgs argumentos). "funcion" ya viene con el
 * mangling resuelto (p. ej. "Persona_calcularEdad", "Persona_init_a2"). "destino"
 * puede ser null si no se usa el valor de retorno. nArgs es SIEMPRE conocido en
 * tiempo de generación de C3D (es argumentos.size()), por eso es int y no String.
 */
public record CuadruplaCall(String funcion, int nArgs, String destino) implements Cuadrupla {
    @Override
    public String toStringLegible() {
        return "call " + funcion + ", " + nArgs + (destino != null ? " -> " + destino : "");
    }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}