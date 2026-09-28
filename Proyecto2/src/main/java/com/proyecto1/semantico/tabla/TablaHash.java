package com.proyecto1.semantico.tabla;

import java.util.ArrayList;
import java.util.List;

public class TablaHash<K, V> {

    /** Nodo de la lista encadenada de cada cubeta. */
    private static final class Nodo<K, V> {
        final K clave;
        V valor;
        Nodo<K, V> siguiente;

        Nodo(K clave, V valor, Nodo<K, V> siguiente) {
            this.clave = clave;
            this.valor = valor;
            this.siguiente = siguiente;
        }
    }

    /** Par clave/valor expuesto hacia afuera (para recorridos), sin exponer los Nodo internos. */
    public static final class Entrada<K, V> {
        private final K clave;
        private final V valor;

        private Entrada(K clave, V valor) {
            this.clave = clave;
            this.valor = valor;
        }

        public K getClave() { return clave; }
        public V getValor() { return valor; }
    }

    private static final int CAPACIDAD_INICIAL = 16;
    private static final double FACTOR_CARGA_MAXIMO = 0.75;

    private Nodo<K, V>[] cubetas;
    private int capacidad;
    private int tamanio;

    @SuppressWarnings("unchecked")
    public TablaHash() {
        this.capacidad = CAPACIDAD_INICIAL;
        this.cubetas = (Nodo<K, V>[]) new Nodo[capacidad];
        this.tamanio = 0;
    }

    /** Mezcla adicional de bits (xorshift-multiply) para dispersar mejor antes del módulo. */
    private int mezclarHash(int h) {
        h ^= (h >>> 16);
        h *= 0x45d9f3b;
        h ^= (h >>> 16);
        h *= 0x45d9f3b;
        h ^= (h >>> 16);
        return h;
    }

    private int indiceCubeta(K clave, int capacidadUsada) {
        int h = (clave == null) ? 0 : mezclarHash(clave.hashCode());
        return (h & 0x7fffffff) % capacidadUsada;
    }

    /**
     * Inserta o actualiza. Devuelve true si la clave era nueva, false si ya existía
     * (y en ese caso se sobreescribe su valor).
     */
    public boolean insertar(K clave, V valor) {
        int idx = indiceCubeta(clave, capacidad);
        Nodo<K, V> actual = cubetas[idx];
        while (actual != null) {
            if (clavesIguales(actual.clave, clave)) {
                actual.valor = valor; // ya existía: se actualiza, no se cuenta como colisión nueva
                return false;
            }
            actual = actual.siguiente;
        }
        // Clave nueva: se encadena al frente de la cubeta (inserción O(1)).
        cubetas[idx] = new Nodo<>(clave, valor, cubetas[idx]);
        tamanio++;
        if (tamanio > capacidad * FACTOR_CARGA_MAXIMO) {
            redimensionar();
        }
        return true;
    }

    public V obtener(K clave) {
        int idx = indiceCubeta(clave, capacidad);
        Nodo<K, V> actual = cubetas[idx];
        while (actual != null) {
            if (clavesIguales(actual.clave, clave)) return actual.valor;
            actual = actual.siguiente;
        }
        return null;
    }

    public boolean contiene(K clave) {
        return obtener(clave) != null;
    }

    public boolean eliminar(K clave) {
        int idx = indiceCubeta(clave, capacidad);
        Nodo<K, V> actual = cubetas[idx];
        Nodo<K, V> anterior = null;
        while (actual != null) {
            if (clavesIguales(actual.clave, clave)) {
                if (anterior == null) cubetas[idx] = actual.siguiente;
                else anterior.siguiente = actual.siguiente;
                tamanio--;
                return true;
            }
            anterior = actual;
            actual = actual.siguiente;
        }
        return false;
    }

    private boolean clavesIguales(K a, K b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    @SuppressWarnings("unchecked")
    private void redimensionar() {
        int capacidadNueva = capacidad * 2;
        Nodo<K, V>[] cubetasNuevas = (Nodo<K, V>[]) new Nodo[capacidadNueva];
        for (Nodo<K, V> cabeza : cubetas) {
            Nodo<K, V> actual = cabeza;
            while (actual != null) {
                Nodo<K, V> siguiente = actual.siguiente; // guardamos antes de reencadenar
                int idx = indiceCubeta(actual.clave, capacidadNueva);
                actual.siguiente = cubetasNuevas[idx];
                cubetasNuevas[idx] = actual;
                actual = siguiente;
            }
        }
        this.cubetas = cubetasNuevas;
        this.capacidad = capacidadNueva;
    }

    public int tamanio() { return tamanio; }

    public boolean estaVacia() { return tamanio == 0; }

    public List<K> claves() {
        List<K> resultado = new ArrayList<>(tamanio);
        for (Nodo<K, V> cabeza : cubetas) {
            for (Nodo<K, V> actual = cabeza; actual != null; actual = actual.siguiente) {
                resultado.add(actual.clave);
            }
        }
        return resultado;
    }

    public List<V> valores() {
        List<V> resultado = new ArrayList<>(tamanio);
        for (Nodo<K, V> cabeza : cubetas) {
            for (Nodo<K, V> actual = cabeza; actual != null; actual = actual.siguiente) {
                resultado.add(actual.valor);
            }
        }
        return resultado;
    }

    public List<Entrada<K, V>> entradas() {
        List<Entrada<K, V>> resultado = new ArrayList<>(tamanio);
        for (Nodo<K, V> cabeza : cubetas) {
            for (Nodo<K, V> actual = cabeza; actual != null; actual = actual.siguiente) {
                resultado.add(new Entrada<>(actual.clave, actual.valor));
            }
        }
        return resultado;
    }

    /** Utilidad de diagnóstico: qué tan larga es un bucket más ocupada (para medir colisiones). */
    public int longitudCadenaMasLarga() {
        int max = 0;
        for (Nodo<K, V> cabeza : cubetas) {
            int len = 0;
            for (Nodo<K, V> actual = cabeza; actual != null; actual = actual.siguiente) len++;
            if (len > max) max = len;
        }
        return max;
    }
}