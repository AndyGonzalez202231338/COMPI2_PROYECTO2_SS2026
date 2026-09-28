package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

public final class Romper extends NodoZ implements InstruccionZ {
    public Romper(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // "break" es válido dentro de un ciclo O dentro de un caso/siempre de un switch.
        // Se consulta dentroDeAlgoRompible() y no dentroDeAlgunCiclo(): esta última solo
        // habilita "continue", que no tiene sentido dentro de un switch suelto.
        if (!(ambito instanceof AmbitoBloque ab) || !ab.dentroDeAlgoRompible())
            errores.reportar(linea, columna,
                    "'break' solo puede usarse dentro de un ciclo o switch");
        return TipoPrimitivo.VOID;
    }


    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String destino = generador.etiquetaFinCiclo();
        if (destino == null) {
            throw new IllegalStateException("'break' fuera de un ciclo o switch (línea "
                    + linea + ", columna " + columna + ")");
        }
        generador.emitirGoto(destino);
        return ResultadoC3D.vacio();
    }
}