package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** (#continueStatementDef). Sin datos propios más que la posición. */
public final class Continuar extends NodoZ implements InstruccionZ {
    public Continuar(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        if (!(ambito instanceof AmbitoBloque ab) || !ab.dentroDeAlgunCiclo())
            errores.reportar(linea, columna,
                    "'continue' solo puede usarse dentro de un ciclo");
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite (goto, null, null, L) donde L es generador.etiquetaInicioCiclo()
     * (tope de la pila de ciclos: destino de "continue" del ciclo más interno).
     * Si la pila está vacía lanza error: verificar() ya reporta
     * "continue fuera de ciclo", así que esto solo ocurre si se generó C3D sin análisis
     * semántico.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String destino = generador.etiquetaInicioCiclo();
        if (destino == null) {
            throw new IllegalStateException("'continue' fuera de un ciclo (línea "
                    + linea + ", columna " + columna + ")");
        }
        generador.emitirGoto(destino);
        return ResultadoC3D.vacio();
    }
}