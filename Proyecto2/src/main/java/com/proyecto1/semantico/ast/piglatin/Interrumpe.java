package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** INTERRUMPE ; (#sentenciaInterrumpeDef). Equivale a Romper de Y. */
public final class Interrumpe extends NodoPigLatin implements InstruccionPigLatin {
    public Interrumpe(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        if (!(ambito instanceof AmbitoBloque ab) || !ab.dentroDeAlgunCiclo())
            errores.reportar(linea, columna,
                    "'interrumpe' solo puede usarse dentro de un ciclo");
        return TipoPrimitivo.VOID;
    }


    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String destino = generador.etiquetaFinCiclo();
        if (destino == null) {
            throw new IllegalStateException("'interrumpe' fuera de un ciclo (línea "
                    + linea + ", columna " + columna + ")");
        }
        generador.emitirGoto(destino);
        return ResultadoC3D.vacio();
    }
}