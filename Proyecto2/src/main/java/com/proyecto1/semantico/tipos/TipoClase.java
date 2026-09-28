package com.proyecto1.semantico.tipos;

import com.proyecto1.semantico.tabla.Simbolo;

import java.util.Objects;

/**
 * También tipado nominal (mismo nombre de clase = mismo tipo).
 */
public final class TipoClase implements Tipo {

    private final Simbolo definicion;

    public TipoClase(Simbolo definicion) {
        this.definicion = definicion;
    }

    public Simbolo getDefinicion() { return definicion; }

    @Override
    public String nombre() { return definicion.getNombre(); }

    @Override
    public boolean esCompuesto() { return true; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TipoClase otra)) return false;
        return Objects.equals(this.definicion.getNombre(), otra.definicion.getNombre());
    }

    @Override
    public int hashCode() { return Objects.hash(TipoClase.class, definicion.getNombre()); }

    @Override
    public String toString() { return nombre(); }
}