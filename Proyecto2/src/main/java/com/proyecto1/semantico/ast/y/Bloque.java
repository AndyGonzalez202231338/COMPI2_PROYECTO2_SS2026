package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.List;

/**
 * Un bloque o bloqueSimple  (#bloqueDef / #bloqueSimpleDef). Se
 * unifican en una sola clase: la diferencia entre ambos en la gramática es puramente
 * de puntuación (si llevan ':' antes o no), no de contenido. las dos son, ya dentro
 * del AST, "una lista de instrucciones".
 */
public final class Bloque extends NodoY {

    private final List<InstruccionY> instrucciones;

    public Bloque(List<InstruccionY> instrucciones, int linea, int columna) {
        super(linea, columna);
        this.instrucciones = instrucciones;
    }

    public List<InstruccionY> getInstrucciones() {
        return instrucciones;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        for (InstruccionY i : instrucciones) i.verificar(ambito, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite: nada propio; recorre las instrucciones en el orden del código fuente y
     * cada una emite sus cuádrupulas en la tabla del generador.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        for (InstruccionY i : instrucciones) i.generarC3D(generador);
        return ResultadoC3D.vacio();
    }
}