package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

public final class Binaria extends NodoZ implements ExpresionZ {

    private final String operador;
    private final ExpresionZ izquierdo;
    private final ExpresionZ derecho;

    public Binaria(String operador, ExpresionZ izquierdo, ExpresionZ derecho, int linea, int columna) {
        super(linea, columna);
        this.operador = operador;
        this.izquierdo = izquierdo;
        this.derecho = derecho;
    }

    public String getOperador() { return operador; }
    public ExpresionZ getIzquierdo() { return izquierdo; }
    public ExpresionZ getDerecho() { return derecho; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo ti = izquierdo.verificar(ambito, errores);
        Tipo td = derecho.verificar(ambito, errores);

        switch (operador) {
            case "+": case "-": case "*": case "/": case "%":
                Tipo r = Tipos.resultadoAritmetico(ti, td, operador.equals("+"));
                if (r == null) {
                    errores.reportar(linea, columna,
                            "Operación '" + operador + "' no válida entre " + ti.nombre() + " y " + td.nombre());
                    return TipoPrimitivo.DESCONOCIDO;
                }
                return r;
            case "==": case "!=":
                if (!Tipos.esComparableIgualdad(ti, td))
                    errores.reportar(linea, columna,
                            "Comparación '" + operador + "' no válida entre " + ti.nombre() + " y " + td.nombre());
                return TipoPrimitivo.BOOL;
            case "<": case ">": case "<=": case ">=":
                if (!Tipos.esComparableOrden(ti, td))
                    errores.reportar(linea, columna,
                            "Comparación de orden no válida entre " + ti.nombre() + " y " + td.nombre());
                return TipoPrimitivo.BOOL;
            case "&&": case "||":
                if (!Tipos.esBooleano(ti) || !Tipos.esBooleano(td))
                    errores.reportar(linea, columna,
                            "Operador lógico requiere bool, se recibió " + ti.nombre() + " y " + td.nombre());
                return TipoPrimitivo.BOOL;
        }
        return TipoPrimitivo.DESCONOCIDO;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        ResultadoC3D a = izquierdo.generarC3D(generador);
        ResultadoC3D b = derecho.generarC3D(generador);

        Tipo tipo;
        switch (operador) {
            case "+": case "-": case "*": case "/": case "%":
                Tipo r = Tipos.resultadoAritmetico(a.getTipo(), b.getTipo(), operador.equals("+"));
                tipo = (r == null) ? TipoPrimitivo.DESCONOCIDO : r;
                break;
            case "==": case "!=": case "<": case ">": case "<=": case ">=":
            case "&&": case "||":
                tipo = TipoPrimitivo.BOOL;
                break;
            default:
                tipo = TipoPrimitivo.DESCONOCIDO;
        }
        String t = generador.nuevoTemporal();
        generador.emitirBinaria(operador, a.getLugar(), b.getLugar(), t);
        return ResultadoC3D.temporal(t, tipo);
    }
}