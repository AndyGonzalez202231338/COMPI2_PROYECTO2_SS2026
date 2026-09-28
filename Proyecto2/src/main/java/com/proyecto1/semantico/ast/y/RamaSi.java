package com.proyecto1.semantico.ast.y;

/**
 * Una rama "si (cond) entonces bloque" o "sino (cond) entonces bloque" dentro de un
 * No implementa NodoAST por sí sola (no es una instrucción ni una expresión independiente, solo tiene sentido colgada de
 * un Si}; no necesita línea/columna propias porque el nodo Si que la
 * contiene ya sabe su posición.
 */
public final class RamaSi {

    private final ExpresionY condicion;
    private final Bloque cuerpo;

    public RamaSi(ExpresionY condicion, Bloque cuerpo) {
        this.condicion = condicion;
        this.cuerpo = cuerpo;
    }

    public ExpresionY getCondicion() {
        return condicion;
    }

    public Bloque getCuerpo() {
        return cuerpo;
    }
}
