package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * {@code ifStatement} (#ifStatementDef): "if (cond) entonces (else contrario)?".
 * El "else if" se resuelve por anidamiento: "contrario" puede ser otro Si.
 */
public final class Si extends NodoZ implements InstruccionZ {

    private final ExpresionZ condicion;
    private final InstruccionZ entonces;
    private final InstruccionZ contrario; // null si no hay "else"

    public Si(ExpresionZ condicion, InstruccionZ entonces, InstruccionZ contrario, int linea, int columna) {
        super(linea, columna);
        this.condicion = condicion;
        this.entonces = entonces;
        this.contrario = contrario;
    }

    public ExpresionZ getCondicion()   { return condicion; }
    public InstruccionZ getEntonces()  { return entonces; }
    public InstruccionZ getContrario() { return contrario; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tc = condicion.verificar(ambito, errores);
        if (!Tipos.esBooleano(tc))
            errores.reportar(condicion.getLinea(), condicion.getColumna(),
                    "Condición del 'if' debe ser bool, se recibió " + tc.nombre());

        AmbitoBloque ambSi = new AmbitoBloque(ambito, false);
        entonces.verificar(ambSi, errores);

        if (contrario != null) {
            AmbitoBloque ambNo = new AmbitoBloque(ambito, false);
            contrario.verificar(ambNo, errores);
        }
        return TipoPrimitivo.VOID;
    }

    /**
     * Sin "else":
     *   [cond]
     *   if_false c goto L_fin
     *   [entonces]
     *   L_fin:
     * Con "else":
     *   [cond]
     *   if_false c goto L_sino
     *   [entonces]
     *   goto L_fin
     *   L_sino:
     *   [contrario]
     *   L_fin:
     * El else-if se cubre solo: cuando "contrario" es otro Si, su propio
     * generarC3D emite su estructura completa anidada dentro de "L_sino:".
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        ResultadoC3D cond = condicion.generarC3D(generador);
        String fin = generador.nuevaEtiqueta();

        if (contrario == null) {
            generador.emitirIfFalse(cond.getLugar(), fin);
            entonces.generarC3D(generador);
            generador.emitirEtiqueta(fin);
            return ResultadoC3D.vacio();
        }

        String etiquetaSino = generador.nuevaEtiqueta();
        generador.emitirIfFalse(cond.getLugar(), etiquetaSino);
        entonces.generarC3D(generador);
        generador.emitirGoto(fin);
        generador.emitirEtiqueta(etiquetaSino);
        contrario.generarC3D(generador);
        generador.emitirEtiqueta(fin);
        return ResultadoC3D.vacio();
    }
}