package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoFuncion;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/** (#returnStatementDef): "return expresion? ;". */
public final class Retorno extends NodoZ implements InstruccionZ {

    private final ExpresionZ valor; // null == "return;" sin valor

    public Retorno(ExpresionZ valor, int linea, int columna) {
        super(linea, columna);
        this.valor = valor;
    }

    public ExpresionZ getValor() { return valor; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoFuncion af = ambito.ambitoFuncionMasCercano();
        if (af == null) {
            errores.reportar(linea, columna, "'return' fuera de un método o constructor");
            return TipoPrimitivo.VOID;
        }
        af.marcarTuvoRetorno();

        if (valor == null) {
            if (!af.esVoid())
                errores.reportar(linea, columna,
                        "Se esperaba un valor de retorno de tipo " + af.getTipoRetorno().nombre());
            return TipoPrimitivo.VOID;
        }
        Tipo tv = valor.verificar(ambito, errores);
        if (af.esVoid())
            errores.reportar(linea, columna, "El método no debe retornar valor");
        else if (!Tipos.esAsignable(af.getTipoRetorno(), tv))
            errores.reportar(linea, columna,
                    "Tipo de retorno incompatible: se esperaba " + af.getTipoRetorno().nombre()
                            + ", se recibió " + tv.nombre());
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite: primero el C3D del valor (si hay) y luego (return, v, null, null).
     * Sin valor emite return con arg1 en null.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        if (valor == null) {
            generador.emitirReturn(null);
        } else {
            ResultadoC3D v = valor.generarC3D(generador);
            generador.emitirReturn(v.getLugar());
        }
        return ResultadoC3D.vacio();
    }
}