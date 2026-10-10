package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.List;

/** Un (#blockDef): "{ statement* }". */
public final class Bloque extends NodoZ implements InstruccionZ {

    private final List<InstruccionZ> instrucciones;
    private Ambito ambitoBloque;

    public Bloque(List<InstruccionZ> instrucciones, int linea, int columna) {
        super(linea, columna);
        this.instrucciones = instrucciones;
    }

    public List<InstruccionZ> getInstrucciones() {
        return instrucciones;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        this.ambitoBloque = ambito;
        for (InstruccionZ i : instrucciones) i.verificar(ambito, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite: nada propio; recorre las instrucciones en orden del código fuente y
     * cada una emite sus cuádruplas en la tabla del generador.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        Ambito anterior = null;
        if (ambitoBloque != null) {
            anterior = generador.entrarAmbito(ambitoBloque);
        }
        for (InstruccionZ i : instrucciones) i.generarC3D(generador);
        if (anterior != null) {
            generador.salirAmbito(anterior);
        }
        return ResultadoC3D.vacio();
    }
}