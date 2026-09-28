package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * Operación unaria, prefija o postfija: !, -, ++, --. Cubre
 * #expUnariaNegacion, #expUnariaMenos, #expUnariaIncPrefijo y #expUnariaDecPrefijo
 * (prefijo = true), y la parte opcional de #expresionPostfijaDef (prefijo = false,
 * solo aplica a ++/--).
 */
public final class Unaria extends NodoPigLatin implements ExpresionPigLatin {

    private final String operador;
    private final ExpresionPigLatin operando;
    private final boolean prefijo;

    public Unaria(String operador, ExpresionPigLatin operando, boolean prefijo, int linea, int columna) {
        super(linea, columna);
        this.operador = operador;
        this.operando = operando;
        this.prefijo = prefijo;
    }

    public String getOperador() { return operador; }
    public ExpresionPigLatin getOperando() { return operando; }
    public boolean isPrefijo() { return prefijo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo t = operando.verificar(ambito, errores);
        switch (operador) {
            case "!":
                if (!Tipos.esBooleano(t))
                    errores.reportar(linea, columna, "'!' requiere bool");
                return TipoPrimitivo.BOOL;
            case "-":
                if (!t.esNumerico() && !t.esDesconocido())
                    errores.reportar(linea, columna, "'-' requiere numérico");
                return t;
            case "++": case "--":
                if (!Tipos.admiteIncrementoDecremento(t))
                    errores.reportar(linea, columna, "'" + operador + "' requiere numérico");
                return t;
        }
        return TipoPrimitivo.DESCONOCIDO;
    }

    /**
     * Emite según el operador (siempre después de generar el C3D del operando):
     *   ! y -: (op, a, null, t). Devuelve el temporal t
     *       (tipo BOOL para "!", el del operando para "-").
     *   ++x / --x (prefijo): t = x ± 1 y luego x = t.
     *       Devuelve t, que contiene el valor NUEVO.
     *   x++ / x-- (postfijo): t0 = x
     *   t1 = x ± 1 y x = t1. Devuelve t0 (el valor VIEJO, que es el
     *       que vale la expresión postfija en y = x++)
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        switch (operador) {
            case "!":
            case "-": {
                ResultadoC3D o = operando.generarC3D(generador);
                String t = generador.nuevoTemporal();
                generador.emitirUnaria(operador, o.getLugar(), t);
                Tipo tipo = operador.equals("!") ? TipoPrimitivo.BOOL : o.getTipo();
                return ResultadoC3D.temporal(t, tipo);
            }
            case "++":
            case "--": {
                if (!(operando instanceof Identificador id)) {
                    throw new UnsupportedOperationException(
                            "'" + operador + "' sobre campos o arreglos: pendiente en C3D");
                }
                ResultadoC3D o = operando.generarC3D(generador);
                String variable = o.getLugar();
                String opBinario = operador.equals("++") ? "+" : "-";

                if (prefijo) {
                    String t = generador.nuevoTemporal();
                    generador.emitirBinaria(opBinario, variable, "1", t);
                    generador.emitirAsignacion(t, variable);
                    return ResultadoC3D.temporal(t, o.getTipo());
                }
                // Postfijo: devolver el valor VIEJO.
                String viejo = generador.nuevoTemporal();
                generador.emitirAsignacion(variable, viejo);
                String nuevo = generador.nuevoTemporal();
                generador.emitirBinaria(opBinario, variable, "1", nuevo);
                generador.emitirAsignacion(nuevo, variable);
                return ResultadoC3D.temporal(viejo, o.getTipo());
            }
            default:
                throw new UnsupportedOperationException("Operador unario no soportado: " + operador);
        }
    }
}