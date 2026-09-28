package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.NodoAST;

/**
 * Marca los nodos que representan una EXPRESIÓN de Y (producen un valor / un tipo).
 * No agrega métodos propios: solo permite escribir List<ExpresionY> en vez de
 * List<NodoAST> en las clases del AST, para que quede claro a simple vista qué
 * se espera guardar ahí.
 */
public interface ExpresionY extends NodoAST {
}
