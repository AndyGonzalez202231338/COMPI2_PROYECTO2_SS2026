package com.proyecto1.semantico.ast.cuadruplas;

/**
 * Patrón Visitor sobre las 20 formas de Cuadrupla. (p. ej. {@code TraductorCuadrupla implements
 * VisitanteCuadrupla<String>}), con un método por tipo; nada de switch sobre
 * Strings de operador, y si se agrega un tipo de cuádrupla nuevo, este archivo (y
 * cada visitante) tiene que agregar el método correspondiente o no compila.
 */
public interface VisitanteCuadrupla<T> {
    T visitar(CuadruplaBinaria c);
    T visitar(CuadruplaUnaria c);
    T visitar(CuadruplaAsignacion c);
    T visitar(CuadruplaGoto c);
    T visitar(CuadruplaIfFalse c);
    T visitar(CuadruplaIfTrue c);
    T visitar(CuadruplaEtiqueta c);
    T visitar(CuadruplaPrint c);
    T visitar(CuadruplaRead c);
    T visitar(CuadruplaCall c);
    T visitar(CuadruplaParam c);
    T visitar(CuadruplaReturn c);
    T visitar(CuadruplaBeginFunc c);
    T visitar(CuadruplaEndFunc c);
    T visitar(CuadruplaIndiceCarga c);
    T visitar(CuadruplaIndiceGuarda c);
    T visitar(CuadruplaCampoCarga c);
    T visitar(CuadruplaCampoGuarda c);
    T visitar(CuadruplaNew c);
    T visitar(CuadruplaNewArray c);
    T visitar(CuadruplaConcat c);
    T visitar(CuadruplaCompCadena c);
    T visitar(CuadruplaConversion c);
}
