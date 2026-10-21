package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;

import java.util.HashMap;
import java.util.Map;

// Sabe en que posicion vive cada campo dentro de un objeto del heap.
//
// POR QUE HACE FALTA
// Las cuadruplas de campo traen el NOMBRE del campo, no su posicion:
//     t0 = this.valor        ->  CuadruplaCampoCarga(objeto="this", campo="valor", ...)
// Pero RISC-V no entiende nombres: para leer un campo necesita un numero de bytes
// desde donde empieza el objeto:
//     lw t0, 4(t1)           ->  "lee 4 bytes mas alla de la direccion que hay en t1"
// Esta clase hace esa traduccion: "valor" -> 4.
//
// DE DONDE SALEN LOS NUMEROS
// LayoutMemoria (en la fase semantica) ya calculo el offset de cada campo y lo guardo
// en su Simbolo. Aqui solo se recorre la tabla de simbolos y se arma un mapa plano
// "Clase.campo" -> offset en bytes, que es lo que el traductor consulta.
//
// COMO QUEDA UN OBJETO EN EL HEAP
// Para una clase de Zetariano:
//     base + 0   -> _class_id   (que clase es en realidad; lo usa el polimorfismo)
//     base + 4   -> primer atributo (heredado si la clase tiene padre)
//     base + 8   -> segundo atributo
//     ...
// Para una estructura de Y? no hay _class_id, asi que los campos empiezan en base + 0.
// Los atributos heredados van primero y en el mismo offset que en la clase padre: por
// eso un Perro se puede usar donde se espera un Animal, sus campos coinciden.
public final class LayoutHeap {

    private static final int BYTES_POR_PALABRA = 4;

    // "Clase.campo" -> offset en bytes desde el inicio del objeto.
    private final Map<String, Integer> offsets = new HashMap<>();

    // "Clase" -> cuantos bytes ocupa un objeto de esa clase.
    private final Map<String, Integer> tamanos = new HashMap<>();

    // Campo oculto que cada objeto de una clase lleva en la posicion 0.
    public static final String CAMPO_CLASS_ID = "_class_id";

    // Recorre el ambito global (y su padre, porque en PigLatin las clases importadas
    // viven en el ambito padre) y registra el layout de cada clase y estructura.
    public void registrar(AmbitoGlobal global) {
        if (global == null) return;
        Ambito padre = global.getPadre();
        if (padre != null) {
            for (Simbolo s : padre.simbolosLocales()) registrarTipo(s);
        }
        for (Simbolo s : global.simbolosLocales()) registrarTipo(s);
    }

    private void registrarTipo(Simbolo s) {
        if (s.getCategoria() == CategoriaSimbolo.CLASE) {
            offsets.put(clave(s.getNombre(), CAMPO_CLASS_ID), 0);
            for (Simbolo atributo : s.getAtributosConHerencia()) {
                if (atributo.getOffset() >= 0) {
                    offsets.put(clave(s.getNombre(), atributo.getNombre()),
                            atributo.getOffset() * BYTES_POR_PALABRA);
                }
            }
            if (s.getTamanoObjeto() > 0) {
                tamanos.put(s.getNombre(), s.getTamanoObjeto() * BYTES_POR_PALABRA);
            }
        } else if (s.getCategoria() == CategoriaSimbolo.ESTRUCTURA) {
            int maxOffset = 0; // <-- NUEVO: Calcular tamaño basado en el campo más grande
            for (Simbolo campo : s.getMiembrosEnOrden()) {
                if (campo.getCategoria() == CategoriaSimbolo.CAMPO && campo.getOffset() >= 0) {
                    offsets.put(clave(s.getNombre(), campo.getNombre()),
                            campo.getOffset() * BYTES_POR_PALABRA);

                    // Calcular cuánto ocupa este campo (con sus arreglos)
                    int size = 4;
                    if (!campo.getTamanosArreglo().isEmpty()) {
                        int totalElems = 1;
                        for (int dim : campo.getTamanosArreglo()) totalElems *= dim;
                        size = totalElems * 4;
                    }
                    int finDelCampo = (campo.getOffset() * BYTES_POR_PALABRA) + size;
                    if (finDelCampo > maxOffset) {
                        maxOffset = finDelCampo;
                    }
                }
            }
            if (maxOffset > 0) {
                tamanos.put(s.getNombre(), maxOffset); //Se guarda el tamaño correcto
            } else if (s.getTamanoObjeto() > 0) {
                tamanos.put(s.getNombre(), s.getTamanoObjeto() * BYTES_POR_PALABRA);
            }
        }
    }

    // Offset en bytes del campo dentro de un objeto de esa clase.
    // Devuelve -1 si no se conoce (clase no registrada o campo inexistente).
    public int offsetDe(String clase, String campo) {
        Integer v = offsets.get(clave(clase, campo));
        return (v != null) ? v : -1;
    }

    // Busca el campo en TODAS las clases registradas. Hace falta porque las cuadruplas
    // de campo no dicen de que clase es el objeto: "t5 = t4.valor" solo trae el nombre
    // del temporal y del campo. Mientras dos clases distintas no tengan un campo con el
    // mismo nombre en posiciones distintas, el resultado es correcto.
    // Devuelve -1 si el campo no existe en ninguna clase, y -2 si es ambiguo (varias
    // clases lo tienen en offsets distintos), para que quien llame pueda avisar.
    public int offsetDeCampoUnico(String campo) {
        int encontrado = -1;
        for (Map.Entry<String, Integer> e : offsets.entrySet()) {
            String k = e.getKey();
            if (!k.endsWith("." + campo)) continue;
            if (encontrado == -1) encontrado = e.getValue();
            else if (encontrado != e.getValue()) return -2;   // ambiguo
        }
        return encontrado;
    }

    // Tamano en bytes de un objeto de esa clase; -1 si no se conoce.
    public int tamanoDe(String clase) {
        Integer v = tamanos.get(clase);
        return (v != null) ? v : -1;
    }

    private static String clave(String clase, String campo) {
        return clase + "." + campo;
    }
}
