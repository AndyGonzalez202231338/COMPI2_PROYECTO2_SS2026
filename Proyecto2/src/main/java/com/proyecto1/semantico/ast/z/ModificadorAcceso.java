package com.proyecto1.semantico.ast.z;

/**
 * Modificador de acceso de una clase o de un miembro (atributo, constructor, metodo).
 *
 * DEFAULT representa la ausencia de modificador (package-private). No existe en la
 * gramatica como palabra: el ASTBuilderZ lo asigna cuando no viene public/private/protected.
 *
 * Reglas de acceso (se aplican en la semantica, no aqui):
 *   PUBLIC    -> accesible desde cualquier lugar
 *   PRIVATE   -> solo dentro de la misma clase
 *   PROTECTED -> misma clase, mismo paquete y subclases
 *   DEFAULT   -> misma clase y mismo paquete
 */
public enum ModificadorAcceso {
    PUBLIC("public"),
    PRIVATE("private"),
    PROTECTED("protected"),
    DEFAULT("default");

    private final String texto;

    ModificadorAcceso(String texto) {
        this.texto = texto;
    }

    // Texto tal como se escribe en el codigo fuente; util para mensajes de error.
    public String texto() {
        return texto;
    }

    @Override
    public String toString() {
        return texto;
    }
}
