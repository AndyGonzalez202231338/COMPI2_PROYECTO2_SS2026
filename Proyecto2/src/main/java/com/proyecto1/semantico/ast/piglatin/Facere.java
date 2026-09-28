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
 * {sentenciaFacere} (#sentenciaFacereDef): facere bloque dum (cond);.
 * El cuerpo se ejecuta al menos una vez y la condición se evalúa DESPUÉS de cada
 * iteración.
 */
public final class Facere extends NodoPigLatin implements InstruccionPigLatin {

    private final Bloque cuerpo;
    private final ExpresionPigLatin condicion;

    public Facere(Bloque cuerpo, ExpresionPigLatin condicion, int linea, int columna) {
        super(linea, columna);
        this.cuerpo = cuerpo;
        this.condicion = condicion;
    }

    public Bloque getCuerpo() { return cuerpo; }
    public ExpresionPigLatin getCondicion() { return condicion; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoBloque amb = new AmbitoBloque(ambito, true);
        cuerpo.verificar(amb, errores);
        Tipo tc = condicion.verificar(ambito, errores);
        if (!Tipos.esBooleano(tc))
            errores.reportar(condicion.getLinea(), condicion.getColumna(),
                    "Condición del 'facere' debe ser bool");
        return TipoPrimitivo.VOID;
    }

    /**
     *   L_inicio:
     *   [cuerpo]              (dentro de entrarCiclo/salirCiclo)
     *   L_cond:
     *   [cond]
     *   if_true c goto L_inicio
     *   L_fin:

     * Se registra el ciclo como entrarCiclo(L_cond, L_fin): "perge" salta a
     * L_cond para reevaluar la condición (si saltara a L_inicio repetiría el cuerpo
     * sin comprobarla) y "interrumpe" a L_fin.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String inicio = generador.nuevaEtiqueta();
        String cond   = generador.nuevaEtiqueta();
        String fin    = generador.nuevaEtiqueta();

        generador.emitirEtiqueta(inicio);

        generador.entrarCiclo(cond, fin);
        cuerpo.generarC3D(generador);
        generador.salirCiclo();

        generador.emitirEtiqueta(cond);
        ResultadoC3D c = condicion.generarC3D(generador);
        generador.emitirIfTrue(c.getLugar(), inicio);

        generador.emitirEtiqueta(fin);
        return ResultadoC3D.vacio();
    }
}