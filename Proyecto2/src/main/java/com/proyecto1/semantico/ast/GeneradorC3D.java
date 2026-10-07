package com.proyecto1.semantico.ast;

import com.proyecto1.semantico.ast.cuadruplas.*;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

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

    // Fase 2: una entrada del dispatch dinamico. Un objeto cuya clase real tiene este
    // classId debe ejecutar la funcion "etiqueta".
    public record EntradaDispatch(int classId, String nombreClase, String etiqueta) {}

    // Nombre del campo oculto donde cada objeto guarda el id de su clase real.
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

    // ---------- Dispatch dinamico (Fase 2) ----------

    /**
     * Para una llamada "obj.metodo(...)" donde obj tiene tipo declarado "claseEstatica",
     * devuelve que funcion ejecutar segun la clase real del objeto: una entrada por cada
     * clase que puede estar detras de esa referencia (claseEstatica y todas sus subclases).
     * Se calcula desde la tabla de simbolos y no desde un registro que se llene al generar
     * cada clase: cada archivo .z tiene su propio GeneradorC3D, asi que al compilar Uso.z
     * nunca se generan Perro ni Cachorro, pero sus simbolos (con su tabla virtual) si estan
     * en el ambito global gracias a CargadorClasesZ.
     * Como se elige la funcion: el metodo ocupa un indice en la tabla virtual de
     * claseEstatica; cada subclase tiene en ese mismo indice su version (la propia si lo
     * sobrescribe o la heredada si no). La etiqueta se arma con la clase que DECLARA esa version.
     * @param claseEstatica
     * @param metodo
     * @return
     */
    public List<EntradaDispatch> dispatchPara(Simbolo claseEstatica, Simbolo metodo) {
        List<EntradaDispatch> entradas = new ArrayList<>();
        if (ambito == null || claseEstatica == null || metodo == null) return entradas;

        int indice = indiceEnTablaVirtual(claseEstatica, metodo);
        if (indice < 0) return entradas;

        for (Simbolo s : ambito.ambitoGlobal().simbolosLocales()) {
            if (s.getCategoria() != CategoriaSimbolo.CLASE || !s.esSubclaseDe(claseEstatica)) continue;
            List<Simbolo> tabla = s.getTablaVirtual();
            if (indice >= tabla.size()) continue;
            Simbolo impl = tabla.get(indice);
            entradas.add(new EntradaDispatch(s.getClassId(), s.getNombre(), etiquetaDe(impl)));
        }
        entradas.sort((a, b) -> a.nombreClase().compareTo(b.nombreClase()));
        return entradas;
    }

    /**
     * Hace falta dispatch solo si hay mas de una implementacion distinta. Si ninguna
     * subclase sobrescribe el metodo, todas las entradas apuntan a la misma etiqueta y
     * alcanza con un call directo (igual que en la Fase 1).
     * @param entradas
     * @return
     */
    public static boolean necesitaDispatch(List<EntradaDispatch> entradas) {
        Set<String> distintas = new HashSet<>();
        for (EntradaDispatch e : entradas) distintas.add(e.etiqueta());
        return distintas.size() > 1;
    }

    // Etiqueta C3D de un metodo, con la clase que lo declara (misma que usa Metodo.generarC3D).
    public static String etiquetaDe(Simbolo metodo) {
        List<Tipo> tipos = new ArrayList<>();
        for (Simbolo p : metodo.getParametros()) {
            tipos.add(p.getTipo() != null ? p.getTipo() : TipoPrimitivo.DESCONOCIDO);
        }
        String clase = (metodo.getClaseDuena() != null) ? metodo.getClaseDuena().getNombre() : "?";
        return etiquetaMetodo(clase, metodo.getNombre(), tipos);
    }

    /**
     * Indice del metodo en la tabla virtual de la clase, buscado por firma. No se usa directamente
     * metodo.getIndiceVirtual() porque ese valor se fija recien cuando se construye la tabla
     * de la clase duena, y aqui esa clase puede ser una hermana cuya tabla todavia no se pidio.
     * @param clase
     * @param metodo
     * @return
     */
    private static int indiceEnTablaVirtual(Simbolo clase, Simbolo metodo) {
        List<Simbolo> tabla = clase.getTablaVirtual();
        String firma = metodo.firma();
        for (int i = 0; i < tabla.size(); i++) {
            if (tabla.get(i).firma().equals(firma)) return i;
        }
        return -1;
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

    // ---------- Operadores logicos ----------

    /**
     * Genera "izq && der" o "izq || der" evaluando el lado derecho SOLO si hace falta:
     *  &&  -> si izq es falso el resultado ya es falso y der no se evalua
     *  ||  -> si izq es verdadero el resultado ya es verdadero y der no se evalua
     * Importa para la semantica, no solo para la eficiencia: en
     * "obj != null && obj.x > 0" evaluar obj.x con obj nulo truena, y en
     * "a > 0 || f()" la llamada no debe ejecutarse (ni sus efectos) si a > 0.
     *
     * C3D para "a && b" (el de "||" es igual con if_true y "true"):
     *      <codigo de a>              -> ta
     *      if_false ta goto Lcorto    a es falso: ya no se evalua b
     *      <codigo de b>              -> tb
     *      t = tb
     *      goto Lfin
     *  Lcorto:
     *      t = false
     *  Lfin:
     * @param operador
     * @param izquierdo
     * @param derecho
     * @return
     */
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

    // true para los operadores que se generan con corto circuito.
    public static boolean esLogicoCortoCircuito(String operador) {
        return operador.equals("&&") || operador.equals("||");
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

    /**
     * Cada uno construye el record concreto de com.proyecto1.semantico.ast.cuadruplas
     * que le corresponde. Ningún nodo de Y/Z/PigLatin necesita cambiar: siguen
     * llamando a estos mismos métodos, con la misma firma que ya usaban.
     * @param v
     * @param x
     */
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
        emitirPrint(v, false);
    }

    public void emitirPrint(String v, boolean nuevaLinea) {
        tabla.agregar(new CuadruplaPrint(v, nuevaLinea));
    }

    public void emitirRead(String x) {
        emitirRead(x, "cadena");
    }

    /** @param tipo nombre del tipo destino, para que Fase 4 use scanf o rt_read_string. */
    public void emitirRead(String x, String tipo) {
        tabla.agregar(new CuadruplaRead(x, tipo));
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
     * Sanitiza Tipo.nombre() para que sea un identificador C válido:
     * reemplaza cualquier carácter no alfanumérico (corchetes, espacios, puntos,
     * signos de interrogación…) por '_'. Si el tipo es null o DESCONOCIDO,
     * devuelve "x" (determinista, para no romper el mangling en programas con
     * errores semánticos previos).
     */
    private static String mangleTipo(Tipo t) {
        if (t == null) return "x";
        String n = t.nombre();
        if (n == null || n.isEmpty()) return "x";
        return n.replaceAll("[^A-Za-z0-9]", "_");
    }

    /**
     * Etiqueta para un método de una clase Z, CON mangling de tipos:
     *   sin parámetros  ->  "Clase_metodo"
     *   con parámetros  ->  "Clase_metodo_T1_T2_…_Tn"
     *
     * El parámetro implícito "this" NO se incluye.
     *
     * Es static para que OrquestadorC3DaC pueda construir la misma etiqueta
     * sin una instancia del generador.
     */
    public static String etiquetaMetodo(String clase, String metodo, List<Tipo> tiposFormales) {
        StringBuilder sb = new StringBuilder(clase).append("_").append(metodo);
        for (Tipo t : tiposFormales) {
            sb.append("_").append(mangleTipo(t));
        }
        return sb.toString();
    }

    /**
     * Etiqueta para un constructor de una clase Z, CON mangling de tipos:
     *   Clase()          ->  "Clase_init_a0"
     *   Clase(int)       ->  "Clase_init_a1_entero"
     *   Clase(int,String)->  "Clase_init_a2_entero_cadena"
     *
     * El parámetro implícito "this" NO se incluye.
     *
     * Es static por el mismo motivo que etiquetaMetodo.
     */
    public static String etiquetaConstructor(String clase, List<Tipo> tiposFormales) {
        StringBuilder sb = new StringBuilder(clase)
                .append("_init_a").append(tiposFormales.size());
        for (Tipo t : tiposFormales) {
            sb.append("_").append(mangleTipo(t));
        }
        return sb.toString();
    }

    /**
     * @deprecated Usa {@link #etiquetaMetodo(String, String, List)} con la lista de
     *             tipos formales. Esta sobrecarga queda sólo para no romper código
     *             transitorio; no la uses desde código nuevo.
     */
    @Deprecated
    public static String etiquetaMetodo(String clase, String metodo) {
        return etiquetaMetodo(clase, metodo, List.of());
    }

    /**
     * @deprecated Usa {@link #etiquetaConstructor(String, List)} con la lista de
     *             tipos formales. Esta sobrecarga queda sólo para no romper código
     *             transitorio; no la uses desde código nuevo.
     */
    @Deprecated
    public static String etiquetaConstructor(String clase, int aridad) {
        // Fallback: construye una lista de "x" tantos como la aridad para mantener
        // la misma cantidad de segmentos; no hay información de tipos.
        List<Tipo> dummy = java.util.Collections.nCopies(aridad, null);
        return etiquetaConstructor(clase, dummy);
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