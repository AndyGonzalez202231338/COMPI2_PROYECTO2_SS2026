package com.proyecto1.semantico.tabla;

import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula y asigna offsets, nombres unicos y tamanos a los simbolos.
 * Se ejecuta DESPUES del analisis semantico y ANTES de la generacion de C3D.
 */
public final class LayoutMemoria {

    private int contadorSufijos;
    private Map<String, Integer> nombresUsados;

    /**
     * Asigna offset a cada campo de una clase de Z y calcula tamanoObjeto.
     * Layout: [_class_id, campos_padre..., campos_propios...]
     * @param clase
     */
    public void asignarClase(Simbolo clase) {
        if (clase.getCategoria() != CategoriaSimbolo.CLASE) return;
        if (clase.getTamanoObjeto() >= 0) return;

        Simbolo padre = clase.getClasePadre();
        if (padre != null && padre.getTamanoObjeto() < 0) {
            asignarClase(padre);
        }

        int offset = 1; // posicion 0 es _class_id
        if (padre != null) {
            offset = padre.getTamanoObjeto();
        }

        for (Simbolo m : clase.getMiembrosEnOrden()) {
            if (m.getCategoria() != CategoriaSimbolo.ATRIBUTO) continue;
            m.setOffset(offset);
            int tam = tamanoPalabras(m.getTipo());
            m.setTamanoPalabras(tam);
            offset += tam;
        }

        clase.setTamanoObjeto(offset);
    }

    /**
     * Asigna offset a cada campo de una estructura de Y.
     * Sin _class_id ni herencia: empieza en offset 0.
     * @param estructura
     */
    public void asignarEstructura(Simbolo estructura) {
        if (estructura.getCategoria() != CategoriaSimbolo.ESTRUCTURA) return;
        if (estructura.getTamanoObjeto() >= 0) return;

        int offset = 0;
        for (Simbolo m : estructura.getMiembrosEnOrden()) {
            if (m.getCategoria() != CategoriaSimbolo.CAMPO) continue;
            m.setOffset(offset);
            int tam = tamanoPalabras(m.getTipo());
            m.setTamanoPalabras(tam);
            offset += tam;
        }

        estructura.setTamanoObjeto(offset);
    }

    // Asigna offset y nombreUnico a parametros y variables locales.
    public void asignarFuncion(Simbolo funcion, Ambito ambitoFuncion) {
        if (funcion.getCategoria() != CategoriaSimbolo.FUNCION
                && funcion.getCategoria() != CategoriaSimbolo.METODO
                && funcion.getCategoria() != CategoriaSimbolo.CONSTRUCTOR) return;

        contadorSufijos = 0;
        nombresUsados = new HashMap<>();
        int offset = 0;

        for (Simbolo p : funcion.getParametros()) {
            p.setOffset(offset);
            p.setTamanoPalabras(tamanoPalabras(p.getTipo()));
            registrarNombre(p.getNombre());
            offset += p.getTamanoPalabras();
        }

        offset = asignarLocales(ambitoFuncion, offset);
        funcion.setTamanoFrame(offset);
    }

    private int asignarLocales(Ambito ambito, int offset) {
        for (Simbolo s : ambito.simbolosLocales()) {
            if (s.getCategoria() != CategoriaSimbolo.VARIABLE) continue;
            s.setNombreUnico(nombreUnicoPara(s.getNombre()));
            s.setTamanoPalabras(tamanoPalabras(s.getTipo()));
            s.setOffset(offset);
            offset += s.getTamanoPalabras();
        }
        for (Ambito hijo : ambito.getHijos()) {
            offset = asignarLocales(hijo, offset);
        }
        return offset;
    }

    private String nombreUnicoPara(String nombre) {
        Integer cuenta = nombresUsados.get(nombre);
        if (cuenta == null) {
            nombresUsados.put(nombre, 0);
            return nombre;
        }
        int sufijo = cuenta + 1;
        nombresUsados.put(nombre, sufijo);
        return nombre + "_" + sufijo;
    }

    private void registrarNombre(String nombre) {
        nombresUsados.putIfAbsent(nombre, 0);
    }

    public static int tamanoPalabras(Tipo tipo) {
        if (tipo == null) return 1;
        if (tipo == TipoPrimitivo.FLOTANTE) return 2;
        return 1;
    }
}