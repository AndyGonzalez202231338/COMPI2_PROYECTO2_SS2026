package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.*;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * primaryExpression LPAREN argumentList? RPAREN (#primarioLlamada).
 * Formas que cubre segun el objetivo:
 *   metodo(args)         objetivo = Identificador   metodo de la clase actual (o heredado)
 *   obj.metodo(args)     objetivo = AccesoCampo     metodo de otro objeto
 *   super.metodo(args)   objetivo = AccesoCampo(Super, ...)  version del padre
 *   super(args)          objetivo = Super           constructor del padre
 * Fase 2:
 *  - Los metodos se resuelven con ResolucionMiembros: busca en la cadena de herencia
 *    y acepta argumentos que sean subclases del parametro.
 *  - Se valida el encapsulamiento del metodo desde la clase actual.
 *  - La etiqueta C3D se arma con la clase que DECLARA el metodo (claseDuena), no con
 *    el tipo del objeto: si Perro hereda comer() de Animal, la funcion que existe es Animal_comer, no Perro_comer.
 *  - Se deja registrado si la llamada debe despacharse en forma dinamica (ver
 *    esDespachoDinamico) junto con el indice en la tabla virtual.
 */
public final class Llamada extends NodoZ implements ExpresionZ {

    private final ExpresionZ objetivo;
    private final List<ExpresionZ> argumentos;

    // Metodo o constructor resuelto por verificar(). null si verificar() no corrio o fallo.
    private Simbolo simboloMetodo;

    // super(args) solo es valido como primera sentencia de un constructor. Constructor
    // lo autoriza antes de verificar su cuerpo; en cualquier otro lugar queda en false.
    private boolean superConstructorAutorizado = false;

    // false cuando la llamada va a una implementacion fija: super.metodo(), super(args)
    // y metodos private (no se pueden sobrescribir). true en el resto de casos.
    private boolean despachoDinamico = false;

    // Tipo DECLARADO del receptor (la clase actual en "metodo(args)", el tipo de obj en
    // "obj.metodo(args)"). El dispatch considera esta clase y todas sus subclases.
    private Simbolo claseReceptor;

    public Llamada(ExpresionZ objetivo, List<ExpresionZ> argumentos, int linea, int columna) {
        super(linea, columna);
        this.objetivo = objetivo;
        this.argumentos = argumentos;
    }

    public ExpresionZ getObjetivo() { return objetivo; }
    public List<ExpresionZ> getArgumentos() { return argumentos; }
    public Simbolo getSimboloMetodo() { return simboloMetodo; }

    // true si es "super(args)".
    public boolean esSuperConstructor() {
        return objetivo instanceof Super;
    }

    public void autorizarSuperConstructor() {
        this.superConstructorAutorizado = true;
    }

    /**
     * Polimorfismo: true si en tiempo de ejecucion hay que elegir la implementacion
     * segun la clase real del objeto. generarC3D lo usa para emitir el if-chain por
     * _class_id (solo si de verdad hay mas de una implementacion posible).
     * @return
     */
    public boolean esDespachoDinamico() {
        return despachoDinamico;
    }

    // Indice del metodo en la tabla virtual (-1 si no aplica).
    public int getIndiceVirtual() {
        return (simboloMetodo == null) ? -1 : simboloMetodo.getIndiceVirtual();
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {

        // ===== Caso 1: super(args) -> constructor de la clase padre. =====
        if (objetivo instanceof Super sup) {
            return verificarSuperConstructor(sup, ambito, errores);
        }

        // ===== Caso 2: metodo de la propia clase -> "metodo(args)". =====
        if (objetivo instanceof Identificador id) {
            AmbitoClase ambClase = ambito.ambitoClaseMasCercano();
            if (ambClase == null) {
                errores.reportar(linea, columna,
                        "No se puede resolver el método '" + id.getNombre() + "' fuera de una clase");
                return TipoPrimitivo.DESCONOCIDO;
            }

            List<Tipo> tiposArgs = verificarArgumentos(ambito, errores);
            Simbolo clase = ambClase.getSimboloContenedor();
            Simbolo m = ResolucionMiembros.resolverMetodo(clase, id.getNombre(), tiposArgs);

            // Comportamiento de la Fase 1: un constructor de la propia clase llamado por nombre.
            if (m == null) {
                Simbolo c = clase.buscarMiembroLocal(id.getNombre() + "#" + argumentos.size());
                if (c != null && c.getCategoria() == CategoriaSimbolo.CONSTRUCTOR) m = c;
            }

            if (m == null) {
                errores.reportar(linea, columna, "Método no declarado: '" + id.getNombre() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloMetodo = m;
            this.claseReceptor = clase;
            Acceso.verificar(m, ambito, errores, linea, columna);
            this.despachoDinamico = esVirtual(m);
            return verificarArgumentosYRetorno(m, tiposArgs, errores);
        }

        // ===== Caso 3: metodo de otro objeto -> "obj.metodo(args)" o "super.metodo(args)". =====
        if (objetivo instanceof AccesoCampo ac) {
            Tipo tObj = ac.getObjeto().verificar(ambito, errores);
            if (!(tObj instanceof TipoClase tc)) {
                if (!tObj.esDesconocido())
                    errores.reportar(linea, columna,
                            "No se puede llamar método sobre " + tObj.nombre());
                return TipoPrimitivo.DESCONOCIDO;
            }

            List<Tipo> tiposArgs = verificarArgumentos(ambito, errores);
            Simbolo m = ResolucionMiembros.resolverMetodo(tc.getDefinicion(), ac.getCampo(), tiposArgs);

            if (m == null) {
                errores.reportar(linea, columna,
                        "La clase '" + tc.nombre() + "' no tiene método '" + ac.getCampo() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloMetodo = m;
            this.claseReceptor = tc.getDefinicion();
            Acceso.verificar(m, ambito, errores, linea, columna);
            // super.metodo() llama siempre a la version del padre: despacho estatico.
            this.despachoDinamico = !(ac.getObjeto() instanceof Super) && esVirtual(m);
            return verificarArgumentosYRetorno(m, tiposArgs, errores);
        }

        errores.reportar(linea, columna, "Llamada inválida");
        return TipoPrimitivo.DESCONOCIDO;
    }

    // super(args): debe estar autorizada (primera sentencia de un constructor), la clase
    // debe heredar y el padre debe tener un constructor accesible para esos argumentos.
    private Tipo verificarSuperConstructor(Super sup, Ambito ambito, ManejadorErrores errores) {
        List<Tipo> tiposArgs = verificarArgumentos(ambito, errores);

        if (!superConstructorAutorizado) {
            errores.reportar(linea, columna,
                    "super(...) solo puede usarse como primera sentencia de un constructor");
            return TipoPrimitivo.VOID;
        }

        Tipo tPadre = sup.verificar(ambito, errores);
        if (!(tPadre instanceof TipoClase tc)) return TipoPrimitivo.VOID; // error ya reportado

        Simbolo padre = tc.getDefinicion();
        Simbolo ctor = ResolucionMiembros.resolverConstructor(padre, tiposArgs);
        if (ctor == null) {
            errores.reportar(linea, columna,
                    "La clase padre '" + padre.getNombre() + "' no tiene un constructor con "
                            + argumentos.size() + " argumentos");
            return TipoPrimitivo.VOID;
        }
        this.simboloMetodo = ctor;
        Acceso.verificar(ctor, ambito, errores, linea, columna);
        verificarArgumentosYRetorno(ctor, tiposArgs, errores);
        return TipoPrimitivo.VOID;
    }

    // Verifica cada argumento UNA sola vez y devuelve sus tipos (se necesitan para
    // elegir la sobrecarga y despues para validar compatibilidad).
    private List<Tipo> verificarArgumentos(Ambito ambito, ManejadorErrores errores) {
        List<Tipo> tipos = new ArrayList<>();
        for (ExpresionZ a : argumentos) tipos.add(a.verificar(ambito, errores));
        return tipos;
    }

    // Un metodo se puede sobrescribir (y por lo tanto se despacha en forma dinamica)
    // si es un METODO no private.
    private static boolean esVirtual(Simbolo m) {
        return m.getCategoria() == CategoriaSimbolo.METODO
                && m.getModificador() != ModificadorAcceso.PRIVATE;
    }

    /** Igual que antes pero recibe los tipos ya verificados (no vuelve a llamar verificar()). */
    private Tipo verificarArgumentosYRetorno(Simbolo m, List<Tipo> tiposArgs,
                                             ManejadorErrores errores) {
        List<Simbolo> params = m.getParametros();
        if (params.size() != tiposArgs.size()) {
            errores.reportar(linea, columna,
                    "Método '" + m.getNombre() + "' espera " + params.size()
                            + " argumentos, recibió " + tiposArgs.size());
            return m.getTipo();
        }
        for (int i = 0; i < tiposArgs.size(); i++) {
            if (!Tipos.esAsignable(params.get(i).getTipo(), tiposArgs.get(i))) {
                errores.reportar(argumentos.get(i).getLinea(), argumentos.get(i).getColumna(),
                        "Argumento " + (i + 1) + " incompatible: se esperaba "
                                + params.get(i).getTipo().nombre() + ", se recibió "
                                + tiposArgs.get(i).nombre());
            }
        }
        return m.getTipo();
    }

    /**
     * Emite:
     *      param receptor            ("this" o el lugar del objeto)
     *      param arg1 ... param argN
     *      call etiqueta, N+1, t     (t = null si el metodo es void)
     * La etiqueta se arma con la clase que declara el metodo o constructor resuelto:
     *      - metodo heredado: la funcion vive en la clase padre (Animal_comer)
     *      - super.metodo(): la version del padre
     *      - super(args): el constructor del padre (Animal_init_...)
     * @param generador
     * @return
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // 1) Receptor.
        String receptor;
        if (objetivo instanceof Identificador || objetivo instanceof Super) {
            receptor = "this";
        } else if (objetivo instanceof AccesoCampo ac) {
            ResultadoC3D r = ac.getObjeto().generarC3D(generador);
            receptor = r.getLugar();
        } else {
            throw new UnsupportedOperationException(
                    "Llamada con objetivo " + objetivo.getClass().getSimpleName()
                            + ": no soportado en C3D");
        }

        // 2) Evaluar todos los argumentos, guardando sus lugares.
        List<String> lugaresArgs = new ArrayList<>();
        for (ExpresionZ a : argumentos) {
            ResultadoC3D v = a.generarC3D(generador);
            lugaresArgs.add(v.getLugar());
        }

        // 3) Etiqueta. Tipos FORMALES del simbolo resuelto (excluye "this").
        List<Tipo> tiposFormales = new ArrayList<>();
        if (simboloMetodo != null) {
            for (Simbolo p : simboloMetodo.getParametros()) {
                tiposFormales.add(p.getTipo() != null ? p.getTipo() : TipoPrimitivo.DESCONOCIDO);
            }
        }

        String etiqueta;
        if (objetivo instanceof Super) {
            String padre = claseDe(simboloMetodo, "?");
            etiqueta = GeneradorC3D.etiquetaConstructor(padre, tiposFormales);
            emitirParams(generador, receptor, lugaresArgs);
            generador.emitirCall(etiqueta, argumentos.size() + 1, null);
            return ResultadoC3D.vacio();
        }

        String metodo = (objetivo instanceof Identificador id)
                ? id.getNombre()
                : ((AccesoCampo) objetivo).getCampo();
        // Sin simbolo resuelto (generacion sin semantica) se usa la clase activa.
        String respaldo = (generador.getClaseActual() != null) ? generador.getClaseActual() : "?";
        String clase = claseDe(simboloMetodo, respaldo);
        etiqueta = GeneradorC3D.etiquetaMetodo(clase, metodo, tiposFormales);

        boolean esVoid = (simboloMetodo != null
                && simboloMetodo.getTipo() != null
                && simboloMetodo.getTipo().esVoid());
        Tipo tipo = (simboloMetodo != null && simboloMetodo.getTipo() != null)
                ? simboloMetodo.getTipo()
                : TipoPrimitivo.DESCONOCIDO;
        String resultado = esVoid ? null : generador.nuevoTemporal();

        // 4) Dispatch dinamico si alguna subclase del tipo declarado sobrescribe el metodo.
        if (despachoDinamico) {
            List<GeneradorC3D.EntradaDispatch> entradas =
                    generador.dispatchPara(claseReceptor, simboloMetodo);
            if (GeneradorC3D.necesitaDispatch(entradas)) {
                emitirDispatch(generador, receptor, lugaresArgs, entradas, etiqueta, resultado);
                return (resultado == null) ? ResultadoC3D.vacio() : ResultadoC3D.temporal(resultado, tipo);
            }
        }

        // 5) Call directo (Fase 1): metodo no virtual o nadie lo sobrescribe.
        emitirParams(generador, receptor, lugaresArgs);
        generador.emitirCall(etiqueta, argumentos.size() + 1, resultado);
        return (resultado == null) ? ResultadoC3D.vacio() : ResultadoC3D.temporal(resultado, tipo);
    }

    // param receptor; param arg1; ... param argN
    // Los params tienen que ir pegados a su call, por eso se repiten en cada caso del dispatch.
    private static void emitirParams(GeneradorC3D generador, String receptor, List<String> lugaresArgs) {
        generador.emitirParam(receptor);
        for (String lugar : lugaresArgs) {
            generador.emitirParam(lugar);
        }
    }

    /**
     * Dispatch dinamico con if-chain (el C3D no tiene punteros a funcion: todo call lleva
     * una etiqueta fija). El receptor y los argumentos ya estan evaluados, una sola vez.
     *      tc = receptor._class_id
     *      tk = tc == idPerro
     *      if_true tk goto Lperro          una comparacion por cada clase que NO usa
     *      tk = tc == idCachorro           la implementacion por defecto
     *      if_true tk goto Lcachorro
     *      param receptor ...              caso por defecto: la version que se resolvio
     *      call Animal_hablar              en la semantica (la del tipo declarado)
     *      goto Lfin
     *      Lperro:
     *        param receptor ...
     *        call Perro_hablar
     *        goto Lfin
     *      Lfin:
     * @param generador
     * @param receptor
     * @param lugaresArgs
     * @param entradas
     * @param etiquetaPorDefecto
     * @param resultado
     */
    private void emitirDispatch(GeneradorC3D generador, String receptor, List<String> lugaresArgs,
                                List<GeneradorC3D.EntradaDispatch> entradas,
                                String etiquetaPorDefecto, String resultado) {
        int nArgs = argumentos.size() + 1;

        String classId = generador.nuevoTemporal();
        generador.emitirCargaCampo(receptor, GeneradorC3D.CAMPO_CLASS_ID, classId);

        // Etiqueta de cada implementacion distinta a la por defecto, en orden de aparicion.
        Map<String, String> casoPorFuncion = new LinkedHashMap<>();
        for (GeneradorC3D.EntradaDispatch e : entradas) {
            if (e.etiqueta().equals(etiquetaPorDefecto)) continue;
            String caso = casoPorFuncion.computeIfAbsent(e.etiqueta(), k -> generador.nuevaEtiqueta());

            String cmp = generador.nuevoTemporal();
            generador.emitirBinaria("==", classId, String.valueOf(e.classId()), cmp);
            generador.emitirIfTrue(cmp, caso);
        }

        String fin = generador.nuevaEtiqueta();

        emitirParams(generador, receptor, lugaresArgs);
        generador.emitirCall(etiquetaPorDefecto, nArgs, resultado);
        generador.emitirGoto(fin);

        for (Map.Entry<String, String> caso : casoPorFuncion.entrySet()) {
            generador.emitirEtiqueta(caso.getValue());
            emitirParams(generador, receptor, lugaresArgs);
            generador.emitirCall(caso.getKey(), nArgs, resultado);
            generador.emitirGoto(fin);
        }

        generador.emitirEtiqueta(fin);
    }

    // Nombre de la clase que declara el miembro, o "respaldo" si no se conoce.
    private static String claseDe(Simbolo miembro, String respaldo) {
        if (miembro != null && miembro.getClaseDuena() != null) {
            return miembro.getClaseDuena().getNombre();
        }
        return respaldo;
    }
}
