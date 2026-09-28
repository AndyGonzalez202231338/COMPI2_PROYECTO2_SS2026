package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.List;

/**
 * ExpresionAsignacion (#expresionAsignacionDef), cuando trae operador.
 * Es una EXPRESIÓN (no instrucción): a = b = 5 es válido; usada como sentencia
 * queda envuelta en ExpresionStmt.
 */
public final class Asignacion extends NodoPigLatin implements ExpresionPigLatin {

    private final ExpresionPigLatin objetivo;
    private final String operador; // "=", "+=", "-=", "*=", "/=", "%="
    private final ExpresionPigLatin valor;

    /**
     * Tipo del lvalue, cacheado por verificar(). Se usa para saber si el RHS
     * es un literal POSICIONAL de estructura/clase (en cuyo caso se
     * valida campo por campo y se expande inline en el C3D) o un arreglo normal.
     */
    private Tipo tipoLValueCache;

    public Asignacion(ExpresionPigLatin objetivo, String operador, ExpresionPigLatin valor,
                      int linea, int columna) {
        super(linea, columna);
        this.objetivo = objetivo;
        this.operador = operador;
        this.valor = valor;
    }

    public ExpresionPigLatin getObjetivo() { return objetivo; }
    public String getOperador() { return operador; }
    public ExpresionPigLatin getValor() { return valor; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tIzq = objetivo.verificar(ambito, errores);
        this.tipoLValueCache = tIzq;

        if (operador.equals("=")
                && valor instanceof InicializadorArreglo lit
                && (tIzq instanceof TipoEstructura || tIzq instanceof TipoClase)) {

            Simbolo defStruct = (tIzq instanceof TipoEstructura te)
                    ? te.getDefinicion()
                    : ((TipoClase) tIzq).getDefinicion();

            List<Simbolo> campos = new ArrayList<>();
            for (Simbolo m : defStruct.getMiembrosEnOrden()) {
                if (m.getCategoria() == CategoriaSimbolo.CAMPO
                        || m.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                    campos.add(m);
                }
            }
            List<ExpresionPigLatin> valores = lit.getElementos();

            if (campos.size() != valores.size()) {
                errores.reportar(linea, columna,
                        "Estructura '" + defStruct.getNombre() + "' espera " + campos.size()
                                + " valores, se recibieron " + valores.size());
            } else {
                for (int i = 0; i < valores.size(); i++) {
                    Tipo tVal = valores.get(i).verificar(ambito, errores);
                    Tipo tCampo = campos.get(i).getTipo();
                    if (!Tipos.esAsignable(tCampo, tVal)) {
                        errores.reportar(valores.get(i).getLinea(), valores.get(i).getColumna(),
                                "Campo " + (i + 1) + " ('" + campos.get(i).getNombre()
                                        + "') espera " + tCampo.nombre()
                                        + ", se recibió " + tVal.nombre());
                    }
                }
            }
            return tIzq;
        }

        // Camino normal: chequeo genérico de compatibilidad.
        Tipo tDer = valor.verificar(ambito, errores);

        if (!operador.equals("=")) {
            Tipo r = Tipos.resultadoAritmetico(tIzq, tDer, operador.equals("+="));
            if (r == null)
                errores.reportar(linea, columna, "Operador '" + operador + "' no válido");
        } else if (!Tipos.esAsignable(tIzq, tDer)) {
            errores.reportar(linea, columna,
                    "No se puede asignar " + tDer.nombre() + " a " + tIzq.nombre());
        }

        if (objetivo instanceof Identificador id) {
            Simbolo s = ambito.resolver(id.getNombre());
            if (s != null) s.marcarInicializado();
        }
        return tIzq;
    }

    /**
     * Emite:
     *      Literal de estructura lvalue = {v1, v2, ...} donde el
     *          tipo del lvalue es estructura/clase): expansión inline -
     *          t = new Tipo; t.campo_i = v_i; lvalue = t. No se emite el
     *          newarr que haría InicializadorArreglo.generarC3D.
     *      x = v normal.
     *      x op= v: leer, operar, guardar.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        LValue lv = resolverLValue(objetivo, generador);

        // Literal de estructura: expandir inline.
        if (operador.equals("=")
                && valor instanceof InicializadorArreglo lit
                && (tipoLValueCache instanceof TipoEstructura || tipoLValueCache instanceof TipoClase)) {

            Simbolo defStruct = (tipoLValueCache instanceof TipoEstructura te)
                    ? te.getDefinicion()
                    : ((TipoClase) tipoLValueCache).getDefinicion();

            String t = generador.nuevoTemporal();
            generador.emitirNew(defStruct.getNombre(), t);

            List<Simbolo> campos = new ArrayList<>();
            for (Simbolo m : defStruct.getMiembrosEnOrden()) {
                if (m.getCategoria() == CategoriaSimbolo.CAMPO
                        || m.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                    campos.add(m);
                }
            }
            List<ExpresionPigLatin> valores = lit.getElementos();
            for (int i = 0; i < campos.size() && i < valores.size(); i++) {
                ResultadoC3D v = valores.get(i).generarC3D(generador);
                generador.emitirGuardarCampo(t, campos.get(i).getNombre(), v.getLugar());
            }

            guardarEn(lv, t, generador);
            return ResultadoC3D.temporal(t, tipoLValueCache);
        }

        // Camino normal.
        if (operador.equals("=")) {
            ResultadoC3D rhs = valor.generarC3D(generador);
            guardarEn(lv, rhs.getLugar(), generador);
            return ResultadoC3D.valor(rhs.getLugar(), lv.tipo());
        }

        ResultadoC3D actual = cargarDe(lv, generador);
        ResultadoC3D rhs    = valor.generarC3D(generador);
        String opBin = operador.substring(0, operador.length() - 1); // "+=" -> "+"
        String t = generador.nuevoTemporal();
        generador.emitirBinaria(opBin, actual.getLugar(), rhs.getLugar(), t);
        guardarEn(lv, t, generador);
        return ResultadoC3D.temporal(t, lv.tipo());
    }


    private record LValue(String base, String campo, String indice, Tipo tipo) {}

    private LValue resolverLValue(ExpresionPigLatin objetivo, GeneradorC3D generador) {
        // Caso 1: identificador simple (variable local o parámetro).
        if (objetivo instanceof Identificador id) {
            Tipo tipo = TipoPrimitivo.DESCONOCIDO;
            Ambito amb = generador.getAmbito();
            if (amb != null) {
                Simbolo s = amb.resolver(id.getNombre());
                if (s != null) tipo = s.getTipo();
            }
            return new LValue(id.getNombre(), null, null, tipo);
        }

        // Caso 2: obj.campo
        if (objetivo instanceof AccesoCampo ac) {
            // Subcaso especial: obj.campo donde obj es arr[i] sobre un arreglo de
            // estructuras/clases -> auto-malloc del slot antes de escribir el campo.
            if (ac.getObjeto() instanceof Indice ind) {
                return resolverCampoDeElemento(ind, ac, generador);
            }
            ResultadoC3D base = ac.getObjeto().generarC3D(generador);
            Tipo tipo = TipoPrimitivo.DESCONOCIDO;
            return new LValue(base.getLugar(), ac.getCampo(), null, tipo);
        }

        // Caso 3: arr[i]
        if (objetivo instanceof Indice ind) {
            ResultadoC3D base = ind.getArreglo().generarC3D(generador);
            ResultadoC3D idx  = ind.getIndice().generarC3D(generador);
            Tipo tipo = (ind.getTipoElemento() != null)
                    ? ind.getTipoElemento() : TipoPrimitivo.DESCONOCIDO;
            return new LValue(base.getLugar(), null, idx.getLugar(), tipo);
        }

        throw new UnsupportedOperationException(
                "Asignación a " + objetivo.getClass().getSimpleName() + ": no soportado en C3D");
    }

    /**
     * Maneja el caso arr[i].campo = v cuando arr es un arreglo de
     * estructuras o clases. Antes de escribir el campo, garantiza que el slot
     * arr[i] esté allocado (malloc si es NULL). Sin esto, escribir
     * personas[0].nombre = "..." sobre un arreglo recién creado con
     * series personas[3] : Persona; escribiría sobre basura y segfaultearía.
     *
     * C3D emitido:
     *   t_slot0 = arr[i]
     *   t_cmp   = t_slot0 == NULL
     *   if_false t_cmp goto L_skip
     *   t_new   = new Tipo
     *   arr[i]  = t_new
     *   L_skip:
     *   t_slot  = arr[i]        // recarga tras el posible malloc
     * El lvalue devuelto usa t_slot como base. El campo se escribe con t_slot.campo = v después.
     */
    private LValue resolverCampoDeElemento(Indice ind, AccesoCampo ac, GeneradorC3D generador) {
        ResultadoC3D arrRes = ind.getArreglo().generarC3D(generador);
        ResultadoC3D idxRes = ind.getIndice().generarC3D(generador);
        String arr = arrRes.getLugar();
        String idx = idxRes.getLugar();

        Tipo tipoElem = ind.getTipoElemento();
        String nombreTipo = nombreDeTipoInstanciable(tipoElem);

        // Si el elemento no es estructura/clase, no hay nada que allocar.
        if (nombreTipo == null) {
            String t = generador.nuevoTemporal();
            generador.emitirCargaIndice(arr, idx, t);
            Tipo tipoCampo = (ac.getTipoCampo() != null) ? ac.getTipoCampo() : TipoPrimitivo.DESCONOCIDO;
            return new LValue(t, ac.getCampo(), null, tipoCampo);
        }

        // Auto-malloc del slot si es NULL.
        String t_slot0 = generador.nuevoTemporal();
        generador.emitirCargaIndice(arr, idx, t_slot0);

        String t_cmp = generador.nuevoTemporal();
        generador.emitirBinaria("==", t_slot0, "NULL", t_cmp);

        String lSkip = generador.nuevaEtiqueta();
        generador.emitirIfFalse(t_cmp, lSkip);

        String t_new = generador.nuevoTemporal();
        generador.emitirNew(nombreTipo, t_new);
        generador.emitirGuardarIndice(arr, idx, t_new);

        generador.emitirEtiqueta(lSkip);

        // Recargar el slot (puede haber cambiado por el malloc de arriba).
        String t_slot = generador.nuevoTemporal();
        generador.emitirCargaIndice(arr, idx, t_slot);

        Tipo tipoCampo = (ac.getTipoCampo() != null) ? ac.getTipoCampo() : TipoPrimitivo.DESCONOCIDO;
        return new LValue(t_slot, ac.getCampo(), null, tipoCampo);
    }

    /**
     * Nombre de clase/estructura si el tipo es instanciable con malloc; null si no.
     */
    private static String nombreDeTipoInstanciable(Tipo t) {
        if (t instanceof TipoEstructura te) return te.getDefinicion().getNombre();
        if (t instanceof TipoClase tc)      return tc.getDefinicion().getNombre();
        return null;
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