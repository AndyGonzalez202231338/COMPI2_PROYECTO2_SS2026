package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;

import java.util.List;

/** Nodo raíz del AST de Y?: corresponde a {@code programa} (#programaDef). */
public final class Programa extends NodoY {

    private final List<Estructura> estructuras;
    private final List<Funcion> funciones;

    public Programa(List<Estructura> estructuras, List<Funcion> funciones, int linea, int columna) {
        super(linea, columna);
        this.estructuras = estructuras;
        this.funciones = funciones;
    }

    public List<Estructura> getEstructuras() {
        return estructuras;
    }

    public List<Funcion> getFunciones() {
        return funciones;
    }

    /**
     * Punto de entrada de la generación de C3D (es la raíz del AST). Recorre las
     * funciones en el orden del código fuente; cada una emite su begin_func / cuerpo /
     * end_func en la tabla del generador.
     * Las estructuras no emiten cuádruplas: son definiciones de tipo, no código
     * ejecutable.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        for (Funcion f : funciones) f.generarC3D(generador);
        return ResultadoC3D.vacio();
    }
}