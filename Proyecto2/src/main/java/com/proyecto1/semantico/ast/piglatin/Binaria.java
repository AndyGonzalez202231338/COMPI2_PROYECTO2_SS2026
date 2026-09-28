package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * Cualquier operación binaria: ||, &&, ==, !=, <, >, <=, >=, +, -, *, /, %.
 * Los seis niveles de precedencia de la gramática se colapsan aquí porque la
 * precedencia ya quedó resuelta por la FORMA del árbol.
 */
public final class Binaria extends NodoPigLatin implements ExpresionPigLatin {

    private final String operador;
    private final ExpresionPigLatin izquierdo;
    private final ExpresionPigLatin derecho;

    public Binaria(String operador, ExpresionPigLatin izquierdo, ExpresionPigLatin derecho,
                   int linea, int columna) {
        super(linea, columna);
        this.operador = operador;
        this.izquierdo = izquierdo;
        this.derecho = derecho;
    }

    public String getOperador() { return operador; }
    public ExpresionPigLatin getIzquierdo() { return izquierdo; }
    public ExpresionPigLatin getDerecho() { return derecho; }

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
                    errores.reportar(linea, columna, "Comparación '" + operador + "' no válida");
                return TipoPrimitivo.BOOL;
            case "<": case ">": case "<=": case ">=":
                if (!Tipos.esComparableOrden(ti, td))
                    errores.reportar(linea, columna, "Comparación de orden no válida");
                return TipoPrimitivo.BOOL;
            case "&&": case "||":
                if (!Tipos.esBooleano(ti) || !Tipos.esBooleano(td))
                    errores.reportar(linea, columna, "Operador lógico requiere bool");
                return TipoPrimitivo.BOOL;
        }
        return TipoPrimitivo.DESCONOCIDO;
    }

    /**
     * Emite (post-orden): primero el C3D del izquierdo, luego el del derecho, y al final
     * UNA cuádrupla (op, a, b, t), es decir t = a op b, con un temporal
     * nuevo pedido DESPUÉS de generar los hijos (así los temporales internos salen
     * numerados antes que el externo). Devuelve {@code ResultadoC3D.temporal(t, tipo)}.
     * El tipo se deduce de los tipos de los operandos (aritmético, BOOL, o DESCONOCIDO
     * si la combinación es inválida - el error ya se reportó en verificar()).
     */
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