package com.proyecto1.semantico.ast;

import com.proyecto1.semantico.ast.cuadruplas.*;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.*;

public class GeneradorC3D {

    private int contadorTemporales = 0;
    private int contadorEtiquetas  = 0;
    private final TablaCuadruplas tabla;
    private Ambito ambito;
    private String claseActual;

    private final Deque<String> pilaInicioCiclo = new ArrayDeque<>();
    private final Deque<String> pilaFinCiclo    = new ArrayDeque<>();
    private final Map<String, Firma> firmas = new LinkedHashMap<>();

    public record ParametroFirma(String nombre, Tipo tipo) {}
    public record Firma(String etiqueta, List<ParametroFirma> parametros, Tipo tipoRetorno, boolean esMetodo) {}
    public record EntradaDispatch(int classId, String nombreClase, String etiqueta) {}

    public static final String CAMPO_CLASS_ID = "_class_id";

    public GeneradorC3D() {
        this(null, new TablaCuadruplas());
    }

    public GeneradorC3D(TablaCuadruplas tabla) {
        this(null, tabla);
    }

    public GeneradorC3D(Ambito ambito) {
        this(ambito, new TablaCuadruplas());
    }

    public GeneradorC3D(Ambito ambito, TablaCuadruplas tabla) {
        if (tabla == null) {
            throw new IllegalArgumentException("La tabla de cuádruplas no puede ser null");
        }
        this.ambito = ambito;
        this.tabla  = tabla;
    }

    public Ambito getAmbito() {
        return ambito;
    }

    public Ambito entrarAmbito(Ambito nuevo) {
        Ambito anterior = this.ambito;
        this.ambito = nuevo;
        return anterior;
    }

    public void salirAmbito(Ambito anterior) {
        this.ambito = anterior;
    }

    public List<EntradaDispatch> dispatchPara(Simbolo claseEstatica, Simbolo metodo) {
        return dispatchPara(claseEstatica, metodo, null);
    }

    public List<EntradaDispatch> dispatchPara(Simbolo claseEstatica, Simbolo metodo,
                                              List<Tipo> tiposFallback) {
        List<EntradaDispatch> entradas = new ArrayList<>();
        if (ambito == null || claseEstatica == null || metodo == null) return entradas;

        int indice = indiceEnTablaVirtual(claseEstatica, metodo);
        if (indice < 0) return entradas;

        List<Tipo> tiposCorrectos = new ArrayList<>();
        List<Simbolo> paramsMetodo = metodo.getParametros();
        for (int i = 0; i < paramsMetodo.size(); i++) {
            Tipo tp = paramsMetodo.get(i).getTipo();
            String n = (tp != null) ? tp.nombre() : null;
            boolean roto = (n == null || n.isBlank() || n.replaceAll("[^A-Za-z0-9]", "").isEmpty());
            if (roto && tiposFallback != null && i < tiposFallback.size()) {
                tp = tiposFallback.get(i);
            }
            tiposCorrectos.add(tp != null ? tp : TipoPrimitivo.DESCONOCIDO);
        }

        for (Simbolo s : ambito.ambitoGlobal().simbolosLocales()) {
            if (s.getCategoria() != CategoriaSimbolo.CLASE || !s.esSubclaseDe(claseEstatica)) continue;
            List<Simbolo> tabla = s.getTablaVirtual();
            if (indice >= tabla.size()) continue;
            Simbolo impl = tabla.get(indice);
            String claseImpl = (impl.getClaseDuena() != null) ? impl.getClaseDuena().getNombre() : s.getNombre();
            String etiqueta = etiquetaMetodo(claseImpl, impl.getNombre(), tiposCorrectos);
            entradas.add(new EntradaDispatch(s.getClassId(), s.getNombre(), etiqueta));
        }
        entradas.sort((a, b) -> a.nombreClase().compareTo(b.nombreClase()));
        return entradas;
    }

    public static boolean necesitaDispatch(List<EntradaDispatch> entradas) {
        Set<String> distintas = new HashSet<>();
        for (EntradaDispatch e : entradas) distintas.add(e.etiqueta());
        return distintas.size() > 1;
    }

    public static String etiquetaDe(Simbolo metodo) {
        List<Tipo> tipos = new ArrayList<>();
        for (Simbolo p : metodo.getParametros()) {
            tipos.add(p.getTipo() != null ? p.getTipo() : TipoPrimitivo.DESCONOCIDO);
        }
        String clase = (metodo.getClaseDuena() != null) ? metodo.getClaseDuena().getNombre() : "?";
        return etiquetaMetodo(clase, metodo.getNombre(), tipos);
    }

    private static int indiceEnTablaVirtual(Simbolo clase, Simbolo metodo) {
        List<Simbolo> tabla = clase.getTablaVirtual();
        String firma = metodo.firma();
        for (int i = 0; i < tabla.size(); i++) {
            if (tabla.get(i).firma().equals(firma)) return i;
        }
        return -1;
    }

    public void entrarClase(String nombreClase) {
        this.claseActual = nombreClase;
    }

    public void salirClase() {
        this.claseActual = null;
    }

    public String getClaseActual() {
        return claseActual;
    }

    public void entrarCiclo(String inicio, String fin) {
        pilaInicioCiclo.push(inicio);
        pilaFinCiclo.push(fin);
    }

    public void salirCiclo() {
        if (pilaInicioCiclo.isEmpty()) {
            throw new IllegalStateException("salirCiclo() sin un entrarCiclo() previo");
        }
        pilaInicioCiclo.pop();
        pilaFinCiclo.pop();
    }

    public String etiquetaInicioCiclo() {
        return pilaInicioCiclo.peek();
    }

    public String etiquetaFinCiclo() {
        return pilaFinCiclo.peek();
    }

    public void entrarBloqueRompible(String fin) {
        pilaFinCiclo.push(fin);
    }

    public void salirBloqueRompible() {
        if (pilaFinCiclo.isEmpty()) {
            throw new IllegalStateException("salirBloqueRompible() sin un entrarBloqueRompible() previo");
        }
        pilaFinCiclo.pop();
    }

    public String nuevoTemporal() {
        return "t" + (contadorTemporales++);
    }

    public String nuevaEtiqueta() {
        return "L" + (contadorEtiquetas++);
    }

    public ResultadoC3D generarLogicoCortoCircuito(String operador,
                                                   java.util.function.Supplier<ResultadoC3D> izquierdo,
                                                   java.util.function.Supplier<ResultadoC3D> derecho) {
        boolean esAnd = operador.equals("&&");
        String resultado = nuevoTemporal();
        String etiquetaCorto = nuevaEtiqueta();
        String etiquetaFin = nuevaEtiqueta();

        ResultadoC3D a = izquierdo.get();
        if (esAnd) {
            emitirIfFalse(a.getLugar(), etiquetaCorto);
        } else {
            emitirIfTrue(a.getLugar(), etiquetaCorto);
        }

        ResultadoC3D b = derecho.get();
        emitirAsignacion(b.getLugar(), resultado);
        emitirGoto(etiquetaFin);

        emitirEtiqueta(etiquetaCorto);
        emitirAsignacion(esAnd ? "false" : "true", resultado);
        emitirEtiqueta(etiquetaFin);

        return ResultadoC3D.temporal(resultado, TipoPrimitivo.BOOL);
    }

    public static boolean esLogicoCortoCircuito(String operador) {
        return operador.equals("&&") || operador.equals("||");
    }

    public void registrarFirma(String etiqueta, List<ParametroFirma> parametros, Tipo tipoRetorno, boolean esMetodo) {
        firmas.put(etiqueta, new Firma(etiqueta, parametros, tipoRetorno, esMetodo));
    }

    public Map<String, Firma> getFirmas() {
        return firmas;
    }

    public void emitirAsignacion(String v, String x) {
        tabla.agregar(new CuadruplaAsignacion(v, x));
    }

    public void emitirBinaria(String op, String a, String b, String t) {
        tabla.agregar(new CuadruplaBinaria(op, a, b, t));
    }

    public void emitirBinaria(String op, String a, String b, String t, String tipoDato) {
        tabla.agregar(new CuadruplaBinaria(op, a, b, t, tipoDato));
    }

    public void emitirUnaria(String op, String a, String t) {
        tabla.agregar(new CuadruplaUnaria(op, a, t));
    }

    public void emitirUnaria(String op, String a, String t, String tipoDato) {
        tabla.agregar(new CuadruplaUnaria(op, a, t, tipoDato));
    }

    public void emitirGoto(String etiqueta) {
        tabla.agregar(new CuadruplaGoto(etiqueta));
    }

    public void emitirIfFalse(String condicion, String etiqueta) {
        tabla.agregar(new CuadruplaIfFalse(condicion, etiqueta));
    }

    public void emitirIfTrue(String condicion, String etiqueta) {
        tabla.agregar(new CuadruplaIfTrue(condicion, etiqueta));
    }

    public void emitirEtiqueta(String etiqueta) {
        tabla.agregar(new CuadruplaEtiqueta(etiqueta));
    }

    public void emitirPrint(String v) {
        emitirPrint(v, false);
    }

    public void emitirPrint(String v, boolean nuevaLinea) {
        tabla.agregar(new CuadruplaPrint(v, nuevaLinea));
    }

    public void emitirPrint(String v, boolean nuevaLinea, String tipoDato) {
        tabla.agregar(new CuadruplaPrint(v, nuevaLinea, tipoDato));
    }

    public void emitirConcat(String a, String b, String t) {
        tabla.agregar(new CuadruplaConcat(a, b, t));
    }

    public void emitirCompCadena(String operador, String a, String b, String t) {
        tabla.agregar(new CuadruplaCompCadena(operador, a, b, t));
    }

    public void emitirConversion(String tipoOrigen, String tipoDestino, String valor, String destino) {
        tabla.agregar(new CuadruplaConversion(tipoOrigen, tipoDestino, valor, destino));
    }

    public void emitirRead(String x) {
        emitirRead(x, "cadena");
    }

    public void emitirRead(String x, String tipo) {
        tabla.agregar(new CuadruplaRead(x, tipo));
    }

    public void emitirCall(String f, int nArgs, String t) {
        tabla.agregar(new CuadruplaCall(f, nArgs, t));
    }

    public void emitirCall(String funcion, int nArgs, String destino, String tipoRetorno) {
        tabla.agregar(new CuadruplaCall(funcion, nArgs, destino, tipoRetorno));
    }

    public void emitirParam(String v) {
        tabla.agregar(new CuadruplaParam(v));
    }

    public void emitirReturn(String v) {
        tabla.agregar(new CuadruplaReturn(v));
    }

    public void emitirBeginFunc(String nombre, int nArgs) {
        tabla.agregar(new CuadruplaBeginFunc(nombre, nArgs));
    }

    public void emitirEndFunc() {
        tabla.agregar(new CuadruplaEndFunc());
    }

    public void emitirCargaIndice(String arr, String idx, String t) {
        tabla.agregar(new CuadruplaIndiceCarga(arr, idx, t));
    }

    public void emitirCargaIndice(String arr, String idx, String t, String tipoElemento) {
        tabla.agregar(new CuadruplaIndiceCarga(arr, idx, t, tipoElemento));
    }

    public void emitirGuardarIndice(String arr, String idx, String v) {
        tabla.agregar(new CuadruplaIndiceGuarda(arr, idx, v));
    }

    public void emitirCargaCampo(String obj, String campo, String t) {
        tabla.agregar(new CuadruplaCampoCarga(obj, campo, t));
    }

    public void emitirGuardarCampo(String obj, String campo, String v) {
        tabla.agregar(new CuadruplaCampoGuarda(obj, campo, v));
    }

    // Variante con la clase del objeto: resuelve el offset del campo sin ambiguedad
    // cuando dos clases distintas tienen un campo con el mismo nombre en posiciones
    // diferentes.
    public void emitirGuardarCampo(String obj, String campo, String v, String claseObjeto) {
        tabla.agregar(new CuadruplaCampoGuarda(obj, campo, v, claseObjeto));
    }

    public void emitirCargaCampo(String objeto, String campo, String destino, String tipoCampo) {
        tabla.agregar(new CuadruplaCampoCarga(objeto, campo, destino, tipoCampo));
    }

    // Variante con tipo del campo y bandera de puntero (para Zetariano)
    public void emitirCargaCampo(String objeto, String campo, String destino, String tipoCampo, boolean esPuntero) {
        tabla.agregar(new CuadruplaCampoCarga(objeto, campo, destino, tipoCampo, esPuntero));
    }

    // Variante completa: tipo + puntero + clase del objeto.
    public void emitirCargaCampo(String objeto, String campo, String destino, String tipoCampo,
                                 boolean esPuntero, String claseObjeto) {
        tabla.agregar(new CuadruplaCampoCarga(objeto, campo, destino, tipoCampo, esPuntero, claseObjeto));
    }

    public void emitirNew(String nombreClase, String t) {
        tabla.agregar(new CuadruplaNew(nombreClase, t));
    }

    public void emitirNewArray(String tipoDescriptor, java.util.List<String> tamanos, String t) {
        tabla.agregar(new CuadruplaNewArray(tipoDescriptor, tamanos, t));
    }

    public void emitirNewArray(String tipoDescriptor, String tamano, String t) {
        emitirNewArray(tipoDescriptor, java.util.List.of(tamano), t);
    }

    private static String mangleTipo(Tipo t) {
        if (t == null) return "x";
        String n = t.nombre();
        if (n == null || n.isEmpty()) return "x";
        return n.replaceAll("[^A-Za-z0-9]", "_");
    }

    public static String etiquetaMetodo(String clase, String metodo, List<Tipo> tiposFormales) {
        StringBuilder sb = new StringBuilder(clase).append("_").append(metodo);
        for (Tipo t : tiposFormales) {
            sb.append("_").append(mangleTipo(t));
        }
        return sb.toString();
    }

    public static String etiquetaConstructor(String clase, List<Tipo> tiposFormales) {
        StringBuilder sb = new StringBuilder(clase)
                .append("_init_a").append(tiposFormales.size());
        for (Tipo t : tiposFormales) {
            sb.append("_").append(mangleTipo(t));
        }
        return sb.toString();
    }

    @Deprecated
    public static String etiquetaMetodo(String clase, String metodo) {
        return etiquetaMetodo(clase, metodo, List.of());
    }

    @Deprecated
    public static String etiquetaConstructor(String clase, int aridad) {
        List<Tipo> dummy = java.util.Collections.nCopies(aridad, null);
        return etiquetaConstructor(clase, dummy);
    }

    public TablaCuadruplas getTabla() {
        return tabla;
    }

    public int siguienteIndice() {
        return tabla.siguienteIndice();
    }

    public void reemplazar(int indice, Cuadrupla nueva) {
        tabla.reemplazar(indice, nueva);
    }

    public List<Cuadrupla> getCuadruplas() {
        return tabla.getCuadruplas();
    }
}