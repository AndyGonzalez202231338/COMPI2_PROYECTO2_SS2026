package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

public final class HacerMientras extends NodoY implements InstruccionY {

    private final Bloque cuerpo;
    private final ExpresionY condicion;

    public HacerMientras(Bloque cuerpo, ExpresionY condicion, int linea, int columna) {
        super(linea, columna);
        this.cuerpo = cuerpo;
        this.condicion = condicion;
    }

    public Bloque getCuerpo() { return cuerpo; }
    public ExpresionY getCondicion() { return condicion; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoBloque amb = new AmbitoBloque(ambito, true); // esCiclo = true
        cuerpo.verificar(amb, errores);

        Tipo tc = condicion.verificar(ambito, errores);
        if (!Tipos.esBooleano(tc))
            errores.reportar(condicion.getLinea(), condicion.getColumna(),
                    "La condición de 'hacer-mientras' debe ser bool, se recibió " + tc.nombre());

        return TipoPrimitivo.VOID;
    }

    /**
     *   L_inicio:
     *   [cuerpo]                     (entre entrarCiclo y salirCiclo)
     *   L_cond:
     *   [condición]
     *   if_true c goto L_inicio
     *   L_fin:
     * L_inicio va ANTES del cuerpo (el cuerpo se ejecuta al menos una vez y se repite
     * desde ahí). Se registra el ciclo como entrarCiclo(L_cond, L_fin):
     * "continuar" salta a L_cond para evaluar la condición (si saltara a L_inicio se
     * repetiría el cuerpo sin comprobarla) y "romper" salta a L_fin.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
    /*
        entero i = 0
        hacer:
            i++
        mientras(i < 3)
    */
        // L_inicio: donde arranca cada repeticion del cuerpo.
        String inicio = generador.nuevaEtiqueta();
        // L_cond: donde "continuar" salta a evaluar la condicion (asi el cuerpo
        // SIEMPRE corre al menos una vez, aunque la condicion sea falsa desde
        // el principio).
        String cond = generador.nuevaEtiqueta();
        // L_fin: donde aterriza "romper", o el salto cuando la condicion resulta falsa.
        String fin = generador.nuevaEtiqueta();

        // Cuádrupla generada "L0:"
        generador.emitirEtiqueta(inicio);

        // "continuar" salta a L_cond (evalua la condicion sin saltarse nada del
        // cuerpo que ya corrio); "romper" salta a L_fin.
        generador.entrarCiclo(cond, fin);
        cuerpo.generarC3D(generador);
        generador.salirCiclo();

        // Cuádrupla generada "L_cond:"
        generador.emitirEtiqueta(cond);
        // Cuádrupla generada "t0 = i < 3"
        ResultadoC3D c = condicion.generarC3D(generador);
        // Si la condicion es VERDADERA, se repite el cuerpo.
        // Cuádrupla generada "if_true t0 goto L0"
        generador.emitirIfTrue(c.getLugar(), inicio);

        // Cuádrupla generada "L_fin:"
        generador.emitirEtiqueta(fin);

        return ResultadoC3D.vacio();
    }
}