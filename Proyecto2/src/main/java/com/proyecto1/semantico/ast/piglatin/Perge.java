package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

public final class Perge extends NodoPigLatin implements InstruccionPigLatin {
    public Perge(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        if (!(ambito instanceof AmbitoBloque ab) || !ab.dentroDeAlgunCiclo())
            errores.reportar(linea, columna,
                    "'perge' solo puede usarse dentro de un ciclo");
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite (goto, null, null, L), donde L es generador.etiquetaInicioCiclo()
     * (el destino de "perge" del ciclo más interno).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String destino = generador.etiquetaInicioCiclo();
        if (destino == null) {
            throw new IllegalStateException("'perge' fuera de un ciclo (línea "
                    + linea + ", columna " + columna + ")");
        }
        generador.emitirGoto(destino);
        return ResultadoC3D.vacio();
    }
}