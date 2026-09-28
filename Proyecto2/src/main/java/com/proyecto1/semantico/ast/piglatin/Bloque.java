package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.List;

/**
 * Un bloque (#bloqueDef): sentencia* . A diferencia de
 * Bloque en Y, aquí SÍ implementa InstruccionPigLatin: la gramática
 * de PigLatin permite un bloque suelto directamente como sentencia
 * (alternativa #stmtBloque).
 */
public final class Bloque extends NodoPigLatin implements InstruccionPigLatin {

    private final List<InstruccionPigLatin> instrucciones;

    public Bloque(List<InstruccionPigLatin> instrucciones, int linea, int columna) {
        super(linea, columna);
        this.instrucciones = instrucciones;
    }

    public List<InstruccionPigLatin> getInstrucciones() {
        return instrucciones;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoBloque amb = new AmbitoBloque(ambito, false);
        for (InstruccionPigLatin i : instrucciones) i.verificar(amb, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite: nada propio; recorre las instrucciones en orden del código fuente y
     * cada una emite sus cuádruplas en la tabla del generador.
     * Devuelve ResultadoC3D.vacio().
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        for (InstruccionPigLatin i : instrucciones) i.generarC3D(generador);
        return ResultadoC3D.vacio();
    }
}