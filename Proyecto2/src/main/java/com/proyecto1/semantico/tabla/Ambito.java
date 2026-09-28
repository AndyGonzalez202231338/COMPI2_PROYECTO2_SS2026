package com.proyecto1.semantico.tabla;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un ámbito (alcance) de la tabla de símbolos. Cada ámbito tiene su propia
 * link TablaHash de símbolos locales y un enlace a su ámbito padre (null solo en
 * el ámbito global), formando una cadena de resolución tipo "lista enlazada de
 * tablas hash": para resolver un nombre se busca primero en el ámbito actual y,
 * si no aparece, se sube al padre, y así sucesivamente hasta el global.
 */
public abstract class Ambito {

    protected final Ambito padre;
    protected final TablaHash<String, Simbolo> simbolos = new TablaHash<>();

    /**
     * Símbolos declarados en ESTE ámbito, en el ORDEN en que se declararon.
     * Paralelo a {@link #simbolos} (que es un hash sin orden garantizado).
     * Se puebla en {@link #declarar} y en métodos de subclases que puedan
     * sobrescribir símbolos (p. ej. {@code AmbitoGlobal.reemplazar}).
     */
    protected final List<Simbolo> simbolosEnOrden = new ArrayList<>();

    protected Ambito(Ambito padre) {
        this.padre = padre;
    }

    public Ambito getPadre() { return padre; }

    /**
     * Declara un símbolo nuevo en este ámbito (no en los padres). Devuelve false si ya
     * existía un símbolo con ese nombre en ESTE MISMO ámbito (redeclaración: eso es un
     * error semántico que debe reportar quien llama, con la línea/columna del nodo).
     */
    public boolean declarar(Simbolo simbolo) {
        if (simbolos.contiene(simbolo.getNombre())) return false;
        simbolos.insertar(simbolo.getNombre(), simbolo);
        simbolosEnOrden.add(simbolo);          // <-- línea nueva: mantener el orden
        return true;
    }

    /** Busca solo en este ámbito (sin subir a los padres). */
    public Simbolo resolverLocal(String nombre) {
        return simbolos.obtener(nombre);
    }

    /** Símbolos declarados directamente en ESTE ámbito, en orden de declaración. */
    public List<Simbolo> simbolosLocales() {
        return Collections.unmodifiableList(simbolosEnOrden);
    }

    /** Busca en este ámbito y, si no está, sube por la cadena de padres hasta el global. */
    public Simbolo resolver(String nombre) {
        Simbolo encontrado = simbolos.obtener(nombre);
        if (encontrado != null) return encontrado;
        if (padre != null) return padre.resolver(nombre);
        return null;
    }

    /** Sube en la cadena de ámbitos hasta encontrar el AmbitoFuncion más cercano (o null si no hay). */
    public AmbitoFuncion ambitoFuncionMasCercano() {
        Ambito actual = this;
        while (actual != null) {
            if (actual instanceof AmbitoFuncion af) return af;
            actual = actual.padre;
        }
        return null;
    }

    /** Sube en la cadena hasta el AmbitoGlobal (siempre existe, es la raíz). */
    public AmbitoGlobal ambitoGlobal() {
        Ambito actual = this;
        while (actual.padre != null) actual = actual.padre;
        return (AmbitoGlobal) actual;
    }

    /** Descripción usada en mensajes de error/depuración ("ámbito global", "función 'foo'", etc.). */
    public abstract String descripcion();
}