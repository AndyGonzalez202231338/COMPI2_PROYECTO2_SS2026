package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.List;

/**
 * (#forStatementDef): "for(init? ; cond? ; act?) cuerpo".
 * El "init" es declaración única o lista de expresiones; el "act" es lista de
 * expresiones; la "cond" puede omitirse.
 */
public final class Para extends NodoZ implements InstruccionZ {

    private final DeclaracionVariable inicializacionDeclaracion;
    private final List<ExpresionZ> inicializacionExpresiones;
    private final ExpresionZ condicion;
    private final List<ExpresionZ> actualizacion;
    private final InstruccionZ cuerpo;

    public Para(DeclaracionVariable inicializacionDeclaracion, List<ExpresionZ> inicializacionExpresiones,
                ExpresionZ condicion, List<ExpresionZ> actualizacion, InstruccionZ cuerpo,
                int linea, int columna) {
        super(linea, columna);
        this.inicializacionDeclaracion = inicializacionDeclaracion;
        this.inicializacionExpresiones = inicializacionExpresiones;
        this.condicion = condicion;
        this.actualizacion = actualizacion;
        this.cuerpo = cuerpo;
    }

    public DeclaracionVariable getInicializacionDeclaracion() { return inicializacionDeclaracion; }
    public List<ExpresionZ> getInicializacionExpresiones()    { return inicializacionExpresiones; }
    public ExpresionZ getCondicion()                          { return condicion; }
    public List<ExpresionZ> getActualizacion()                { return actualizacion; }
    public InstruccionZ getCuerpo()                           { return cuerpo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoBloque ambCiclo = new AmbitoBloque(ambito, true);

        if (inicializacionDeclaracion != null)
            inicializacionDeclaracion.verificar(ambCiclo, errores);
        if (inicializacionExpresiones != null)
            for (ExpresionZ e : inicializacionExpresiones) e.verificar(ambCiclo, errores);

        if (condicion != null) {
            Tipo tc = condicion.verificar(ambCiclo, errores);
            if (!Tipos.esBooleano(tc))
                errores.reportar(condicion.getLinea(), condicion.getColumna(),
                        "Condición del 'for' debe ser bool");
        }
        if (actualizacion != null)
            for (ExpresionZ e : actualizacion) e.verificar(ambCiclo, errores);

        AmbitoBloque ambCuerpo = new AmbitoBloque(ambCiclo, false);
        cuerpo.verificar(ambCuerpo, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     *   [init]                (declaración o lista de expresiones, una sola vez)
     *   L_inicio:
     *   [cond?]
     *   if_false c goto L_fin
     *   [cuerpo]              (dentro de entrarCiclo/salirCiclo)
     *   L_act:
     *   [act]                 (lista de expresiones, en orden)
     *   goto L_inicio
     *   L_fin:
     * El ciclo se registra como entrarCiclo(L_act, L_fin): "continue" salta a
     * L_act (así se ejecuta la actualización, evitando ciclos infinitos) y "break" a
     * L_fin sin pasar por L_act. Sin condición no se emite if_false (solo termina con
     * "break"). Init y act quedan FUERA de entrarCiclo/salirCiclo.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String inicio = generador.nuevaEtiqueta();
        String act    = generador.nuevaEtiqueta();
        String fin    = generador.nuevaEtiqueta();

        // Init: solo una de las dos variantes es no-null.
        if (inicializacionDeclaracion != null) {
            inicializacionDeclaracion.generarC3D(generador);
        } else if (inicializacionExpresiones != null) {
            for (ExpresionZ e : inicializacionExpresiones) {
                e.generarC3D(generador);
            }
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
            for (ExpresionZ e : actualizacion) {
                e.generarC3D(generador);
            }
        }

        generador.emitirGoto(inicio);
        generador.emitirEtiqueta(fin);
        return ResultadoC3D.vacio();
    }
}