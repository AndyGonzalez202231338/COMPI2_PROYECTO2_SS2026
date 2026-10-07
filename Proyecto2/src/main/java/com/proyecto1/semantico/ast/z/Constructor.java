package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.tabla.ModificadorAcceso;
import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Acceso;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoClase;
import com.proyecto1.semantico.tabla.AmbitoFuncion;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.ArrayList;
import java.util.List;

/**
 * (#constructorDef): "NombreClase ( parametros? ) { cuerpo }".
 *
 * El nombre del constructor coincide con el de su clase; guarda ese
 * nombre (es lo que después alimenta a
 * #etiquetaConstructor(String, int)).
 *
 * Todo método/constructor de Z recibe un parámetro implícito extra: "this". Por eso
 * el @code begin_func lleva parametros.size() + 1 argumentos. El "this" no se
 * declara como símbolo en la tabla (no es un identificador resoluble); es una
 * convención de generación: cualquier acceso a un atributo emite
 * (=., this, campo, t) / (.=, this, campo, v).
 */
public final class Constructor extends NodoZ /* o la base que ya uses */ {

    private final String nombre;              // == nombre de la clase
    private final List<Parametro> parametros;
    private final Bloque cuerpo;              // o InstruccionZ, según tu gramática

    /**
     * Ámbito de la función, creado y validado por verificar() y reutilizado por
     * generarC3D() para no reconstruir parámetros ni volver a tocar ManejadorErrores.
     * Es el mismo patrón que AccesoCampo usa para cachear tipoCampo.
     */
    private AmbitoFuncion ambitoPropio;
    private String nombreClaseReal;

    // Fase 2: modificador de acceso; DEFAULT si no se escribio ninguno.
    private final ModificadorAcceso modificador;

    // Fase 2: true si lo agrego Clase porque la clase no declaro ningun constructor.
    private boolean implicito = false;

    /**
     * Fase 2: llamada al constructor del padre, resuelta por verificar().
     *      superExplicito -> "super(args);" escrito como primera sentencia del cuerpo\
     *      superImplicito -> constructor sin parametros del padre, cuando no se escribio super(...)
     * A lo sumo uno de los dos es distinto de null, y ambos son null si la clase no hereda.
     */
    private Llamada superExplicito;
    private Simbolo superImplicito;

    // Constructor por defecto que se agrega a una clase sin constructores: public, sin parametros y con cuerpo vacio.
    public static Constructor implicito(String nombreClase, int linea, int columna) {
        Constructor c = new Constructor(ModificadorAcceso.PUBLIC, nombreClase, new ArrayList<>(),
                new Bloque(new ArrayList<>(), linea, columna), linea, columna);
        c.implicito = true;
        return c;
    }

    public boolean esImplicito() { return implicito; }

    // Constructor de la Fase 1: constructor public.
    public Constructor(String nombre, List<Parametro> parametros, Bloque cuerpo,
                       int linea, int columna) {
        this(ModificadorAcceso.PUBLIC, nombre, parametros, cuerpo, linea, columna);
    }

    public Constructor(ModificadorAcceso modificador, String nombre, List<Parametro> parametros,
                       Bloque cuerpo, int linea, int columna) {
        super(linea, columna);
        this.modificador = (modificador != null) ? modificador : ModificadorAcceso.DEFAULT;
        this.nombre = nombre;
        this.parametros = parametros;
        this.cuerpo = cuerpo;
    }

    public String getNombre() { return nombre; }
    public ModificadorAcceso getModificador() { return modificador; }
    public List<Parametro> getParametros() { return parametros; }
    public Bloque getCuerpo() { return cuerpo; }
    public AmbitoFuncion getAmbitoPropio() { return ambitoPropio; }

    /**
     * Idéntico al verificar() que ya tenías, MÁS una línea al final del setup del
     * ámbito: cachear el {AmbitoFuncion} recién creado en #ambitoPropio
     * para que generarC3D() lo reutilice.
     */
    public Tipo verificar(AmbitoClase ambClase, ManejadorErrores errores) {
        /**
         * (1) Guardar el nombre REAL de la clase para usarlo al generar la etiqueta C3D.
         *      El error por nombre incorrecto YA se reportó en AnalizadorSemanticoZ;
         *      aquí no se vuelve a chequear (evita el mensaje duplicado).
         */
        this.nombreClaseReal = ambClase.getSimboloContenedor().getNombre();

        // (2) Lookup con clave específica: nombreClaseReal#aridad#Tipo1#Tipo2...
        StringBuilder sb = new StringBuilder(nombreClaseReal)
                .append("#").append(parametros.size());
        for (Parametro p : parametros) {
            Tipo tp = p.getTipo().resolver(ambClase, errores);
            sb.append("#").append(tp.nombre());
        }
        Simbolo simbolo = ambClase.getSimboloContenedor().buscarMiembro(sb.toString());

        AmbitoFuncion amb = new AmbitoFuncion(ambClase, simbolo);
        this.ambitoPropio = amb;

        for (Parametro p : parametros) {
            Tipo t = p.resolverTipo(amb, errores);
            Simbolo sp = new Simbolo(p.getNombre(), CategoriaSimbolo.PARAMETRO,
                    t, p.getLinea(), p.getColumna());
            if (!amb.declarar(sp)) {
                errores.reportar(p.getLinea(), p.getColumna(),
                        "Parámetro duplicado: '" + p.getNombre() + "'");
            }
        }

        /**
         * (3) Herencia: llamada al constructor del padre.
         * Si la primera sentencia es super(...), esa Llamada queda autorizada (en cualquier
         * otro lugar super(...) es error). La resuelve la propia Llamada al verificar el cuerpo.
         */
        this.superExplicito = primeraSentenciaSuper();
        if (superExplicito != null) superExplicito.autorizarSuperConstructor();

        cuerpo.verificar(amb, errores);

        // Sin super(...) explicito se llama al constructor sin parametros del padre,
        // igual que Java. Si el padre no tiene uno, hay que escribir super(...).
        Simbolo padre = ambClase.getClasePadre();
        if (padre != null && superExplicito == null) {
            Simbolo ctorPadre = padre.buscarMiembroLocal(padre.getNombre() + "#0");
            if (ctorPadre == null || ctorPadre.getCategoria() != CategoriaSimbolo.CONSTRUCTOR) {
                String donde = implicito
                        ? "declare un constructor en '" + nombreClaseReal + "' que llame a super(...)"
                        : "llame a super(...) como primera sentencia del constructor";
                errores.reportar(linea, columna,
                        "La clase padre '" + padre.getNombre()
                                + "' no tiene un constructor sin parametros; " + donde);
            } else if (Acceso.verificar(ctorPadre, amb, errores, linea, columna)) {
                this.superImplicito = ctorPadre;
            }
        }
        return TipoPrimitivo.VOID;
    }

    // La Llamada "super(args)" si es la primera sentencia del cuerpo, si no null.
    private Llamada primeraSentenciaSuper() {
        List<InstruccionZ> instrucciones = cuerpo.getInstrucciones();
        if (instrucciones.isEmpty()) return null;
        if (instrucciones.get(0) instanceof ExpresionStmt es
                && es.getExpresion() instanceof Llamada ll
                && ll.esSuperConstructor()) {
            return ll;
        }
        return null;
    }


    /**
     *   (begin_func, etiquetaConstructor(nombre, aridad), parametros.size()+1, null).
     *       El +1 es el "this" implícito.
     *   entrarAmbito(ambitoPropio)}.
     *   Por cada atributo con inicializador no nulo:
     *       this.<campo> = <C3D del inicializador> (una cuádrupla (.=, this, campo, v)).
     *   C3D del cuerpo del usuario.
     *   salirAmbito(anterior).
     *   (end_func).
     * La firma lleva atributosClase porque el constructor debe inyectar los
     * field initializers de la clase y Constructor por sí solo no los conoce.
     * Clase#generarC3D es quien los pasa.
     */
    public ResultadoC3D generarC3D(GeneradorC3D generador, List<Atributo> atributosClase) {
        // Usar el nombre real de la clase (si verificar ya corrió), no el declarado.
        String nombreParaEtiqueta = (nombreClaseReal != null) ? nombreClaseReal : nombre;

        // Tipos formales de los parámetros (excluye "this").
        List<Tipo> tiposFormales = new java.util.ArrayList<>();
        for (Parametro p : parametros) {
            Tipo tp = (ambitoPropio != null)
                    ? ambitoPropio.resolverLocal(p.getNombre()) != null
                    ? ambitoPropio.resolverLocal(p.getNombre()).getTipo()
                    : null
                    : null;
            tiposFormales.add(tp != null ? tp : com.proyecto1.semantico.tipos.TipoPrimitivo.DESCONOCIDO);
        }
        String etiqueta = GeneradorC3D.etiquetaConstructor(nombreParaEtiqueta, tiposFormales);

        // --- Registrar la firma ANTES del begin_func ---
        // "this" primero (TipoClase del contenedor), luego los formales en orden.
        List<GeneradorC3D.ParametroFirma> pfs = new ArrayList<>();

        Simbolo simboloClase = null;
        if (ambitoPropio != null && ambitoPropio.getPadre() instanceof AmbitoClase ac) {
            simboloClase = ac.getSimboloContenedor();
        }
        Tipo tipoThis = (simboloClase != null)
                ? new TipoClase(simboloClase)
                : TipoPrimitivo.DESCONOCIDO;
        pfs.add(new GeneradorC3D.ParametroFirma("this", tipoThis));

        for (Parametro p : parametros) {
            // El parámetro ya está declarado en ambitoPropio por verificar(): lo leemos de ahí
            // en vez de volver a resolver el NodoTipoRef (que requeriría un ManejadorErrores).
            Simbolo sp = (ambitoPropio != null) ? ambitoPropio.resolverLocal(p.getNombre()) : null;
            Tipo tp = (sp != null && sp.getTipo() != null) ? sp.getTipo() : TipoPrimitivo.DESCONOCIDO;
            pfs.add(new GeneradorC3D.ParametroFirma(p.getNombre(), tp));
        }

        // Un constructor NO retorna valor y SÍ es método de clase (tiene "this").
        generador.registrarFirma(etiqueta, pfs, TipoPrimitivo.VOID, true);
        // --- fin registro de firma ---

        generador.emitirBeginFunc(etiqueta, parametros.size() + 1);

        Ambito anterior = generador.entrarAmbito(ambitoPropio);

        /**
         * Orden de Java: primero el constructor del padre, despues los inicializadores
         * de los atributos propios y al final el resto del cuerpo. Por eso, si la primera
         * sentencia es super(...), se genera antes que los inicializadores.
         */
        List<InstruccionZ> instrucciones = cuerpo.getInstrucciones();
        int desde = 0;
        if (superExplicito != null) {
            instrucciones.get(0).generarC3D(generador);
            desde = 1;
        } else if (superImplicito != null) {
            String etiquetaPadre = GeneradorC3D.etiquetaConstructor(
                    superImplicito.getClaseDuena().getNombre(), new ArrayList<>());
            generador.emitirParam("this");
            generador.emitirCall(etiquetaPadre, 1, null);
        }

        /**
         * Campo oculto _class_id (posicion 0 del objeto): la clase REAL del objeto, que es
         * lo que compara el dispatch dinamico. Se escribe DESPUES de la llamada al
         * constructor del padre porque ese constructor tambien escribe su propio id: al
         * crear un Cachorro se ejecuta Animal_init (escribe Animal), luego Perro_init
         * (escribe Perro) y por ultimo Cachorro_init, que deja el valor final correcto.
         * Consecuencia: mientras corre el constructor del padre, el objeto todavia figura
         * como de la clase padre (en Java ya figuraria como la hija).
         */
        if (simboloClase != null && simboloClase.getClassId() >= 0) {
            generador.emitirGuardarCampo("this", GeneradorC3D.CAMPO_CLASS_ID,
                    String.valueOf(simboloClase.getClassId()));
        }

        for (Atributo a : atributosClase) {
            if (a.getInicializador() != null) {
                ResultadoC3D v = a.getInicializador().generarC3D(generador);
                generador.emitirGuardarCampo("this", a.getNombre(), v.getLugar());
            }
        }

        for (int i = desde; i < instrucciones.size(); i++) {
            instrucciones.get(i).generarC3D(generador);
        }

        generador.salirAmbito(anterior);
        generador.emitirEndFunc();
        return ResultadoC3D.vacio();
    }
}