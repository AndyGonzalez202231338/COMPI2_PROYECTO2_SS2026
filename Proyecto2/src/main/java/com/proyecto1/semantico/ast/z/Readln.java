package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * readlnExpression (#primarioReadln): "readln()".
 *
 * Es una expresión (no una instrucción): devuelve el valor leído, que el padre
 * decide qué hacer con él x = readln();, readln() + "x", etc.). Como en Z la lectura se trata como llamada al
 * runtime, el C3D es (call, rt_readln, 0, t) con un temporal nuevo como
 * destino.
 */
public final class Readln extends NodoZ implements ExpresionZ {

    public Readln(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // readln() no tiene argumentos; sin más validación.
        return TipoPrimitivo.CADENA;
    }

    /**
     * Emite: una única cuádrupla (call, rt_readln, 0, t) con un temporal nuevo,
     * y devuelve temporal(t, CADENA). El padre decide qué hacer con el valor.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String t = generador.nuevoTemporal();
        generador.emitirRead(t, "cadena");
        return ResultadoC3D.temporal(t, TipoPrimitivo.CADENA);
    }
}