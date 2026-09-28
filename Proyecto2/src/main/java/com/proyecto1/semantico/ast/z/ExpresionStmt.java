package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;

/**
 * Una expresión usada como instrucción suelta (#expressionStatementDef -> #stmtExpresion),
 * p. ej. "x = 5;", "obj.metodo();" o "contador++;" — cualquier ExpresionZ
 * (incluida una Asignacion, que en Z es una expresión) terminada en ';'.
 */
public final class ExpresionStmt extends NodoZ implements InstruccionZ {

    private final ExpresionZ expresion;

    public ExpresionStmt(ExpresionZ expresion, int linea, int columna) {
        super(linea, columna);
        this.expresion = expresion;
    }

    public ExpresionZ getExpresion() {
        return expresion;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        return expresion.verificar(ambito, errores);
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        expresion.generarC3D(generador);
        return ResultadoC3D.vacio();
    }
}
