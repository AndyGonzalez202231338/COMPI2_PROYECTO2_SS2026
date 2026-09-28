package com.proyecto1.semantico.tabla;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ámbito raíz (sin padre). Aquí viven, según el lenguaje que se esté analizando:
 *   - Y?: las ESTRUCTURA definidas en %estructuras y las FUNCION definidas en %funciones.
 *   - Zetariano: la (o las, si se linkean varios archivos) CLASE.
 */
public class AmbitoGlobal extends Ambito {

    public AmbitoGlobal() {
        super(null);
    }

    public AmbitoGlobal(Ambito padre) {
        super(padre);
    }

    /**
     * Guarda {@code simbolo} sobreescribiendo, si lo hay, otro con el mismo nombre
     * (declarar() lo rechazaría). También hay que mantener coherente {@code simbolosEnOrden}:
     * si ya existía uno con ese nombre, se reemplaza en la misma posición; si no,
     * se añade al final.
     */
    public void reemplazar(Simbolo simbolo) {
        String nombre = simbolo.getNombre();
        if (simbolos.contiene(nombre)) {
            // Reemplazo: mantener la POSICIÓN original en simbolosEnOrden.
            for (int i = 0; i < simbolosEnOrden.size(); i++) {
                if (simbolosEnOrden.get(i).getNombre().equals(nombre)) {
                    simbolosEnOrden.set(i, simbolo);
                    break;
                }
            }
        } else {
            simbolosEnOrden.add(simbolo);
        }
        simbolos.insertar(nombre, simbolo);
    }

    /**
     * Tipos definidos por el usuario (estructuras de Y y clases de Z), en orden de
     * declaración, SUBIENDO POR LA CADENA DE PADRES. En PigLatin las estructuras y
     * clases vienen del ámbito de imports (el padre), no del propio .pig; sin subir
     * por la cadena, `getTiposDefinidos()` devolvería una lista vacía y el archivo .c
     * del .pig no tendría los typedef struct que sus prototipos necesitan.
     *
     * La deduplicación por nombre se hace con un LinkedHashMap: si el mismo tipo
     * aparece en el hijo y en el padre (por ejemplo, si el .pig redeclarara algo),
     * gana la versión del hijo (se inserta primero y el putIfAbsent la conserva).
     */
    public List<Simbolo> getTiposDefinidos() {
        java.util.LinkedHashMap<String, Simbolo> porNombre = new java.util.LinkedHashMap<>();
        Ambito actual = this;
        while (actual != null) {
            for (Simbolo s : actual.simbolosLocales()) {
                CategoriaSimbolo c = s.getCategoria();
                if (c == CategoriaSimbolo.ESTRUCTURA || c == CategoriaSimbolo.CLASE) {
                    porNombre.putIfAbsent(s.getNombre(), s);
                }
            }
            actual = actual.getPadre();
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(porNombre.values()));
    }

    @Override
    public String descripcion() {
        return "ámbito global";
    }
}