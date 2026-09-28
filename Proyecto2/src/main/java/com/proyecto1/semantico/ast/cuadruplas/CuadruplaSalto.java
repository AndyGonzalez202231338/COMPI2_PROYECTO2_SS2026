package com.proyecto1.semantico.ast.cuadruplas;

/**
 * Subconjunto de Cuadrupla que representa un salto cuyo destino puede no
 * conocerse todavía en el momento de emitirlo (backpatching): goto,
 * if_false, if_true. Es la única familia de instrucciones sobre la
 * que tiene sentido "reemplazar la etiqueta destino después de emitida" por eso
 * conEtiquetaDestino(String) vive aquí y no en Cuadrupla: para
 * cualquier otra instrucción (un print, un call) "cambiarle el
 * destino" no tiene un significado sensato, y con esta interfaz separada el
 * compilador ya no deja intentarlo por error.
 */
public sealed interface CuadruplaSalto extends Cuadrupla
        permits CuadruplaGoto, CuadruplaIfFalse, CuadruplaIfTrue {

    /** La etiqueta destino actual del salto (para leerla sin castear a la subclase concreta). */
    String getEtiquetaDestino();

    /** Devuelve una copia de esta misma instrucción con otra etiqueta destino. */
    CuadruplaSalto conEtiquetaDestino(String nuevaEtiquetaDestino);
}