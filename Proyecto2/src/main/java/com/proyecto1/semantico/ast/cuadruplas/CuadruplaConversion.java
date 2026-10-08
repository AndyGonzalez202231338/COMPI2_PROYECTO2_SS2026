package com.proyecto1.semantico.ast.cuadruplas;

/**
 * t = conv(tipoOrigen -> tipoDestino) valor
 * Conversiones explicitas entre tipos:
 *   entero -> flotante     (i2f)
 *   entero -> cadena       (i2s)
 *   flotante -> cadena     (f2s)
 *   caracter -> cadena     (c2s)
 *   bool -> cadena         (b2s)
 *   caracter -> entero     (c2i)
 *   entero -> caracter     (i2c)
 * @param tipoOrigen
 * @param tipoDestino
 * @param valor
 * @param destino
 */
public record CuadruplaConversion(String tipoOrigen, String tipoDestino, String valor, String destino) implements Cuadrupla {
    @Override
    public String toStringLegible() {
        return destino + " = conv(" + tipoOrigen + " -> " + tipoDestino + ") " + valor;
    }
    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}