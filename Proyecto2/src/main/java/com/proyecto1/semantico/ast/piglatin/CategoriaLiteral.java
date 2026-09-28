package com.proyecto1.semantico.ast.piglatin;

/**
 * Qué tipo de literal es un {Literal}, para no tener que volver a mirar el
 * texto original. Incluye NULO (para NULL), que no existe en la
 * gramática de Y.
 */
public enum CategoriaLiteral {
    ENTERO,
    FLOTANTE,
    CARACTER,
    CADENA,
    BOOLEANO,
    NULO
}
