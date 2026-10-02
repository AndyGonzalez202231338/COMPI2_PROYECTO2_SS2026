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
import java.util.List;

/**
 * primaryExpression LPAREN argumentList? RPAREN (#primarioLlamada): llamada a
 * método/función. Cubre "metodo(args)" (objetivo = Identificador, llamada
 * dentro de la propia clase) y "obj.metodo(args)" (objetivo = AccesoCampo).
 */
public final class Llamada extends NodoZ implements ExpresionZ {

    private final ExpresionZ objetivo;
    private final List<ExpresionZ> argumentos;

    /**
     * Símbolo del método resuelto por verificar(). Se usa en generarC3D para el tipo
     * de retorno, sin volver a navegar el ámbito. Es null si verificar() no corrió.
     */
    private Simbolo simboloMetodo;

    /**
     * Nombre de la clase cuyo método se está llamando, CUANDO el objetivo es un
     * AccesoCampo (llamada "obj.metodo"). Para el caso Identificador
     * no se cachea aquí: se lee del generador (GeneradorC3D#getClaseActual()),
     * que Clase.generarC3D deja fijado mientras se generan sus métodos.
     */
    private String nombreClaseObjetivo;

    public Llamada(ExpresionZ objetivo, List<ExpresionZ> argumentos, int linea, int columna) {
        super(linea, columna);
        this.objetivo = objetivo;
        this.argumentos = argumentos;
    }

    public ExpresionZ getObjetivo() { return objetivo; }
    public List<ExpresionZ> getArgumentos() { return argumentos; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {

        // ===== Caso 1: llamada a método de la propia clase -> "metodo(args)". =====
        if (objetivo instanceof Identificador id) {
            AmbitoClase ambClase = buscarAmbitoClase(ambito);
            if (ambClase == null) {
                errores.reportar(linea, columna,
                        "No se puede resolver el método '" + id.getNombre() + "' fuera de una clase");
                return TipoPrimitivo.DESCONOCIDO;
            }

            // (1) Verificar argumentos UNA sola vez, guardando sus tipos.
            List<Tipo> tiposArgs = new ArrayList<>();
            for (ExpresionZ a : argumentos) tiposArgs.add(a.verificar(ambito, errores));

            // (2) Clave específica con tipos.
            StringBuilder sb = new StringBuilder(id.getNombre()).append("#").append(argumentos.size());
            for (Tipo t : tiposArgs) sb.append("#").append(t.nombre());
            Simbolo m = ambClase.getSimboloContenedor().buscarMiembro(sb.toString());

            // (3) Fallback genérico.
            if (m == null) {
                m = ambClase.getSimboloContenedor().buscarMiembro(
                        id.getNombre() + "#" + argumentos.size());
            }

            if (m == null || (m.getCategoria() != CategoriaSimbolo.METODO
                    && m.getCategoria() != CategoriaSimbolo.CONSTRUCTOR)) {
                errores.reportar(linea, columna, "Método no declarado: '" + id.getNombre() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloMetodo = m;
            return verificarArgumentosYRetorno(m, tiposArgs, errores);
        }

        // ===== Caso 2: método de otro objeto -> "obj.metodo(args)". =====
        if (objetivo instanceof AccesoCampo ac) {
            Tipo tObj = ac.getObjeto().verificar(ambito, errores);
            if (!(tObj instanceof TipoClase tc)) {
                if (!tObj.esDesconocido())
                    errores.reportar(linea, columna,
                            "No se puede llamar método sobre " + tObj.nombre());
                return TipoPrimitivo.DESCONOCIDO;
            }

            // (1) Verificar argumentos UNA sola vez, guardando sus tipos.
            List<Tipo> tiposArgs = new ArrayList<>();
            for (ExpresionZ a : argumentos) tiposArgs.add(a.verificar(ambito, errores));

            // (2) Clave específica con tipos, sobre el símbolo de la clase objetivo.
            StringBuilder sb = new StringBuilder(ac.getCampo()).append("#").append(argumentos.size());
            for (Tipo t : tiposArgs) sb.append("#").append(t.nombre());
            Simbolo m = tc.getDefinicion().buscarMiembro(sb.toString());

            // (3) Fallback genérico.
            if (m == null) {
                m = tc.getDefinicion().buscarMiembro(ac.getCampo() + "#" + argumentos.size());
            }

            if (m == null || m.getCategoria() != CategoriaSimbolo.METODO) {
                errores.reportar(linea, columna,
                        "La clase '" + tc.nombre() + "' no tiene método '" + ac.getCampo() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloMetodo = m;
            this.nombreClaseObjetivo = tc.getDefinicion().getNombre();
            return verificarArgumentosYRetorno(m, tiposArgs, errores);
        }

        errores.reportar(linea, columna, "Llamada inválida");
        return TipoPrimitivo.DESCONOCIDO;
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

    /** Sube en la cadena de ámbitos hasta el AmbitoClase más cercano. */
    private static AmbitoClase buscarAmbitoClase(Ambito a) {
        while (a != null && !(a instanceof AmbitoClase)) a = a.getPadre();
        return (a instanceof AmbitoClase ac) ? ac : null;
    }

    /**
     * Emite, en este orden:
     *   Determinar el receptor:
     *       Identificador: llamada a método de la propia clase; el receptor
     *             es la palabra literal "this" (parámetro implícito que todo
     *             método Z recibe).
     *         AccesoCampo: se genera el C3D de la expresión del objeto; su
     *             lugar pasa a ser el receptor.
     *   (call, etiquetaMetodo(Clase, metodo), nArgs+1, t): el +1
     *       es el receptor.
     * Devuelve temporal(t, tipoRetornoDelMétodo).
     *
     * La clase con la que se construye la etiqueta se resuelve así:
     *   Identificador: generador.getClaseActual(), que
     *       Clase#generarC3D deja fijado mientras recorre sus métodos. Si por
     *       algún motivo no estuviera fijado (p. ej. generación fuera de una clase),
     *       la etiqueta queda con prefijo "?" - visible al inspeccionar la
     *       tabla y no un crash silencioso.
     *   AccesoCampo: el nombre cacheado por verificar a partir de
     *       TipoClase.getDefinicion().getNombre().
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // 1) Receptor.
        String receptor;
        if (objetivo instanceof Identificador) {
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

        // 3) Bloque de params: receptor + args en orden.
        generador.emitirParam(receptor);
        for (String lugar : lugaresArgs) {
            generador.emitirParam(lugar);
        }

        // 4) Llamada. Nombre del método: en ambos casos lo sabemos por el objetivo.
        String metodo = (objetivo instanceof Identificador id)
                ? id.getNombre()
                : ((AccesoCampo) objetivo).getCampo();

        String clase;
        if (objetivo instanceof Identificador) {
            // Llamada propia: la clase activa la fija Clase.generarC3D.
            clase = generador.getClaseActual();
            if (clase == null) clase = "?";
        } else {
            // Llamada sobre objeto: clase cacheada en verificar.
            clase = (nombreClaseObjetivo != null) ? nombreClaseObjetivo : "?";
        }
        // Tipos FORMALES del símbolo resuelto (excluye "this").
        List<Tipo> tiposFormales = new java.util.ArrayList<>();
        if (simboloMetodo != null) {
            for (com.proyecto1.semantico.tabla.Simbolo p : simboloMetodo.getParametros()) {
                tiposFormales.add(p.getTipo() != null
                        ? p.getTipo()
                        : TipoPrimitivo.DESCONOCIDO);
            }
        }
        String etiqueta = GeneradorC3D.etiquetaMetodo(clase, metodo, tiposFormales);

        boolean esVoid = (simboloMetodo != null
                && simboloMetodo.getTipo() != null
                && simboloMetodo.getTipo().esVoid());

        if (esVoid) {
            generador.emitirCall(etiqueta, argumentos.size() + 1, null);
            return ResultadoC3D.vacio();
        }
        String t = generador.nuevoTemporal();
        generador.emitirCall(etiqueta, argumentos.size() + 1, t);
        Tipo tipo = (simboloMetodo != null && simboloMetodo.getTipo() != null)
                ? simboloMetodo.getTipo()
                : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(t, tipo);
    }
}