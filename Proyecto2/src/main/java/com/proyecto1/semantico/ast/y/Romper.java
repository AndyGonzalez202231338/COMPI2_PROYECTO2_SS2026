package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** {@code ROMPER} (#instRomper). Sin datos propios más que la posición. */
public final class Romper extends NodoY implements InstruccionY {
    public Romper(int linea, int columna) {
        super(linea, columna);
    }

    /**
     * "romper" es válido dentro de un ciclo (para/mientras/hacer-mientras) O dentro de
     * un caso/siempre de un elegir (como el "break" de un switch de C). Por eso se
     * consulta dentroDeAlgoRompible() y no dentroDeAlgunCiclo(): esta última solo
     * habilita "continuar", que no tiene sentido dentro de un elegir suelto.
     */
    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        if (!(ambito instanceof AmbitoBloque ab) || !ab.dentroDeAlgoRompible())
            errores.reportar(linea, columna, "'romper' solo puede usarse dentro de un ciclo o un elegir");
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite: (goto, null, null, L), donde L es generador.etiquetaFinCiclo():
     * el destino de "romper" más cercano, ya sea el L_fin de un ciclo (empujado con
     * entrarCiclo) o el L_fin de un elegir (empujado con entrarBloqueRompible). No
     * genera código propio adicional; solo consulta la pila.
     * Si la pila está vacía lanza error: verificar() ya reporta
     * "romper fuera de ciclo o elegir", así que esto solo ocurre si se generó C3D sin
     * haber pasado el análisis semántico.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String destino = generador.etiquetaFinCiclo();
        if (destino == null) {
            throw new IllegalStateException("'romper' fuera de un ciclo o un elegir (línea "
                    + linea + ", columna " + columna + ")");
        }
        generador.emitirGoto(destino);
        return ResultadoC3D.vacio();
    }
}