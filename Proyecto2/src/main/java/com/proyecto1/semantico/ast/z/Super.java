package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Palabra reservada "super": referencia al objeto actual visto como su clase padre.
 * Formas en que aparece en el AST (no hacen falta nodos extra):
 *  super(args);        -> Llamada(Super, args)                  llamada al constructor padre
 *  super.metodo(args); -> Llamada(AccesoCampo(Super, "metodo"), args)
 *  super.campo         -> AccesoCampo(Super, "campo")
 */
public final class Super extends NodoZ implements ExpresionZ {

    public Super(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // Pendiente de la fase semantica de herencia (ver comentario de la clase).
        return TipoPrimitivo.DESCONOCIDO;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        return ResultadoC3D.valor("this", TipoPrimitivo.DESCONOCIDO);
    }
}
