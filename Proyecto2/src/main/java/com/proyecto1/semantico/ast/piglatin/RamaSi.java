package com.proyecto1.semantico.ast.piglatin;

/**
 * Una rama "si (cond) bloque" o "aliter (cond) bloque" dentro de un Si.
 */
public final class RamaSi {

    private final ExpresionPigLatin condicion;
    private final Bloque cuerpo;

    public RamaSi(ExpresionPigLatin condicion, Bloque cuerpo) {
        this.condicion = condicion;
        this.cuerpo = cuerpo;
    }

    public ExpresionPigLatin getCondicion() {
        return condicion;
    }

    public Bloque getCuerpo() {
        return cuerpo;
    }
}
