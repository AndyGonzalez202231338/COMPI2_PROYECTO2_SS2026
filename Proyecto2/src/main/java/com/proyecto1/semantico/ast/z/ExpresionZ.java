package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.NodoAST;

/**
 * Marca los nodos que representan una EXPRESIÓN de Zetariano (producen un valor / un
 * tipo). Mismo rol que {@code ExpresionY}: no agrega métodos, solo permite escribir
 * List<ExpresionZ> en vez de List<NodoAST>.
 *
 */
public interface ExpresionZ extends NodoAST {
}
