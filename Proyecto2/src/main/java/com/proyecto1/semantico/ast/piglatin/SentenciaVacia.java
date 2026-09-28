package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * una instrucción vacía (un ; suelto, sin
 * ningún contenido). No tiene equivalente en Y; se incluye porque la gramática de
 * PigLatin la admite explícitamente como alternativa de sentencia. Sin datos
 * propios más que la posición.
 */
public final class SentenciaVacia extends NodoPigLatin implements InstruccionPigLatin {
    public SentenciaVacia(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        return TipoPrimitivo.VOID;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // Nada que emitir: la sentencia vacía no produce cuádruplas.
        return ResultadoC3D.vacio();
    }
}
