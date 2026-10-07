package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.List;

/** IMPRIMIR(expresion (, expresion)*) (#instImprimir). */
public final class Imprimir extends NodoY implements InstruccionY {

    private final List<ExpresionY> argumentos;

    public Imprimir(List<ExpresionY> argumentos, int linea, int columna) {
        super(linea, columna);
        this.argumentos = argumentos;
    }

    public List<ExpresionY> getArgumentos() { return argumentos; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        for (ExpresionY a : argumentos) a.verificar(ambito, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, en orden de aparición: para cada argumento, primero su C3D (que puede
     * generar cuádruplas propias: "t0 = a + b", "t1 = leer()"→"read t1", …) y luego
     * (print, v, null, null).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        for (int i = 0; i < argumentos.size(); i++) {
            ResultadoC3D v = argumentos.get(i).generarC3D(generador);
            generador.emitirPrint(v.getLugar(), i == argumentos.size() - 1);
        }
            return ResultadoC3D.vacio();
    }
}
