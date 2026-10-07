package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.List;

/** {@code >> expresion (>> expresion)* ;} (#sentenciaImprimirDef). Uno o más valores impresos en secuencia. */
public final class Imprimir extends NodoPigLatin implements InstruccionPigLatin {

    private final List<ExpresionPigLatin> argumentos;

    public Imprimir(List<ExpresionPigLatin> argumentos, int linea, int columna) {
        super(linea, columna);
        this.argumentos = argumentos;
    }

    public List<ExpresionPigLatin> getArgumentos() {
        return argumentos;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        for (ExpresionPigLatin a : argumentos) a.verificar(ambito, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, en orden de aparición: por cada argumento, primero su C3D (que puede
     * generar cuádruplas propias: "t0 = a + b", "t1 = leer()", etc.) y luego UNA
     * cuádrupla (print, v, null, null) con el lugar resultante. Como la
     * cuádrupla "print" solo tiene arg1, cada argumento produce su propia cuádrupla
     * — no hay un "print variádico". Fase 4 decide el formato (printf con "%d %f %s…"
     * según el tipo del lugar).
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