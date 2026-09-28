package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.NodoAST;

/**
 * Marca los nodos que representan una EXPRESIÓN de PigLatin (producen un valor / un
 * tipo). No agrega métodos propios: solo permite escribir {@code List<ExpresionPigLatin>}
 * en vez de {@code List<NodoAST>} en las clases del AST, para que quede claro a simple
 * vista qué se espera guardar ahí.
 */
public interface ExpresionPigLatin extends NodoAST {
}
