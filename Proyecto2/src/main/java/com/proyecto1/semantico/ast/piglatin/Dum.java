package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * entenciaDum (#sentenciaDumDef): dum (cond) bloque . Equivale
 * al Mientras de Y: evalúa la condición ANTES de cada iteración.
 */
public final class Dum extends NodoPigLatin implements InstruccionPigLatin {

    private final ExpresionPigLatin condicion;
    private final Bloque cuerpo;

    public Dum(ExpresionPigLatin condicion, Bloque cuerpo, int linea, int columna) {
        super(linea, columna);
        this.condicion = condicion;
        this.cuerpo = cuerpo;
    }

    public ExpresionPigLatin getCondicion() { return condicion; }
    public Bloque getCuerpo() { return cuerpo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tc = condicion.verificar(ambito, errores);
        if (!Tipos.esBooleano(tc))
            errores.reportar(condicion.getLinea(), condicion.getColumna(),
                    "Condición del 'dum' debe ser bool");
        AmbitoBloque amb = new AmbitoBloque(ambito, true);
        cuerpo.verificar(amb, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     *   L_inicio:
     *   [cond]
     *   if_false c goto L_fin
     *   [cuerpo]              (dentro de entrarCiclo/salirCiclo)
     *   goto L_inicio
     *   L_fin:
     * "perge" (continue) salta a L_inicio (reevalúa la condición); "interrumpe" a L_fin..
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String inicio = generador.nuevaEtiqueta();
        String fin    = generador.nuevaEtiqueta();

        generador.emitirEtiqueta(inicio);
        ResultadoC3D c = condicion.generarC3D(generador);
        generador.emitirIfFalse(c.getLugar(), fin);

        generador.entrarCiclo(inicio, fin);
        cuerpo.generarC3D(generador);
        generador.salirCiclo();

        generador.emitirGoto(inicio);
        generador.emitirEtiqueta(fin);
        return ResultadoC3D.vacio();
    }
}