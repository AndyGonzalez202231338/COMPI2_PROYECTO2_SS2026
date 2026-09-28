package com.proyecto1.semantico.tabla;

import com.proyecto1.semantico.tipos.Tipo;

import java.util.ArrayList;
import java.util.List;

public class Simbolo {

    private final String nombre;
    private final CategoriaSimbolo categoria;
    private Tipo tipo;                 // tipo de la variable/campo/atributo, o tipo de RETORNO si es función/método
    private final int linea;
    private final int columna;

    // Solo para FUNCION / METODO / CONSTRUCTOR: sus parámetros, en orden.
    private final List<Simbolo> parametros = new ArrayList<>();
    private final List<Simbolo> miembrosEnOrden = new ArrayList<>();

    /**
     * Solo para ESTRUCTURA / CLASE: sus miembros (campos/atributos/métodos/constructores),
     * indexados por nombre en su propia tabla hash para resolver accesos "objeto.miembro"
     * en O(1) amortizado en vez de recorrer una lista.
     */
    private final TablaHash<String, Simbolo> miembros = new TablaHash<>();

    /**
     * Reservado para la Fase 3 (generación de C3D): posición/offset donde vivirá este símbolo
     */
    private int offset = -1;

    /**
     * Cuántos elementos tiene cada dimensión, si el símbolo es un arreglo declarado con
     * tamaño fijo (Y?: "entero notas[5]"; Z: se calcula en tiempo de ejecución con "new").
     */
    private final List<Integer> tamanosArreglo = new ArrayList<>();

    private boolean inicializado = false; // ¿ya se le asignó un valor al menos una vez?

    public Simbolo(String nombre, CategoriaSimbolo categoria, Tipo tipo, int linea, int columna) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.tipo = tipo;
        this.linea = linea;
        this.columna = columna;
    }

    public String getNombre() {
        return nombre;
    }

    public CategoriaSimbolo getCategoria() {
        return categoria;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }

    public List<Simbolo> getParametros() {
        return parametros;
    }

    public void agregarParametro(Simbolo parametro) {
        this.parametros.add(parametro);
    }

    public TablaHash<String, Simbolo> getMiembros() {
        return miembros;
    }

    public boolean agregarMiembro(Simbolo m) {
        boolean nuevo = miembros.insertar(m.getNombre(), m);
        if (nuevo) miembrosEnOrden.add(m);
        return nuevo;
    }
    // util para la sobrecarga de datos en constructores
    public boolean agregarMiembroConClave(String clave, Simbolo miembro) {
        return miembros.insertar(clave, miembro);
    }

    /**
     * Miembros en el ORDEN en que se declararon (a diferencia de {@code getMiembros()},
     * que devuelve la TablaHash sin orden garantizado). Se usa para generar el C3D de
     * un inicializador de estructura/clase emparejando posicionalmente los valores con
     * los campos, y (Fase 4) para emitir el {@code struct} de C en el orden correcto.
     */
    public List<Simbolo> getMiembrosEnOrden() {
        return miembrosEnOrden;
    }

    public Simbolo buscarMiembro(String nombre) {
        return miembros.obtener(nombre);
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public List<Integer> getTamanosArreglo() {
        return tamanosArreglo;
    }

    public boolean esArregloDeTamanoFijo() {
        return !tamanosArreglo.isEmpty();
    }

    public boolean isInicializado() {
        return inicializado;
    }

    public void marcarInicializado() {
        this.inicializado = true;
    }

    /** Descripción corta usada en mensajes de error: "función 'suma'", "estructura 'Persona'". */
    public String descripcionCorta() {
        String categoriaTexto = switch (categoria) {
            case VARIABLE -> "variable";
            case PARAMETRO -> "parámetro";
            case FUNCION -> "función";
            case ESTRUCTURA -> "estructura";
            case CAMPO -> "campo";
            case CLASE -> "clase";
            case ATRIBUTO -> "atributo";
            case METODO -> "método";
            case CONSTRUCTOR -> "constructor";
        };
        return categoriaTexto + " '" + nombre + "'";
    }

    @Override
    public String toString() {
        return descripcionCorta() + " : " + (tipo == null ? "?" : tipo.nombre());
    }
}