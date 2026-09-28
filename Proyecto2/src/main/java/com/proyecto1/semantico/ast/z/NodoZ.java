package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.NodoAST;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Base de TODOS los nodos del AST de Zetariano. Mismo rol que {@code NodoY}: guarda
 * línea/columna y deja verificar(Ambito, ManejadorErrores) como placeholder
 * (siempre TipoPrimitivo#DESCONOCIDO) hasta que se escriban las reglas
 * semánticas reales en cada subclase concreta.
 */
public abstract class NodoZ implements NodoAST {

    protected final int linea;
    protected final int columna;

    protected NodoZ(int linea, int columna) {
        this.linea = linea;
        this.columna = columna;
    }

    @Override
    public int getLinea() {
        return linea;
    }

    @Override
    public int getColumna() {
        return columna;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        return TipoPrimitivo.DESCONOCIDO; // pendiente a propósito, ver Javadoc de la clase
    }
}
