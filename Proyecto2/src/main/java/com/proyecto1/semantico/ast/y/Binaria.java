package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

public final class Binaria extends NodoY implements ExpresionY {

    private final String operador;
    private final ExpresionY izquierdo;
    private final ExpresionY derecho;

    public Binaria(String operador, ExpresionY izquierdo, ExpresionY derecho, int linea, int columna) {
        super(linea, columna);
        this.operador = operador;
        this.izquierdo = izquierdo;
        this.derecho = derecho;
    }

    public String getOperador() { return operador; }
    public ExpresionY getIzquierdo() { return izquierdo; }
    public ExpresionY getDerecho() { return derecho; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo ti = izquierdo.verificar(ambito, errores);
        Tipo td = derecho.verificar(ambito, errores);

        switch (operador) {
            case "+": case "-": case "*": case "/": case "%":
                // "%" sigue la misma regla que -,*,/ : numérico con numérico, sin concatenar cadenas
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

    /**
     * Emite (post-orden): primero el C3D del izquierdo, luego el del derecho, y al final
     * UNA cuádrupla {@code (op, a, b, t)}, es decir {@code t = a op b}, con un temporal
     * nuevo pedido DESPUÉS de generar los hijos (así los temporales internos salen
     * numerados antes que el externo).
     * Devuelve: {@code ResultadoC3D.temporal(t, tipo)}. El tipo se deduce de los tipos
     * de los operandos: aritméticos con Tipos#resultadoAritmetico; BOOL para
     * comparaciones y lógicos; DESCONOCIDO si algún operando es DESCONOCIDO (p. ej. un
     * Identificador, ver su Javadoc) o la combinación es inválida (el error semántico ya
     * se reportó en verificar()).
     *
     * Nota: && y || se emiten como operación binaria plana, SIN cortocircuito. El
     * cortocircuito (saltos con backpatching) se tratará con las estructuras de control.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        ResultadoC3D a = izquierdo.generarC3D(generador);
        ResultadoC3D b = derecho.generarC3D(generador);

        //Que tipo de operacion binaria es (aritmetica o booleana)
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
        // a + b o a > b
        String t = generador.nuevoTemporal();
        //t0 = a + b
        //Crear la cuadrupla(+, a, b, t0)
        generador.emitirBinaria(operador, a.getLugar(), b.getLugar(), t);
        //c = t0 resultado del temporal para guardar en su variable
        //(t0,aritmetico o booleano)
        return ResultadoC3D.temporal(t, tipo);
    }
}