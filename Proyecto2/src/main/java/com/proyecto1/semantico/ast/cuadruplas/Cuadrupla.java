package com.proyecto1.semantico.ast.cuadruplas;

/**
 * Cada tipo de instrucción, cada una con SUS PROPIOS campos con
 * nombre y tipo reales (p. ej. CuadruplaCall#nArgs() es un int, ya
 * no un String que hay que parsear). Así:
 *   - Cada clase es responsable solo de su propia forma y de imprimirse a sí misma no hay un switch gigante en un solo lugar.
 *   - Agregar una instrucción nueva es agregar una clase nueva + un caso en VisitanteCuadrupla, sin tocar las que ya existen.
 *   - El compilador avisa (permits) si falta cubrir un tipo en un sealed hierarchy.
 *   - implementa un VisitanteCuadrupla, con un
 *     método por tipo de instrucción, en vez de un switch sobre Strings.
 */
public sealed interface Cuadrupla
        permits CuadruplaSalto,
                CuadruplaBinaria, CuadruplaUnaria, CuadruplaAsignacion, CuadruplaEtiqueta,
                CuadruplaPrint, CuadruplaRead,
                CuadruplaCall, CuadruplaParam, CuadruplaReturn,
                CuadruplaBeginFunc, CuadruplaEndFunc,
                CuadruplaIndiceCarga, CuadruplaIndiceGuarda,
                CuadruplaCampoCarga, CuadruplaCampoGuarda,
                CuadruplaNew, CuadruplaNewArray {

    /** Formato legible para humanos: "t0 = a + b", "goto L1", "if_false t0 goto L2", etc. */
    String toStringLegible();

    /**
     * Punto de entrada del patrón Visitor: cada implementación hace
     * return visitante.visitar(this); así el dispatch a la sobrecarga
     * correcta lo resuelve el compilador (por el tipo estático de "this" dentro de
     * cada clase), no un switch sobre un String de operador.
     */
    <T> T aceptar(VisitanteCuadrupla<T> visitante);
}
