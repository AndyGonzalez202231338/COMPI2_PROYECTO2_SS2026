package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * series ID [ tamaño ] : tipo (= { expr, ... })? ;} (#declaracionArregloDef).
 * tamano ya viene parseado a int (no como texto crudo) para que los
 * nodos de más arriba no tengan que volver a parsear el literal entero.
 */
public final class DeclaracionArreglo extends NodoPigLatin implements InstruccionPigLatin {

    private final String nombre;
    private final int tamano;
    private final NodoTipoRef tipo;
    private final InicializadorArreglo inicializador; // null si no hay "= { ... }"

    /**
     * Tipo base del arreglo, cacheado por verificar() (donde hay ámbito para
     * resolver tipos de clase). Sin este cache, generarC3D() tendría que volver
     * a resolver el NodoTipoRef con ámbito null y fallaría para clases.
     */
    private Tipo tipoBaseCache;

    public DeclaracionArreglo(String nombre, int tamano, NodoTipoRef tipo, InicializadorArreglo inicializador,
                              int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
        this.tamano = tamano;
        this.tipo = tipo;
        this.inicializador = inicializador;
    }

    public String getNombre() { return nombre; }
    public int getTamano() { return tamano; }
    public NodoTipoRef getTipo() { return tipo; }
    public InicializadorArreglo getInicializador() { return inicializador; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo base = tipo.resolver(ambito, errores);
        this.tipoBaseCache = base;

        Tipo tArr = new TipoArreglo(base);

        Simbolo s = new Simbolo(nombre, CategoriaSimbolo.VARIABLE, tArr, linea, columna);
        s.getTamanosArreglo().add(tamano);
        if (!ambito.declarar(s)) {
            errores.reportar(linea, columna, "Arreglo ya declarado: '" + nombre + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        if (inicializador != null)
            inicializador.verificar(ambito, errores);
        s.marcarInicializado();
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite:
     *   Con inicializador: el C3D del inicializador (que ya reserva
     *       el bloque con newarr y llena los elementos) y luego la
     *       asignación nombre = t.
     *   Sin inicializador: reserva el bloque AQUÍ con un newarr
     *       y lo asigna a la variable. Sin esta rama, series arr[N] : T;
     *       dejaría arr como puntero basura y cualquier acceso
     *       arr[i] explotaría con segfault en runtime.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        if (inicializador != null) {
            ResultadoC3D v = inicializador.generarC3D(generador);
            generador.emitirAsignacion(v.getLugar(), nombre);
            return ResultadoC3D.vacio();
        }

        // Sin inicializador: reservar el bloque.
        String descriptor = descriptorElemento();
        java.util.List<String> tamanos = java.util.List.of(String.valueOf(tamano));
        String t = generador.nuevoTemporal();
        generador.emitirNewArray(descriptor, tamanos, t);
        generador.emitirAsignacion(t, nombre);
        return ResultadoC3D.vacio();
    }

    /**
     * Descriptor C del tipo de los ELEMENTOS del arreglo (no del arreglo).
     * Ejemplos:
     * numerus  -> "int"
     * Persona  -> "Persona*"
     * textum}   -> "char*"
     * Se usa como argumento del newarr, que en C emite
     * (T*) malloc(N * sizeof(T)). Para que el destino sea T*,
     * T debe ser el tipo del elemento, no el del arreglo.
     */
    private String descriptorElemento() {
        return tipoAC(tipoBaseCache);
    }

    /** Mismo mapeo que el resto del proyecto. */
    private static String tipoAC(Tipo t) {
        if (t == null) return "int";
        if (t == TipoPrimitivo.ENTERO)   return "int";
        if (t == TipoPrimitivo.FLOTANTE) return "double";
        if (t == TipoPrimitivo.CARACTER) return "char";
        if (t == TipoPrimitivo.CADENA)   return "char*";
        if (t == TipoPrimitivo.BOOL)     return "int";
        if (t instanceof com.proyecto1.semantico.tipos.TipoClase tc)
            return tc.getDefinicion().getNombre() + "*";
        if (t instanceof com.proyecto1.semantico.tipos.TipoEstructura te)
            return te.getDefinicion().getNombre() + "*";
        if (t instanceof TipoArreglo ta)
            return tipoAC(ta.getBase()) + "*";
        return "int";
    }
}