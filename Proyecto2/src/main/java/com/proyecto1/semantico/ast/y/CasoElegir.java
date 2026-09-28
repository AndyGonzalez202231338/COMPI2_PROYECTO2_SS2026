package com.proyecto1.semantico.ast.y;

/**
 * Un casoElegir (#casoDef): "caso literal: bloque", dentro de un Elegir.
 */
public final class CasoElegir {

    private final Literal valor;
    private final Bloque cuerpo;

    public CasoElegir(Literal valor, Bloque cuerpo) {
        this.valor = valor;
        this.cuerpo = cuerpo;
    }

    public Literal getValor() {
        return valor;
    }

    public Bloque getCuerpo() {
        return cuerpo;
    }
}
