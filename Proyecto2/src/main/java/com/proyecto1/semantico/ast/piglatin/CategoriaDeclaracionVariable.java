package com.proyecto1.semantico.ast.piglatin;

/**
 * Categoría de una DeclaracionVariable, según cuál de las 3 alternativas de
 * la regla declaracionVariableSinPuntoYComa se usó:
 *   declaracionVarConTipo: esto ID : tipo (= expresion)? el
 *       tipo viene explícito.
 *   #declaracionVarEstructura: esto ID : ID inicializadorArreglo
 *       el "tipo" es el nombre de una estructura/clase importada y SIEMPRE trae un
 *       inicializador.
 *   #declaracionVarSoloValor: esto ID : expresion - sin tipo
 *       explícito; se infiere del valor.
 */
public enum CategoriaDeclaracionVariable {
    CON_TIPO,
    ESTRUCTURA,
    SOLO_VALOR
}
