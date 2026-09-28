package com.proyecto1.semantico.tipos;

import com.proyecto1.semantico.tabla.Simbolo;

import java.util.Objects;

/**
 * Se usa tipado NOMINAL, no estructural: dos estructuras son el mismo tipo si y solo
 * si son la misma definición (mismo nombre declarado una sola vez en %estructuras;
 * el parser/analizador ya garantiza que no hay dos estructuras con el mismo nombre).
 */
public final class TipoEstructura implements Tipo {

    private final Simbolo definicion;

    public TipoEstructura(Simbolo definicion) {
        this.definicion = definicion;
    }

    public Simbolo getDefinicion() {
        return definicion;
    }

    @Override
    public String nombre() {
        return definicion.getNombre();
    }

    @Override
    public boolean esCompuesto() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TipoEstructura otra)) return false;
        return Objects.equals(this.definicion.getNombre(), otra.definicion.getNombre());
    }

    @Override
    public int hashCode() {
        return Objects.hash(TipoEstructura.class, definicion.getNombre());
    }

    @Override
    public String toString() {
        return nombre();
    }
}