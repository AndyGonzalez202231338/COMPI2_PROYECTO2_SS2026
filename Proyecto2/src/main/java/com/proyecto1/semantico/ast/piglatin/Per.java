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
 * (#sentenciaPerDef): per (init; cond?; act?) bloque.
 * Equivale al Para de Y. Init puede ser DeclaracionVariable o
 * ListaExpresiones; act siempre es ListaExpresiones. Ambos, más la
 * condición, son opcionales.
 */
public final class Per extends NodoPigLatin implements InstruccionPigLatin {

    private final InstruccionPigLatin inicializacion; // DeclaracionVariable | ListaExpresiones | null
    private final ExpresionPigLatin condicion;        // null si se omitió
    private final InstruccionPigLatin actualizacion;  // ListaExpresiones | null
    private final Bloque cuerpo;

    public Per(InstruccionPigLatin inicializacion, ExpresionPigLatin condicion,
               InstruccionPigLatin actualizacion, Bloque cuerpo, int linea, int columna) {
        super(linea, columna);
        this.inicializacion = inicializacion;
        this.condicion = condicion;
        this.actualizacion = actualizacion;
        this.cuerpo = cuerpo;
    }

    public InstruccionPigLatin getInicializacion() { return inicializacion; }
    public ExpresionPigLatin getCondicion() { return condicion; }
    public InstruccionPigLatin getActualizacion() { return actualizacion; }
    public Bloque getCuerpo() { return cuerpo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoBloque ambCiclo = new AmbitoBloque(ambito, true);
        if (inicializacion != null) inicializacion.verificar(ambCiclo, errores);
        if (condicion != null) {
            Tipo tc = condicion.verificar(ambCiclo, errores);
            if (!Tipos.esBooleano(tc))
                errores.reportar(condicion.getLinea(), condicion.getColumna(),
                        "Condición del 'per' debe ser bool");
        }
        if (actualizacion != null) actualizacion.verificar(ambCiclo, errores);

        AmbitoBloque ambCuerpo = new AmbitoBloque(ambCiclo, false);
        cuerpo.verificar(ambCuerpo, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     *   [init]                (una sola vez, fuera del ciclo)
     *   L_inicio:
     *   [cond?]
     *   if_false c goto L_fin
     *   [cuerpo]              (dentro de entrarCiclo/salirCiclo)
     *   L_act:
     *   [act?]
     *   goto L_inicio
     *   L_fin:
     *
     * Se registra el ciclo como entrarCiclo(L_act, L_fin): "perge" salta a
     * L_act (así la actualización SÍ se ejecuta, evitando ciclos infinitos si el cuerpo
     * hace "perge" antes del incremento) e "interrumpe" a L_fin sin pasar por L_act.
     * Sin condición no se emite if_false (el ciclo solo termina con "interrumpe").
     * Init y act quedan FUERA de entrarCiclo/salirCiclo. Devuelve
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String inicio = generador.nuevaEtiqueta();
        String act    = generador.nuevaEtiqueta();
        String fin    = generador.nuevaEtiqueta();

        if (inicializacion != null) {
            inicializacion.generarC3D(generador);
        }

        generador.emitirEtiqueta(inicio);

        if (condicion != null) {
            ResultadoC3D c = condicion.generarC3D(generador);
            generador.emitirIfFalse(c.getLugar(), fin);
        }

        generador.entrarCiclo(act, fin);
        cuerpo.generarC3D(generador);
        generador.salirCiclo();

        generador.emitirEtiqueta(act);
        if (actualizacion != null) {
            actualizacion.generarC3D(generador);
        }

        generador.emitirGoto(inicio);
        generador.emitirEtiqueta(fin);
        return ResultadoC3D.vacio();
    }
}