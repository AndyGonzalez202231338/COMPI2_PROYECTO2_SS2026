package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * Operación unaria, prefija o postfija: !, - (negación aritmética), ++, --. Cubre
 * #expUnariaPrefijaDef (prefijo=true) y la parte opcional de #expPostfijaDef
 * (prefijo=false, solo aplica a ++/--).
 */
public final class Unaria extends NodoY implements ExpresionY {

    private final String operador;
    private final ExpresionY operando;
    private final boolean prefijo;

    public Unaria(String operador, ExpresionY operando, boolean prefijo, int linea, int columna) {
        super(linea, columna);
        this.operador = operador;
        this.operando = operando;
        this.prefijo = prefijo;
    }

    public String getOperador() {
        return operador;
    }

    public ExpresionY getOperando() {
        return operando;
    }

    public boolean isPrefijo() {
        return prefijo;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo t = operando.verificar(ambito, errores);
        switch (operador) {
            case "!":
                if (!Tipos.esBooleano(t))
                    errores.reportar(linea, columna, "'!' requiere bool, se recibió " + t.nombre());
                return TipoPrimitivo.BOOL;
            case "-":
                if (!t.esNumerico() && !t.esDesconocido())
                    errores.reportar(linea, columna, "'-' requiere numérico, se recibió " + t.nombre());
                return t;
            case "++": case "--":
                if (!Tipos.admiteIncrementoDecremento(t))
                    errores.reportar(linea, columna, "'" + operador + "' requiere numérico, se recibió " + t.nombre());
                return t;
        }
        return TipoPrimitivo.DESCONOCIDO;
    }

    /**
     * Emite según el operador (siempre después de generar el C3D del operando):
     *   ! y -: (op, a, null, t), es decir t = op a.
     *       Devuelve el temporal t (tipo BOOL para "!", el del operando para "-").
     *   ++x / --x (prefijo): t = x + 1 (o - 1) y luego
     *       x = t. Devuelve t, que contiene el valor NUEVO.
     *   x++ / x-- (postfijo): t0 = x (copia del valor
     *       viejo), t1 = x + 1 (o - 1) y x = t1. Devuelve t0,
     *       porque el valor de la expresión postfija es el anterior a incrementar
     *        y = x++ y recibe el viejo).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        switch (operador) {
            case "!":
            case "-": {
                //x = 1
                //to = -x
                ResultadoC3D o = operando.generarC3D(generador);
                String t = generador.nuevoTemporal();
                //crear cuadrupla (-, x, to)
                generador.emitirUnaria(operador, o.getLugar(), t);
                //si es logico o tipo primitivo
                Tipo tipo = operador.equals("!") ? TipoPrimitivo.BOOL : o.getTipo();
                // x = t0 retornar el temporal para guardar
                return ResultadoC3D.temporal(t, tipo);
            }
            case "++":
            case "--": {
                //++x
                //t0 = x + 1
                //x = t0
                if (!(operando instanceof Identificador)) {
                    throw new UnsupportedOperationException(
                            "'" + operador + "' sobre campos o arreglos: pendiente en C3D");
                }
                ResultadoC3D o = operando.generarC3D(generador);
                String variable = o.getLugar();
                String opBinario = operador.equals("++") ? "+" : "-";

                if (prefijo) {
                    //entero y = -x
                    String t = generador.nuevoTemporal();
                    //crear cuaadrupla (+,x,1,t0)
                    generador.emitirBinaria(opBinario, variable, "1", t);
                    // x = t0 retornar el temporal para guardar
                    generador.emitirAsignacion(t, variable);
                    return ResultadoC3D.temporal(t, o.getTipo());
                }
                //postfija x++
                String viejo = generador.nuevoTemporal();
                // t0 = x
                generador.emitirAsignacion(variable, viejo);
                String nuevo = generador.nuevoTemporal();
                // t1 = x + 1  cuadrupla(+, x, 1, t10)
                generador.emitirBinaria(opBinario, variable, "1", nuevo);
                // x = t1  (t1, x)
                generador.emitirAsignacion(nuevo, variable);
                return ResultadoC3D.temporal(viejo, o.getTipo());
            }
            default:
                throw new UnsupportedOperationException("Operador unario no soportado: " + operador);
        }
    }
}