package com.proyecto1.ui.modelo;

import com.proyecto1.semantico.ast.cuadruplas.*;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte la salida del compilador (tabla de simbolos y cuadruplas) en filas planas
 * de solo texto, listas para mostrarse en las TableView del panel derecho del IDE.
 *
 * No guarda estado: son utilidades estaticas que recorren las estructuras del backend.
 */
public final class RecolectorVista {

    private RecolectorVista() {}

    // =====================================================================
    // TABLA DE SIMBOLOS
    // =====================================================================

    // Recorre el ambito global y arma una fila por cada simbolo relevante:
    // clases, estructuras, funciones y, dentro de ellas, sus miembros, parametros
    // y variables locales. El "ambito" de cada fila dice donde vive el simbolo.
    public static List<FilaSimbolo> recolectarSimbolos(AmbitoGlobal global) {
        List<FilaSimbolo> filas = new ArrayList<>();
        if (global == null) return filas;

        // En PigLatin las variables del .pig viven en "global", pero las funciones y
        // clases importadas (de los .y/.z) viven en su ambito PADRE. Se recorren ambos
        // para que la tabla muestre todo: primero lo importado (el padre), luego lo
        // propio del archivo. Para Y? y Z no hay padre con simbolos, asi que solo se
        // recorre su propio ambito. El Set evita listar dos veces un mismo simbolo si
        // apareciera en ambos niveles.
        java.util.Set<String> vistos = new java.util.HashSet<>();

        Ambito padre = global.getPadre();
        if (padre != null) {
            for (Simbolo s : padre.simbolosLocales()) {
                if (vistos.add(claveSimbolo(s))) agregarSimbolo(filas, s);
            }
        }
        for (Simbolo s : global.simbolosLocales()) {
            if (vistos.add(claveSimbolo(s))) agregarSimbolo(filas, s);
        }
        return filas;
    }

    private static String claveSimbolo(Simbolo s) {
        return s.getCategoria() + ":" + s.getNombre();
    }

    private static void agregarSimbolo(List<FilaSimbolo> filas, Simbolo s) {
        switch (s.getCategoria()) {
            case CLASE -> agregarClase(filas, s);
            case ESTRUCTURA -> agregarEstructura(filas, s);
            case FUNCION -> agregarFuncion(filas, s, "global");
            case VARIABLE -> filas.add(filaVariableGlobal(s));
            default -> filas.add(filaSimple(s, "global"));
        }
    }

    private static void agregarClase(List<FilaSimbolo> filas, Simbolo clase) {
        String extra = "classId=" + clase.getClassId()
                + ", tamObjeto=" + clase.getTamanoObjeto();
        if (clase.getNombreClasePadre() != null) {
            extra += ", extends " + clase.getNombreClasePadre();
        }
        filas.add(new FilaSimbolo(clase.getNombre(), "CLASE", "-", "global",
                texto(clase.getModificador()), clase.getLinea(), clase.getColumna(), extra));

        // Atributos (incluye heredados; la columna INFO dice de que clase vienen).
        for (Simbolo m : clase.getAtributosConHerencia()) {
            String duena = (m.getClaseDuena() != null) ? m.getClaseDuena().getNombre() : clase.getNombre();
            String info = "offset=" + m.getOffset();
            if (!duena.equals(clase.getNombre())) info += ", heredado de " + duena;
            filas.add(new FilaSimbolo(m.getNombre(), "ATRIBUTO", tipo(m.getTipo()),
                    clase.getNombre(), texto(m.getModificador()), m.getLinea(), m.getColumna(), info));
        }
        // Constructores declarados en esta clase.
        for (Simbolo c : clase.getConstructoresDeclarados()) {
            agregarConstructor(filas, c, clase.getNombre());
        }
        // Metodos declarados en esta clase.
        for (Simbolo m : clase.getMetodosDeclarados()) {
            agregarMetodo(filas, m, clase.getNombre());
        }
    }

    private static void agregarEstructura(List<FilaSimbolo> filas, Simbolo est) {
        filas.add(new FilaSimbolo(est.getNombre(), "ESTRUCTURA", "-", "global",
                "-", est.getLinea(), est.getColumna(),
                "tamObjeto=" + est.getTamanoObjeto()));
        for (Simbolo m : est.getMiembrosEnOrden()) {
            if (m.getCategoria() == CategoriaSimbolo.CAMPO) {
                filas.add(new FilaSimbolo(m.getNombre(), "CAMPO", tipo(m.getTipo()),
                        est.getNombre(), "-", m.getLinea(), m.getColumna(),
                        "offset=" + m.getOffset()));
            }
        }
    }

    private static void agregarFuncion(List<FilaSimbolo> filas, Simbolo fn, String ambito) {
        filas.add(new FilaSimbolo(nombrePlano(fn.getNombre()), "FUNCION",
                tipo(fn.getTipo()), ambito, "-", fn.getLinea(), fn.getColumna(),
                "tamFrame=" + fn.getTamanoFrame()));
        for (Simbolo p : fn.getParametros()) {
            filas.add(new FilaSimbolo(p.getNombre(), "PARAMETRO", tipo(p.getTipo()),
                    nombrePlano(fn.getNombre()), "-", p.getLinea(), p.getColumna(),
                    "offset=" + p.getOffset()));
        }
    }

    private static void agregarMetodo(List<FilaSimbolo> filas, Simbolo m, String clase) {
        filas.add(new FilaSimbolo(nombrePlano(m.getNombre()), "METODO", tipo(m.getTipo()),
                clase, texto(m.getModificador()), m.getLinea(), m.getColumna(),
                "indiceVirtual=" + m.getIndiceVirtual()));
        for (Simbolo p : m.getParametros()) {
            filas.add(new FilaSimbolo(p.getNombre(), "PARAMETRO", tipo(p.getTipo()),
                    clase + "." + nombrePlano(m.getNombre()), "-",
                    p.getLinea(), p.getColumna(), "offset=" + p.getOffset()));
        }
    }

    private static void agregarConstructor(List<FilaSimbolo> filas, Simbolo c, String clase) {
        filas.add(new FilaSimbolo(nombrePlano(c.getNombre()), "CONSTRUCTOR", "-",
                clase, texto(c.getModificador()), c.getLinea(), c.getColumna(), "-"));
        for (Simbolo p : c.getParametros()) {
            filas.add(new FilaSimbolo(p.getNombre(), "PARAMETRO", tipo(p.getTipo()),
                    clase + "." + nombrePlano(c.getNombre()), "-",
                    p.getLinea(), p.getColumna(), "offset=" + p.getOffset()));
        }
    }

    private static FilaSimbolo filaVariableGlobal(Simbolo s) {
        return new FilaSimbolo(s.getNombre(), "VARIABLE", tipo(s.getTipo()), "global",
                "-", s.getLinea(), s.getColumna(),
                s.getOffset() >= 0 ? "offset=" + s.getOffset() : "-");
    }

    private static FilaSimbolo filaSimple(Simbolo s, String ambito) {
        return new FilaSimbolo(s.getNombre(), s.getCategoria().name(), tipo(s.getTipo()),
                ambito, "-", s.getLinea(), s.getColumna(), "-");
    }

    // nombre#2#entero#cadena -> nombre
    private static String nombrePlano(String nombre) {
        int i = nombre.indexOf('#');
        return (i >= 0) ? nombre.substring(0, i) : nombre;
    }

    private static String tipo(Tipo t) {
        return (t == null) ? "-" : t.nombre();
    }

    private static String texto(Object modificador) {
        return (modificador == null) ? "-" : modificador.toString();
    }

    // =====================================================================
    // TABLA DE CUADRUPLAS
    // =====================================================================

    // Descompone cada cuadrupla en sus cuatro campos clasicos para mostrarlos en
    // columnas separadas. El guion "-" marca un campo que esa cuadrupla no usa.
    public static List<FilaCuadrupla> recolectarCuadruplas(List<Cuadrupla> cuadruplas) {
        List<FilaCuadrupla> filas = new ArrayList<>();
        if (cuadruplas == null) return filas;

        int i = 0;
        for (Cuadrupla c : cuadruplas) {
            String[] campos = descomponer(c);
            filas.add(new FilaCuadrupla(i++, campos[0], campos[1], campos[2], campos[3],
                    c.toStringLegible()));
        }
        return filas;
    }

    // Devuelve [operador, operando1, operando2, resultado].
    private static String[] descomponer(Cuadrupla c) {
        return switch (c) {
            case CuadruplaBinaria q -> new String[]{ q.operador(), q.a(), q.b(), q.t() };
            case CuadruplaUnaria q -> new String[]{ q.operador(), q.a(), "-", q.t() };
            case CuadruplaAsignacion q -> new String[]{ "=", q.valor(), "-", q.destino() };
            case CuadruplaEtiqueta q -> new String[]{ "label", "-", "-", q.etiqueta() };
            case CuadruplaGoto q -> new String[]{ "goto", "-", "-", q.etiqueta() };
            case CuadruplaIfTrue q -> new String[]{ "if_true", q.condicion(), "-", q.etiqueta() };
            case CuadruplaIfFalse q -> new String[]{ "if_false", q.condicion(), "-", q.etiqueta() };
            case CuadruplaPrint q -> new String[]{ q.nuevaLinea() ? "println" : "print", q.valor(), "-", "-" };
            case CuadruplaRead q -> new String[]{ "read", "-", "-", q.destino() };
            case CuadruplaCall q -> new String[]{ "call", q.funcion(), String.valueOf(q.nArgs()),
                    q.destino() == null ? "-" : q.destino() };
            case CuadruplaParam q -> new String[]{ "param", q.valor(), "-", "-" };
            case CuadruplaReturn q -> new String[]{ "return", q.valor() == null ? "-" : q.valor(), "-", "-" };
            case CuadruplaBeginFunc q -> new String[]{ "begin_func", q.nombre(), String.valueOf(q.nArgs()), "-" };
            case CuadruplaEndFunc q -> new String[]{ "end_func", "-", "-", "-" };
            case CuadruplaIndiceCarga q -> new String[]{ "=[]", q.arreglo(), q.indice(), q.destino() };
            case CuadruplaIndiceGuarda q -> new String[]{ "[]=", q.arreglo(), q.indice(), q.valor() };
            case CuadruplaCampoCarga q -> new String[]{ "=.", q.objeto(), q.campo(), q.destino() };
            case CuadruplaCampoGuarda q -> new String[]{ ".=", q.objeto(), q.campo(), q.valor() };
            case CuadruplaNew q -> new String[]{ "new", q.clase(), "-", q.destino() };
            case CuadruplaNewArray q -> new String[]{ "newarr", q.tipoElemento(),
                    String.join("x", q.tamanos()), q.destino() };
            case CuadruplaConcat q -> new String[]{ "concat", q.a(), q.b(), q.t() };
            case CuadruplaCompCadena q -> new String[]{ "strcmp" + q.operador(), q.a(), q.b(), q.t() };
            case CuadruplaConversion q -> new String[]{ "conv", q.valor(),
                    q.tipoOrigen() + "->" + q.tipoDestino(), q.destino() };
        };
    }
}