package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * assignmentExpression (#assignmentExpressionDef) cuando trae operador:
 * "objetivo op= valor". implementa ExpresionZ, NO InstruccionZ —
 * a diferencia de Y?, en la gramática de Z la asignación vive DENTRO de la jerarquía
 * de expresiones (así "a = (b = 5)" es válido). Una asignación usada como sentencia
 * suelta ("x = 5;") queda envuelta en ExpresionStmt, igual que cualquier otra
 * expresión.
 *
 * El objetivo se guarda ya armado como ExpresionZ (una cadena de
 * Identificador envuelto en AccesoCampo/Indice), exactamente
 * como sale de visitar "primaryExpression" normalmente — no hace falta lógica
 * distinta de encadenado para el lado izquierdo.
 */
public final class Asignacion extends NodoZ implements ExpresionZ {

    private final ExpresionZ objetivo;
    private final String operador; // "=", "+=", "-=", "*=", "/=", "%="
    private final ExpresionZ valor;

    public Asignacion(ExpresionZ objetivo, String operador, ExpresionZ valor, int linea, int columna) {
        super(linea, columna);
        this.objetivo = objetivo;
        this.operador = operador;
        this.valor = valor;
    }

    public ExpresionZ getObjetivo() { return objetivo; }
    public String getOperador()     { return operador; }
    public ExpresionZ getValor()    { return valor; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // Con "=" el objetivo se ESCRIBE, no se lee: marcarlo inicializado ANTES de
        // verificarlo evita el falso "usada sin inicializar" en "boolean x; x = false;".

        if (operador.equals("=") && objetivo instanceof Identificador idObj) {
            Simbolo sObj = ambito.resolver(idObj.getNombre());
            if (sObj != null) sObj.marcarInicializado();
        }

        Tipo tIzq = objetivo.verificar(ambito, errores);
        Tipo tDer = valor.verificar(ambito, errores);

        if (!operador.equals("=")) {
            Tipo r = Tipos.resultadoAritmetico(tIzq, tDer, operador.equals("+="));
            if (r == null)
                errores.reportar(linea, columna, "Operador '" + operador + "' no válido");
        } else if (!Tipos.esAsignable(tIzq, tDer)) {
            errores.reportar(linea, columna,
                    "No se puede asignar " + tDer.nombre() + " a " + tIzq.nombre());
        }
        return tIzq;
    }

    /**
     * Emite:
     *
     *   x = v: el C3D del RHS y la escritura en el lugar del lvalue
     *       (que según el caso puede ser (=, v, -, x), (.=, this, x, v),
     *       Devuelve un ResultadoC3D con el lugar del RHS y el tipo del lvalue,
     *       para que a = (b = 5) encadene.
     *   x op= v: primero se lee el valor actual del lvalue
     *       #cargarDe}, se emite la binaria t = actual op v, y se
     *       guarda t en el lvalue. Devuelve temporal(t, tipoLvalue).
     *
     * Orden de evaluación: primero se resuelve el lvalue (evalúa
     * subexpresiones del lvalue: obj en obj.f = v, arr e
     * {idx en arr[i] = v), luego el RHS. Así arr[i()] = f()
     * evalúa i() antes que f().
     *
     * 4 casos de lvalue en Z:
     *   Identificador con categoría VARIABLE/PARAMETRO:
     *       variable local.
     *   Identificador} con categoría ATRIBUTO: acceso implícito
     *       a this.<nombre>. Es el caso que Z añade sobre Y.<
     *   AccesoCampo}: obj.campo.
     *   Indice: arr[i].
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        LValue lv = resolverLValue(objetivo, generador);

        if (operador.equals("=")) {
            ResultadoC3D rhs = valor.generarC3D(generador);
            guardarEn(lv, rhs.getLugar(), generador);
            // El valor de la expresión "x = v" es v, con el tipo del lvalue.
            return ResultadoC3D.valor(rhs.getLugar(), lv.tipo());
        }

        // Operador compuesto: leer, operar, guardar.
        ResultadoC3D actual = cargarDe(lv, generador);
        ResultadoC3D rhs    = valor.generarC3D(generador);
        String opBin = operador.substring(0, operador.length() - 1); // "+=" -> "+"
        String t = generador.nuevoTemporal();
        generador.emitirBinaria(opBin, actual.getLugar(), rhs.getLugar(), t);
        guardarEn(lv, t, generador);
        return ResultadoC3D.temporal(t, lv.tipo());
    }

    // ---------- ayudantes privados ----------

    /**
     * Descripción "sin resolver a dirección" de un lvalue: guarda los lugares donde
     * quedaron la base (nombre de variable, temporal, o el literal "this"),
     * el nombre del campo (si aplica) y el índice ya evaluado (si aplica). Solo uno
     * de {campo, indice} es no-null.
     */
    private record LValue(String base, String campo, String indice, Tipo tipo) {}

    private LValue resolverLValue(ExpresionZ objetivo, GeneradorC3D generador) {
        // Caso 1 + 2: identificador simple (variable local, parámetro, o atributo).
        if (objetivo instanceof Identificador id) {
            String nombre = id.getNombre();
            Tipo tipo = TipoPrimitivo.DESCONOCIDO;
            CategoriaSimbolo cat = null;

            Ambito amb = generador.getAmbito();
            if (amb != null) {
                Simbolo s = amb.resolver(nombre);
                if (s != null) {
                    tipo = s.getTipo();
                    cat  = s.getCategoria();
                }
            }
            if (cat == CategoriaSimbolo.ATRIBUTO) {
                // Acceso implícito a "this.<nombre>".
                return new LValue("this", nombre, null, tipo);
            }
            // Variable local o parámetro.
            return new LValue(nombre, null, null, tipo);
        }

        // Caso 3: obj.campo
        if (objetivo instanceof AccesoCampo ac) {
            ResultadoC3D base = ac.getObjeto().generarC3D(generador);
            Tipo tipo = (ac.getTipoCampo() != null) ? ac.getTipoCampo() : TipoPrimitivo.DESCONOCIDO;
            return new LValue(base.getLugar(), ac.getCampo(), null, tipo);
        }

        // Caso 4: arr[i]
        if (objetivo instanceof Indice ind) {
            ResultadoC3D base = ind.getArreglo().generarC3D(generador);
            ResultadoC3D idx  = ind.getIndice().generarC3D(generador);
            Tipo tipo = (ind.getTipoElemento() != null) ? ind.getTipoElemento() : TipoPrimitivo.DESCONOCIDO;
            return new LValue(base.getLugar(), null, idx.getLugar(), tipo);
        }

        throw new UnsupportedOperationException(
                "Asignación a " + objetivo.getClass().getSimpleName() + ": no soportado en C3D");
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