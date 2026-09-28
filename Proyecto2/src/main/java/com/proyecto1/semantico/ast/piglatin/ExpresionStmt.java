package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;

/**
 * expresion ; (#expresionSentenciaDef): una expresión usada como instrucción
 * suelta. Cubre asignaciones x = 5;), llamadas (metodo();) y llamadas
 * encadenadas (obj.metodo();), que en la gramática de PigLatin son todas la
 * misma regla expresion
 */
public final class ExpresionStmt extends NodoPigLatin implements InstruccionPigLatin {

    private final ExpresionPigLatin expresion;

    public ExpresionStmt(ExpresionPigLatin expresion, int linea, int columna) {
        super(linea, columna);
        this.expresion = expresion;
    }

    public ExpresionPigLatin getExpresion() {
        return expresion;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        return expresion.verificar(ambito, errores);
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        expresion.generarC3D(generador);   // efectos secundarios sí, valor no
        return ResultadoC3D.vacio();
    }
}
