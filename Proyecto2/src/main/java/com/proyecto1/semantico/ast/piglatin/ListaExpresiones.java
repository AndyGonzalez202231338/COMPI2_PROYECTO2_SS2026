package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.List;

/**
 * (#listaExpresionesDef): expresion (, expresion)*.
 * Usada en dos lugares de per (...): como inicializacionFor
 * Se modela como InstruccionPigLatin (no como expresión) porque en ambos casos
 * cuelga directamente de un Per, nunca de otra expresión.
 */
public final class ListaExpresiones extends NodoPigLatin implements InstruccionPigLatin {

    private final List<ExpresionPigLatin> expresiones;

    public ListaExpresiones(List<ExpresionPigLatin> expresiones, int linea, int columna) {
        super(linea, columna);
        this.expresiones = expresiones;
    }

    public List<ExpresionPigLatin> getExpresiones() {
        return expresiones;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        for (ExpresionPigLatin e : expresiones) e.verificar(ambito, errores);
        return TipoPrimitivo.VOID;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        for (ExpresionPigLatin e : expresiones) {
            e.generarC3D(generador);   // efectos sí, valores no
        }
        return ResultadoC3D.vacio();
    }
}