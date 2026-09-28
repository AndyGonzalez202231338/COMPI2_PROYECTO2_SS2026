package com.proyecto1.semantico.ast;

import com.proyecto1.semantico.ast.cuadruplas.*;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;

import java.util.*;

/**
 * Servicios compartidos que necesita cualquier nodo al traducirse a C3D:
 *   - pedir un temporal nuevo (t0, t1, ...),
 *   - pedir una etiqueta nueva (L0, L1, ... para saltos de si/mientras/para),
 *   - emitir cuádruplas ESTRUCTURADAS en la tabla global (una clase concreta de
 *     com.proyecto1.semantico.ast.cuadruplas por cada tipo de instrucción, ver
 *     Cuadrupla),
 *   - consultar el Ámbito (tabla de símbolos) para resolver tipos de identificadores,
 *   - construir etiquetas de función/método/constructor (mangling),
 *   - registrar la firma (parámetros + tipo de retorno) de cada función/método/
 *     constructor emitido, para que la Fase 4 (C3D -> C) pueda escribir cabeceras
 *     de función sin tener que volver a caminar el AST.
 *
 * Cada nodo recibe este generador en generarC3D(generador), de modo que ningún nodo
 * guarda estado propio ("en qué temporal voy"). Las cuádruplas viven solo en la
 * TablaCuadruplas de este generador (no en ResultadoC3D), lo que permite backpatching
 * global mediante siguienteIndice() y reemplazar(int, Cuadrupla).
 *
 * El Ámbito es opcional: si se construye sin él, getAmbito() devuelve null y los nodos
 * que lo necesitan caen a TipoPrimitivo.DESCONOCIDO. Para Z es OBLIGATORIO pasarlo
 * (Identificador y Asignación de Z lo necesitan para distinguir ATRIBUTO vs. variable).
 *
 * Los métodos emitirXxx(...) de más abajo son la ÚNICA forma en que un nodo del AST
 * agrega una cuádrupla: por dentro, cada uno construye el record concreto que le
 * corresponde (CuadruplaBinaria, CuadruplaCall, ...) — así ningún nodo de Y, Z o
 * PigLatin necesita conocer esas clases directamente ni cambiar si su forma interna
 * cambia, siempre que la firma del emitirXxx() se mantenga.
 */
public class GeneradorC3D {

    private int contadorTemporales = 0;
    private int contadorEtiquetas  = 0;
    private final TablaCuadruplas tabla;
    private Ambito ambito; // mutable: se intercambia al entrar/salir de funciones (Z)
    private String claseActual;

    /**
     * Pilas de etiquetas de los ciclos abiertos, con el ciclo más interno en el tope.
     * Siempre se apilan y desapilan juntas (entrarCiclo/salirCiclo), así que tienen
     * el mismo tamaño. Las llenan Mientras, Para y HacerMientras y las consultan
     * Continuar y Romper. Se usan pilas (no un solo par) para que continuar/romper
     * siempre apunten al ciclo más interno cuando hay ciclos anidados.
     */
    private final Deque<String> pilaInicioCiclo = new ArrayDeque<>();
    private final Deque<String> pilaFinCiclo    = new ArrayDeque<>();

    /**
     * Firma de cada función/método/constructor emitido con emitirBeginFunc.
     * begin_func en el C3D solo guarda (nombre, nArgs) — el CONTEO de parámetros, no
     * sus nombres ni tipos, ni el tipo de retorno. La Fase 4 (C3D -> C) necesita eso
     * para escribir la cabecera de cada función en C; esta tabla lo conserva junto al
     * C3D en vez de obligar al traductor a volver a caminar el AST o la tabla de
     * símbolos. Se llena aparte, con registrarFirma, junto a cada llamada a
     * emitirBeginFunc (no automáticamente: begin_func no conoce los Simbolo de los
     * parámetros, solo el conteo).
     */
    private final Map<String, Firma> firmas = new LinkedHashMap<>();

    /** Un parámetro dentro de una Firma: su nombre en el código fuente y su tipo. */
    public record ParametroFirma(String nombre, Tipo tipo) {}

    /**
     * Firma completa de una función/método/constructor, indexada por la misma
     * etiqueta que se le pasó a emitirBeginFunc.
     *
     * El parámetro "esMetodo" es true si el primer parámetro real (no incluido en
     * "parametros") es un "this"/receptor implícito de Z — la Fase 4 lo necesita
     * para saber si debe anteponer "NombreClase* this" a la firma en C.
     */
    public record Firma(String etiqueta, List<ParametroFirma> parametros, Tipo tipoRetorno, boolean esMetodo) {}

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

    // ---------- Ámbito ----------

    public Ambito getAmbito() {
        return ambito;
    }

    /**
     * Cambia el ámbito activo y devuelve el anterior para restaurarlo luego.
     * Patrón de uso (típico en Constructor/Metodo.generarC3D):
     *
     *   Ambito anterior = generador.entrarAmbito(ambitoPropio);
     *   try { ... emitir cuerpo ... } finally { generador.salirAmbito(anterior); }
     *
     * Se devuelve el anterior en lugar de apilarlos porque el consumidor necesita
     * restaurarlo en el mismo orden; no hay anidamiento arbitrario (una función no
     * contiene a otra), así que no hace falta una pila.
     */
    public Ambito entrarAmbito(Ambito nuevo) {
        Ambito anterior = this.ambito;
        this.ambito = nuevo;
        return anterior;
    }

    /** Restaura el ámbito devuelto por entrarAmbito(Ambito). */
    public void salirAmbito(Ambito anterior) {
        this.ambito = anterior;
    }

    // ---------- Contexto de clase (para mangling de métodos) ----------

    public void entrarClase(String nombreClase) {
        this.claseActual = nombreClase;
    }

    public void salirClase() {
        this.claseActual = null;
    }

    public String getClaseActual() {
        return claseActual;
    }

    // ---------- Pilas de ciclos (para continuar / romper) ----------

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

    // ---------- Temporales y etiquetas ----------

    public String nuevoTemporal() {
        return "t" + (contadorTemporales++);
    }

    public String nuevaEtiqueta() {
        return "L" + (contadorEtiquetas++);
    }

    // ---------- Firmas de función (Fase 4) ----------

    /**
     * Registra la firma de la función/método/constructor cuya etiqueta ya se pasó a
     * emitirBeginFunc. Se llama por separado (no dentro de emitirBeginFunc) porque
     * begin_func no conoce los Simbolo de los parámetros — cada nodo que ya arma esa
     * lista para verificar()/generarC3D() (Funcion en Y, Constructor/Metodo en Z,
     * FuncionPrincipal en PigLatin) es quien la tiene a mano.
     */
    public void registrarFirma(String etiqueta, List<ParametroFirma> parametros, Tipo tipoRetorno, boolean esMetodo) {
        firmas.put(etiqueta, new Firma(etiqueta, parametros, tipoRetorno, esMetodo));
    }

    /** Todas las firmas registradas hasta ahora, indexadas por etiqueta de begin_func. */
    public Map<String, Firma> getFirmas() {
        return firmas;
    }

    // ---------- Emisores tipados ----------
    // Cada uno construye el record concreto de com.proyecto1.semantico.ast.cuadruplas
    // que le corresponde. Ningún nodo de Y/Z/PigLatin necesita cambiar: siguen
    // llamando a estos mismos métodos, con la misma firma que ya usaban.

    public void emitirAsignacion(String v, String x) {
        tabla.agregar(new CuadruplaAsignacion(v, x));
    }

    public void emitirBinaria(String op, String a, String b, String t) {
        tabla.agregar(new CuadruplaBinaria(op, a, b, t));
    }

    public void emitirUnaria(String op, String a, String t) {
        tabla.agregar(new CuadruplaUnaria(op, a, t));
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
        tabla.agregar(new CuadruplaPrint(v));
    }

    public void emitirRead(String x) {
        tabla.agregar(new CuadruplaRead(x));
    }

    /** t = call f(nArgs argumentos); t puede ser null si no se usa el valor. */
    public void emitirCall(String f, int nArgs, String t) {
        tabla.agregar(new CuadruplaCall(f, nArgs, t));
    }

    /** param v : empuja v como argumento de la próxima call. Orden = orden fuente. */
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

    // ---------- Arreglos y campos ----------

    /** t = arr[i]  ->  CuadruplaIndiceCarga. Fase 4 aplica base + i*tamañoElemento. */
    public void emitirCargaIndice(String arr, String idx, String t) {
        tabla.agregar(new CuadruplaIndiceCarga(arr, idx, t));
    }

    /** arr[i] = v  ->  CuadruplaIndiceGuarda. */
    public void emitirGuardarIndice(String arr, String idx, String v) {
        tabla.agregar(new CuadruplaIndiceGuarda(arr, idx, v));
    }

    /** t = obj.f  ->  CuadruplaCampoCarga. El campo va por NOMBRE, no por offset. */
    public void emitirCargaCampo(String obj, String campo, String t) {
        tabla.agregar(new CuadruplaCampoCarga(obj, campo, t));
    }

    /** obj.f = v  ->  CuadruplaCampoGuarda. */
    public void emitirGuardarCampo(String obj, String campo, String v) {
        tabla.agregar(new CuadruplaCampoGuarda(obj, campo, v));
    }

    // ---------- Objetos y arreglos dinámicos ----------

    /**
     * t = new NombreClase  ->  CuadruplaNew.
     * Fase 4 lo traduce a t = malloc(sizeof(NombreClase)).
     */
    public void emitirNew(String nombreClase, String t) {
        tabla.agregar(new CuadruplaNew(nombreClase, t));
    }

    /** t = new Tipo[t1][t2]...[tn]  ->  CuadruplaNewArray. Fase 4: malloc con el producto de tamaños. */
    public void emitirNewArray(String tipoDescriptor, java.util.List<String> tamanos, String t) {
        tabla.agregar(new CuadruplaNewArray(tipoDescriptor, tamanos, t));
    }

    /** Atajo 1D: t = new Tipo[n]. Equivale a emitirNewArray(tipo, List.of(n), t). */
    public void emitirNewArray(String tipoDescriptor, String tamano, String t) {
        emitirNewArray(tipoDescriptor, java.util.List.of(tamano), t);
    }

    /**
     * Etiqueta para un método de una clase Z: "Clase_metodo".
     * Los métodos NO se sobrecargan en Z (declararMiembro los guarda por nombre plano).
     *
     * Es static (no depende de ningún estado de esta instancia) para que la Fase 4
     * (OrquestadorC3DaC, al armar el main() de un programa Zetariano) pueda construir
     * la misma etiqueta sin necesitar un GeneradorC3D a mano — solo el nombre de la
     * clase y el método.
     */
    public static String etiquetaMetodo(String clase, String metodo) {
        return clase + "_" + metodo;
    }

    /**
     * Etiqueta para un constructor de una clase Z: "Clase_init_aN" (N = aridad).
     * Los constructores SÍ se sobrecargan por aridad, de ahí el sufijo.
     *
     * Por qué "_aN" y no "@N": la etiqueta de un constructor termina siendo, tal cual,
     * el NOMBRE DE LA FUNCIÓN en el C generado (ver CuadruplaBeginFunc.nombre() y
     * cada CuadruplaCall.funcion() que la referencia) — y @ no es un carácter válido
     * en un identificador de C. Antes esta etiqueta era "Clase_init@N", lo cual
     * compilaba bien como C3D pero generaba C inválido en CUALQUIER llamada a un
     * constructor. Es un cambio de formato, no de significado: sigue siendo "nombre
     * de clase + aridad", legible y sin colisión entre aridades distintas del mismo
     * constructor.
     *
     * Es static por el mismo motivo que etiquetaMetodo: la Fase 4 necesita poder
     * reconstruir esta etiqueta (para buscar el constructor de 0 argumentos de la
     * clase de entrada al armar main()) sin una instancia.
     *
     * Deuda detectada: hoy Constructor.verificar intenta resolver el constructor
     * buscando por nombre + "@" + aridad (con el separador viejo), pero
     * AmbitoContenedor.declararMiembro los guarda por nombre plano. Es una
     * incoherencia preexistente entre esas dos clases (no de C3D, y no la resuelve
     * este cambio). Antes de generar C3D con constructores sobrecargados, hay que
     * decidir cuál de las dos se arregla: o declararMiembro usa una clave única al
     * declararlos, o Constructor.verificar busca por nombre plano.
     */
    public static String etiquetaConstructor(String clase, int aridad) {
        return clase + "_init_a" + aridad;
    }

    // ---------- Acceso a la tabla / backpatching ----------

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