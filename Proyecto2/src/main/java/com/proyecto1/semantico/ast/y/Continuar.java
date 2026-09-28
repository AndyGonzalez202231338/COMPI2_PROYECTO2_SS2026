package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** (#instContinuar). Sin datos propios más que la posición. */
public final class Continuar extends NodoY implements InstruccionY {
    public Continuar(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        if (!(ambito instanceof AmbitoBloque ab) || !ab.dentroDeAlgunCiclo())
            errores.reportar(linea, columna, "'continuar' solo puede usarse dentro de un ciclo");
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite: (goto, null, null, L), donde L es generador.etiquetaInicioCiclo()
     * (tope de la pila de ciclos: el destino de "continuar" del ciclo más interno).
     * No genera código propio adicional; solo consulta la pila.
     * Si la pila está vacía lanza IllegalStateException: verificar() ya reporta
     * "continuar fuera de ciclo", así que esto solo ocurre si se generó C3D sin haber
     * pasado el análisis semántico.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String destino = generador.etiquetaInicioCiclo();
        if (destino == null) {
            throw new IllegalStateException("'continuar' fuera de un ciclo (línea "
                    + linea + ", columna " + columna + ")");
        }
        generador.emitirGoto(destino);
        return ResultadoC3D.vacio();
    }
}