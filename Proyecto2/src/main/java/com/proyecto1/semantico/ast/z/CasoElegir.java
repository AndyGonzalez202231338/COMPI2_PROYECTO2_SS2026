package com.proyecto1.semantico.ast.z;

import java.util.List;

/**
 * Un switchCase (#switchCaseDef): "case expresion: statement* break?", dentro
 * de un Elegir.
 */
public final class CasoElegir {

    private final ExpresionZ valor;
    private final List<InstruccionZ> instrucciones;
    private final boolean tieneRomper; // false == cae al siguiente caso (fall-through)

    public CasoElegir(ExpresionZ valor, List<InstruccionZ> instrucciones, boolean tieneRomper) {
        this.valor = valor;
        this.instrucciones = instrucciones;
        this.tieneRomper = tieneRomper;
    }

    public ExpresionZ getValor() {
        return valor;
    }

    public List<InstruccionZ> getInstrucciones() {
        return instrucciones;
    }

    public boolean isTieneRomper() {
        return tieneRomper;
    }
}
