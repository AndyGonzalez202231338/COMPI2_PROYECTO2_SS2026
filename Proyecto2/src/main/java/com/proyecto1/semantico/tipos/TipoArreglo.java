package com.proyecto1.semantico.tipos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tipo arreglo: envuelve un tipo base. Para Y siempre se usa con un solo nivel
 * (los arreglos se "aplanan", según la especificación); para Zetariano puede anidarse
 * (TipoArreglo(TipoArreglo(ENTERO)) para representar "int[][]").
 *
 */
public final class TipoArreglo implements Tipo {

    /** Marca de "longitud no conocida en tiempo de compilación". */
    public static final int LONGITUD_DESCONOCIDA = -1;

    private final Tipo base;
    private final int longitud;

    /** Constructor histórico (longitud desconocida). */
    public TipoArreglo(Tipo base) {
        this(base, LONGITUD_DESCONOCIDA);
    }

    /** Constructor completo: usar cuando la longitud se conoce. */
    public TipoArreglo(Tipo base, int longitud) {
        this.base = base;
        this.longitud = longitud;
    }

    public Tipo getBase() { return base; }

    /** Longitud del nivel MÁS EXTERNO, o {@link #LONGITUD_DESCONOCIDA}. */
    public int getLongitud() { return longitud; }

    /** true si la longitud del nivel más externo se conoce en compile-time. */
    public boolean tieneLongitudConocida() {
        return longitud != LONGITUD_DESCONOCIDA;
    }

    /** Para "int[][][]" devuelve el tipo escalar final (ENTERO), atravesando todos los niveles. */
    public Tipo baseEscalar() {
        Tipo actual = base;
        while (actual instanceof TipoArreglo ta) actual = ta.getBase();
        return actual;
    }

    /** Cuántos pares "[]" tiene este arreglo (1 para "int[]", 2 para "int[][]", etc.). */
    public int dimensiones() {
        int contador = 1;
        Tipo actual = base;
        while (actual instanceof TipoArreglo ta) {
            contador++;
            actual = ta.getBase();
        }
        return contador;
    }

    public List<Integer> tamanosCompletos() {
        List<Integer> out = new ArrayList<>();
        Tipo actual = this;
        while (actual instanceof TipoArreglo ta) {
            out.add(ta.getLongitud());
            actual = ta.getBase();
        }
        return out;
    }

    /**
     * ¿Se puede aplicar el aplanado (flat) a este arreglo?
     *
     * Regla: SÍ si TODAS las dimensiones internas (d2, d3, ..., dn) se conocen
     * en compile-time. La dimensión externa (d1) NO cuenta para esta decisión:
     * solo se usa en el malloc del {@code new}, no en el cálculo del índice
     * aplanado.
     */
    public boolean esAplanable() {
        List<Integer> dims = tamanosCompletos();
        for (int i = 1; i < dims.size(); i++) {
            if (dims.get(i) == LONGITUD_DESCONOCIDA) return false;
        }
        return true;
    }

    /**
     * Nombre del TIPO, no de la instancia: no incluye la longitud.
     */
    @Override
    public String nombre() {
        return base.nombre() + "[]";
    }

    /** Solo para depuración: "int[5]" o "int[]" si la longitud es desconocida. */
    public String toStringConLongitud() {
        return tieneLongitudConocida()
                ? base.nombre() + "[" + longitud + "]"
                : base.nombre() + "[]";
    }

    @Override
    public boolean esArreglo() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TipoArreglo otro)) return false;
        return Objects.equals(this.base, otro.base);
    }

    @Override
    public int hashCode() {
        return Objects.hash(TipoArreglo.class, base);
    }

    @Override
    public String toString() {
        return nombre();
    }
}