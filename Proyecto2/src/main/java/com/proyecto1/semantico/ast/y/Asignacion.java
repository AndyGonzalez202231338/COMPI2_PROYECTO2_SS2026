package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * (#asigDef): "ID (.campo | [indice])* op= expresion".
 */
public final class Asignacion extends NodoY implements InstruccionY {

    private final ExpresionY objetivo;
    private final String operador; // "=", "+=", "-=", "*=", "/="
    private final ExpresionY valor;

    public Asignacion(ExpresionY objetivo, String operador, ExpresionY valor, int linea, int columna) {
        super(linea, columna);
        this.objetivo = objetivo;
        this.operador = operador;
        this.valor = valor;
    }

    public ExpresionY getObjetivo() { return objetivo; }
    public String getOperador() { return operador; }
    public ExpresionY getValor() { return valor; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tIzq = objetivo.verificar(ambito, errores);
        Tipo tDer = valor.verificar(ambito, errores);

        if (!Tipos.esAsignable(tIzq, tDer)) {
            errores.reportar(linea, columna,
                    "No se puede asignar " + tDer.nombre() + " a " + tIzq.nombre());
        }

        // Marcar inicializado solo si es variable simple (identificador).
        if (objetivo instanceof Identificador id) {
            Simbolo s = ambito.resolver(id.getNombre());
            if (s != null) s.marcarInicializado();
        }
        return tIzq;
    }

    /**
     * Emite la asignación para cualquiera de los tres tipos de lvalue que admite la
     * gramática: variable simple, campo de estructura, elemento de arreglo.
     *
     * Orden de evaluación (coherente con C):
     *   1. resolverLValue(objetivo)  -> evalúa subexpresiones del lvalue UNA sola vez
     *      (p. ej. el i() de arr[i()] o el obj de obj.f).
     *   2. Si op == "=": C3D del RHS y guardarEn(lv, rhs).
     *      Si op es compuesto: cargarDe(lv) (t = x), C3D del RHS, emitirBinaria, guardar.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        LValue lv = resolverLValue(objetivo, generador);

        if (operador.equals("=")) {
            ResultadoC3D rhs = valor.generarC3D(generador);
            guardarEn(lv, rhs.getLugar(), generador);
        } else {
            ResultadoC3D actual = cargarDe(lv, generador);
            ResultadoC3D rhs = valor.generarC3D(generador);
            String opBin = operador.substring(0, operador.length() - 1); // "+=" -> "+"
            String t = generador.nuevoTemporal();
            generador.emitirBinaria(opBin, actual.getLugar(), rhs.getLugar(), t);
            guardarEn(lv, t, generador);
        }
        return ResultadoC3D.vacio();
    }

    // ---------- ayudantes privados ----------

    /**
     * Descripción "sin resolver a dirección" de un lvalue: guarda los lugares donde
     * quedaron la base (nombre de variable o temporal), el nombre del campo (si aplica)
     * y el índice ya evaluado (si aplica). Solo uno de {campo, indice} es no-null.
     */
    private record LValue(String base, String campo, String indice, Tipo tipo) {}

    private LValue resolverLValue(ExpresionY objetivo, GeneradorC3D generador) {
        if (objetivo instanceof Identificador id) {
            Tipo t = TipoPrimitivo.DESCONOCIDO;
            Ambito amb = generador.getAmbito();
            if (amb != null) {
                Simbolo s = amb.resolver(id.getNombre());
                if (s != null && s.getTipo() != null) t = s.getTipo();
            }
            return new LValue(id.getNombre(), null, null, t);
        }
        if (objetivo instanceof AccesoCampo ac) {
            ResultadoC3D base = ac.getObjeto().generarC3D(generador);
            Tipo t = (ac.getTipoCampo() != null) ? ac.getTipoCampo() : TipoPrimitivo.DESCONOCIDO;
            return new LValue(base.getLugar(), ac.getCampo(), null, t);
        }
        if (objetivo instanceof Indice ind) {
            ResultadoC3D base = ind.getArreglo().generarC3D(generador);
            ResultadoC3D idx  = ind.getIndice().generarC3D(generador);
            Tipo t = (ind.getTipoElemento() != null) ? ind.getTipoElemento() : TipoPrimitivo.DESCONOCIDO;
            return new LValue(base.getLugar(), null, idx.getLugar(), t);
        }
        throw new UnsupportedOperationException(
                "Asignación a " + objetivo.getClass().getSimpleName() + ": pendiente en C3D");
    }

    private ResultadoC3D cargarDe(LValue lv, GeneradorC3D generador) {
        if (lv.campo() != null) {
            String t = generador.nuevoTemporal();
            generador.emitirCargaCampo(lv.base(), lv.campo(), t);
            return ResultadoC3D.temporal(t, lv.tipo());
        }
        if (lv.indice() != null) {
            String t = generador.nuevoTemporal();
            generador.emitirCargaIndice(lv.base(), lv.indice(), t);
            return ResultadoC3D.temporal(t, lv.tipo());
        }
        return ResultadoC3D.valor(lv.base(), lv.tipo());
    }

    private void guardarEn(LValue lv, String v, GeneradorC3D generador) {
        if (lv.campo() != null) {
            generador.emitirGuardarCampo(lv.base(), lv.campo(), v);
        } else if (lv.indice() != null) {
            generador.emitirGuardarIndice(lv.base(), lv.indice(), v);
        } else {
            generador.emitirAsignacion(v, lv.base());
        }
    }
}