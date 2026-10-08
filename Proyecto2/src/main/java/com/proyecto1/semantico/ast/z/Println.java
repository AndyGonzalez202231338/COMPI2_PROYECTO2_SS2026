package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** PRINTLN LPAREN expression RPAREN (#primarioPrintln): "println(expr)". */
public final class Println extends NodoZ implements ExpresionZ {

    private final ExpresionZ argumento;

    public Println(ExpresionZ argumento, int linea, int columna) {
        super(linea, columna);
        this.argumento = argumento;
    }

    public ExpresionZ getArgumento() { return argumento; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        argumento.verificar(ambito, errores);
        return TipoPrimitivo.VOID;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        ResultadoC3D v = argumento.generarC3D(generador);
        generador.emitirPrint(v.getLugar(), true);
        return ResultadoC3D.vacio();
    }
}