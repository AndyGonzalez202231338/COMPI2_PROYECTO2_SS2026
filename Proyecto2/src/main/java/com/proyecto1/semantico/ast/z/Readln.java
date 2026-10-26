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
 * Es una expresion (no una instruccion): devuelve el valor leido, que el
 * padre decide que hacer con el (x = readln(), readln() + "x", etc.).
 *
 * El tipo de retorno se infiere del contexto: si readln() aparece como
 * inicializador de "int x = readln()", el tipo es entero; si aparece en
 * "String s = readln()", es cadena. Sin contexto, se asume cadena.
 */
public final class Readln extends NodoZ implements ExpresionZ {

    /**
     * Tipo esperado por el contexto (ej. "int x = readln()" -> ENTERO).
     * Lo setea el nodo padre (DeclaracionVariable, Asignacion) antes de
     * llamar a verificar(). Si nadie lo setea, queda null y se asume CADENA.
     */
    private Tipo tipoEsperado;

    public Readln(int linea, int columna) {
        super(linea, columna);
    }

    public void setTipoEsperado(Tipo tipo) {
        this.tipoEsperado = tipo;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        if (tipoEsperado != null) return tipoEsperado;
        return TipoPrimitivo.CADENA;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String t = generador.nuevoTemporal();
        String tipo = (tipoEsperado != null) ? tipoEsperado.nombre() : "cadena";
        generador.emitirRead(t, tipo);
        return ResultadoC3D.temporal(t, tipoEsperado != null ? tipoEsperado : TipoPrimitivo.CADENA);
    }
}