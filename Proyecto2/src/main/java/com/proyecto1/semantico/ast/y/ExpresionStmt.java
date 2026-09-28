package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;

/** Una expresión usada como instrucción suelta (#instExpresion), p. ej. "leer();" o "contador++;". */
public final class ExpresionStmt extends NodoY implements InstruccionY {

    private final ExpresionY expresion;

    public ExpresionStmt(ExpresionY expresion, int linea, int columna) {
        super(linea, columna);
        this.expresion = expresion;
    }

    public ExpresionY getExpresion() {
        return expresion;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        return expresion.verificar(ambito, errores);
    }

    /**
     * Emite: lo que emita la expresión (efectos como "contador++" incluidos); este nodo
     * no agrega cuádruplas propias. El valor resultante se descarta.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        expresion.generarC3D(generador);
        return ResultadoC3D.vacio();
    }
}