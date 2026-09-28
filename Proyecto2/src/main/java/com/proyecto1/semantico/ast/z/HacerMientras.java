package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**(#doWhileStatementDef): "do cuerpo while(cond);". */
public final class HacerMientras extends NodoZ implements InstruccionZ {

    private final InstruccionZ cuerpo;
    private final ExpresionZ condicion;

    public HacerMientras(InstruccionZ cuerpo, ExpresionZ condicion, int linea, int columna) {
        super(linea, columna);
        this.cuerpo = cuerpo;
        this.condicion = condicion;
    }

    public InstruccionZ getCuerpo()    { return cuerpo; }
    public ExpresionZ getCondicion()   { return condicion; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoBloque amb = new AmbitoBloque(ambito, true);
        cuerpo.verificar(amb, errores);

        Tipo tc = condicion.verificar(ambito, errores);
        if (!Tipos.esBooleano(tc))
            errores.reportar(condicion.getLinea(), condicion.getColumna(),
                    "Condición del 'do-while' debe ser bool");
        return TipoPrimitivo.VOID;
    }

    /**
     *   L_inicio:
     *   [cuerpo]              (dentro de entrarCiclo/salirCiclo)
     *   L_cond:
     *   [cond]
     *   if_true c goto L_inicio
     *   L_fin:
     * El ciclo se registra como entrarCiclo(L_cond, L_fin): "continue" salta a
     * L_cond para evaluar la condición (si saltara a L_inicio repetiría el cuerpo sin
     * comprobarla) y "break" a L_fin.
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