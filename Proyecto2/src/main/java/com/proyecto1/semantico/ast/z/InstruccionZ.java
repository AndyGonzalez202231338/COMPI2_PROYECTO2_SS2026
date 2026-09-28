package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.NodoAST;

/**
 * Marca los nodos que representan una INSTRUCCIÓN de Zetariano statement:
 * bloque, si, elegir, para, mientras, hacer-mientras, retornar, romper, continuar,
 * declaración, expresión-sentencia, o la sentencia vacía ";"). Mismo rol que
 * InstruccionY.
 */
public interface InstruccionZ extends NodoAST {
}
