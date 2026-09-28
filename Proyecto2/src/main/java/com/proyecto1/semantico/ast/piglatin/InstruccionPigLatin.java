package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.NodoAST;

/**
 * Marca los nodos que representan una INSTRUCCIÓN (sentencia) de PigLatin: una
 * pieza de código dentro de un Bloque o del cuerpo de FuncionPrincipal
 * (declaración, asignación usada como sentencia, si/dum/facere/per, imprimir, leer,
 * interrumpe/perge, etc.). Ver ExpresionPigLatin para la razón de ser de esta
 * interfaz vacía.
 */
public interface InstruccionPigLatin extends NodoAST {
}
