package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoEstructura;
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
        // 1. Resolver tipo del LHS sin disparar error de "no inicializado"
        Tipo tIzq = resolverTipoLadoIzquierdo(objetivo, ambito, errores);

        // 2. Verificar el RHS (el valor que se va a asignar)
        if (valor instanceof Leer lectura) lectura.setTipoEsperado(tIzq);
        Tipo tDer = valor.verificar(ambito, errores);

        // 3. Validar compatibilidad de tipos
        if (!operador.equals("=")) {
            Tipo r = Tipos.resultadoAritmetico(tIzq, tDer, operador.equals("+="));
            if (r == null) {
                errores.reportar(linea, columna,
                        "Operador '" + operador + "' no válido entre " + tIzq.nombre() + " y " + tDer.nombre());
            }
        } else if (!Tipos.esAsignable(tIzq, tDer)) {
            errores.reportar(linea, columna,
                    "No se puede asignar " + tDer.nombre() + " a " + tIzq.nombre());
        }

        // 4. Marcar la variable raíz (ej. 'q' en q.x) como inicializada
        marcarRaizInicializada(objetivo, ambito);

        return tIzq;
    }


    private Tipo resolverTipoLadoIzquierdo(ExpresionY exp, Ambito ambito, ManejadorErrores errores) {
        if (exp instanceof Identificador id) {
            Simbolo s = ambito.resolver(id.getNombre());
            if (s == null) {
                errores.reportar(id.getLinea(), id.getColumna(), "Variable no declarada: '" + id.getNombre() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            return s.getTipo(); // Retorna el tipo SIN quejarse de que no está inicializada
        }
        if (exp instanceof AccesoCampo ac) {
            Tipo tBase = resolverTipoLadoIzquierdo(ac.getObjeto(), ambito, errores);
            if (tBase instanceof TipoEstructura te) {
                Simbolo campo = te.getDefinicion().buscarMiembro(ac.getCampo());
                if (campo == null) {
                    errores.reportar(ac.getLinea(), ac.getColumna(),
                            "La estructura '" + te.nombre() + "' no tiene el campo '" + ac.getCampo() + "'");
                    return TipoPrimitivo.DESCONOCIDO;
                }
                return campo.getTipo();
            }
            return TipoPrimitivo.DESCONOCIDO;
        }
        if (exp instanceof Indice ind) {
            // Para un índice (arr[i] = x), el arreglo SÍ debe estar inicializado para acceder a él.
            Tipo tArr = ind.getArreglo().verificar(ambito, errores);
            if (tArr instanceof TipoArreglo ta) return ta.getBase();
            return TipoPrimitivo.DESCONOCIDO;
        }
        // Fallback por si es otra expresión
        return exp.verificar(ambito, errores);
    }

    private void marcarRaizInicializada(ExpresionY exp, Ambito ambito) {
        if (exp instanceof Identificador id) {
            Simbolo s = ambito.resolver(id.getNombre());
            if (s != null) s.marcarInicializado();
        } else if (exp instanceof AccesoCampo ac) {
            marcarRaizInicializada(ac.getObjeto(), ambito); // Sube recursivamente hasta 'q'
        } else if (exp instanceof Indice ind) {
            marcarRaizInicializada(ind.getArreglo(), ambito);
        }
    }

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

    private record LValue(String base, String campo, String indice, Tipo tipo) {}

    private LValue resolverLValue(ExpresionY objetivo, GeneradorC3D generador) {
        if (objetivo instanceof Identificador id) {
            Tipo t = TipoPrimitivo.DESCONOCIDO;
            String destino = id.getNombre();
            Ambito amb = generador.getAmbito();
            if (amb != null) {
                Simbolo s = amb.resolver(id.getNombre());
                if (s != null && s.getTipo() != null) t = s.getTipo();
                if (s != null) destino = s.getNombreC3D();
            }
            return new LValue(destino, null, null, t);
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