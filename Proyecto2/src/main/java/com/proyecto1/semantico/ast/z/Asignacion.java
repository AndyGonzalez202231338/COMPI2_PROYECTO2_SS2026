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

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        LValue lv = resolverLValue(objetivo, generador);

        if (operador.equals("=")) {
            ResultadoC3D rhs = valor.generarC3D(generador);
            guardarEn(lv, rhs.getLugar(), generador);
            return ResultadoC3D.valor(rhs.getLugar(), lv.tipo());
        }

        ResultadoC3D actual = cargarDe(lv, generador);
        ResultadoC3D rhs    = valor.generarC3D(generador);
        String opBin = operador.substring(0, operador.length() - 1);
        String t = generador.nuevoTemporal();
        generador.emitirBinaria(opBin, actual.getLugar(), rhs.getLugar(), t);
        guardarEn(lv, t, generador);
        return ResultadoC3D.temporal(t, lv.tipo());
    }

    private record LValue(String base, String campo, String indice, Tipo tipo) {}

    private LValue resolverLValue(ExpresionZ objetivo, GeneradorC3D generador) {
        if (objetivo instanceof Identificador id) {
            String nombre = id.getNombre();
            Tipo tipo = TipoPrimitivo.DESCONOCIDO;
            CategoriaSimbolo cat = null;
            Simbolo simb = null;

            Ambito amb = generador.getAmbito();
            if (amb != null) {
                simb = amb.resolver(nombre);
                if (simb != null) {
                    tipo = simb.getTipo();
                    cat  = simb.getCategoria();
                }
            }
            if (cat == CategoriaSimbolo.ATRIBUTO) {
                return new LValue("this", nombre, null, tipo);
            }
            String nombreC3D = (simb != null) ? simb.getNombreC3D() : nombre;
            return new LValue(nombreC3D, null, null, tipo);
        }

        if (objetivo instanceof AccesoCampo ac) {
            ResultadoC3D base = ac.getObjeto().generarC3D(generador);
            Tipo tipo = (ac.getTipoCampo() != null) ? ac.getTipoCampo() : TipoPrimitivo.DESCONOCIDO;
            return new LValue(base.getLugar(), ac.getCampo(), null, tipo);
        }

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
            String tipoStr = (lv.tipo() != null) ? lv.tipo().nombre() : null;
            boolean esPuntero = (lv.tipo() != null && lv.tipo().esArreglo());
            generador.emitirCargaCampo(lv.base(), lv.campo(), t, tipoStr, esPuntero);
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