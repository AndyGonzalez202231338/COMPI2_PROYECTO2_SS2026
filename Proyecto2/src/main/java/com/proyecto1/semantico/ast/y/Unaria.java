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
                // ++/-- sobre un CAMPO (obj.f++) o un INDICE (arr[i]++):
                // se carga el valor, se opera en un temporal y se vuelve a guardar.
                if (operando instanceof AccesoCampo ac) {

                    ResultadoC3D base = ac.getObjeto().generarC3D(generador);
                    String viejoC = generador.nuevoTemporal();
                    generador.emitirCargaCampo(base.getLugar(), ac.getCampo(), viejoC);
                    String nuevoC = generador.nuevoTemporal();
                    generador.emitirBinaria(operador.equals("++") ? "+" : "-", viejoC, "1", nuevoC);
                    generador.emitirGuardarCampo(base.getLugar(), ac.getCampo(), nuevoC);

                    Tipo tc = ac.getTipoCampo() != null ? ac.getTipoCampo() : TipoPrimitivo.DESCONOCIDO;

                    return ResultadoC3D.temporal(prefijo ? nuevoC : viejoC, tc);

                }
                if (operando instanceof Indice ix) {
                    ResultadoC3D baseA = ix.getArreglo().generarC3D(generador);
                    ResultadoC3D idxA  = ix.getIndice().generarC3D(generador);
                    String viejoI = generador.nuevoTemporal();
                    generador.emitirCargaIndice(baseA.getLugar(), idxA.getLugar(), viejoI);
                    String nuevoI = generador.nuevoTemporal();
                    generador.emitirBinaria(operador.equals("++") ? "+" : "-", viejoI, "1", nuevoI);

                    generador.emitirGuardarIndice(baseA.getLugar(), idxA.getLugar(), nuevoI);
                    Tipo ti = ix.getTipoElemento() != null ? ix.getTipoElemento() : TipoPrimitivo.DESCONOCIDO;

                    return ResultadoC3D.temporal(prefijo ? nuevoI : viejoI, ti);

                }
                if (!(operando instanceof Identificador id)) {
                    throw new UnsupportedOperationException(
                            "'" + operador + "' solo aplica a variable, campo o índice");
                }

                // Variable simple (Identificador): x++ / ++x
                ResultadoC3D o = operando.generarC3D(generador);
                String lugar = o.getLugar();

                if (!prefijo) {
                    String viejo = generador.nuevoTemporal();
                    generador.emitirBinaria("+", lugar, "0", viejo);      // viejo = lugar + 0
                    String nuevo = generador.nuevoTemporal();
                    generador.emitirBinaria(operador.equals("++") ? "+" : "-", lugar, "1", nuevo);
                    generador.emitirAsignacion(nuevo, lugar);
                    return ResultadoC3D.temporal(viejo, o.getTipo());
                }

                String nuevo = generador.nuevoTemporal();
                generador.emitirBinaria(operador.equals("++") ? "+" : "-", lugar, "1", nuevo);
                generador.emitirAsignacion(nuevo, lugar);
                return ResultadoC3D.temporal(nuevo, o.getTipo());
            }
            default:
                throw new UnsupportedOperationException("Operador unario no soportado: " + operador);
        }
    }
}