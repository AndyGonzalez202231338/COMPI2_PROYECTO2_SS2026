package com.proyecto1.semantico.ast;

import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Lo que devuelve generarC3D(...): DÓNDE quedó el valor del nodo (una variable, un
 * temporal como "t3", o null si el nodo no produce valor, p. ej. "romper"), de qué
 * TIPO semántico es ese valor (lo necesitará la Fase 4 para declarar variables en C)
 * y si ese lugar es un temporal.
 *
 * Esta clase NO guarda cuádruplas. Las cuádruplas viven únicamente en la
 * TablaCuadruplas del GeneradorC3D: si cada resultado llevara su propia lista habría
 * que concatenarlas al subir por el árbol, los índices cambiarían y no se podría
 * hacer backpatching global. Aquí solo se describe el valor producido.
 */
public final class ResultadoC3D {

    private final String lugar;
    private final Tipo tipo;
    private final boolean esTemporal;

    private ResultadoC3D(String lugar, Tipo tipo, boolean esTemporal) {
        this.lugar = lugar;
        this.tipo = tipo;
        this.esTemporal = esTemporal;
    }

    /** Resultado guardado en un temporal (t0, t1, ...). */
    public static ResultadoC3D temporal(String nombre, Tipo tipo) {
        return new ResultadoC3D(nombre, tipo, true);
    }

    /** Resultado que es una variable o constante ya existente (no un temporal). */
    public static ResultadoC3D valor(String nombre, Tipo tipo) {
        return new ResultadoC3D(nombre, tipo, false);
    }

    /** Nodo que no produce valor. */
    public static ResultadoC3D vacio() {
        return new ResultadoC3D(null, TipoPrimitivo.DESCONOCIDO, false);
    }

    public String getLugar() { return lugar; }
    public Tipo getTipo() { return tipo; }
    public boolean esTemporal() { return esTemporal; }
    public boolean esVacio() { return lugar == null; }

    @Override
    public String toString() {
        if (esVacio()) {
            return "ResultadoC3D[vacio]";
        }
        return "ResultadoC3D[lugar=" + lugar + ", tipo=" + tipo + ", temporal=" + esTemporal + "]";
    }
}