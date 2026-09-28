package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.NodoAST;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Base de TODOS los nodos del AST de PigLatin. Guarda línea/columna (para poder
 * reportar errores exactamente donde ocurren) y provee una implementación de
 * #verificar(Ambito, ManejadorErrores).
 */
public abstract class NodoPigLatin implements NodoAST {

    protected final int linea;
    protected final int columna;

    protected NodoPigLatin(int linea, int columna) {
        this.linea = linea;
        this.columna = columna;
    }

    @Override
    public int getLinea() { return linea; }

    @Override
    public int getColumna() { return columna; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        return TipoPrimitivo.DESCONOCIDO; // pendiente a propósito, ver Javadoc de la clase
    }
}
