package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.cuadruplas.Cuadrupla;
import com.proyecto1.semantico.ast.cuadruplas.CuadruplaSalto;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.List;

/**
 * (#sentenciaSiDef: si (cond) bloque (aliter (cond) bloque)*
 * (aliter bloque)? finis;. La rama "si" más cero o más "aliter" con su propia
 * condición, y opcionalmente un "aliter" final sin condición (equivalente al
 * "contrario" de Y).
 */
public final class Si extends NodoPigLatin implements InstruccionPigLatin {

    private final List<RamaSi> ramas;   // ramas.get(0) es el "si"; el resto son los "aliter (cond)"
    private final Bloque contrario;     // null si no hay "aliter" final sin condición

    public Si(List<RamaSi> ramas, Bloque contrario, int linea, int columna) {
        super(linea, columna);
        this.ramas = ramas;
        this.contrario = contrario;
    }

    public List<RamaSi> getRamas() { return ramas; }
    public Bloque getContrario() { return contrario; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        for (RamaSi rama : ramas) {
            Tipo tc = rama.getCondicion().verificar(ambito, errores);
            if (!Tipos.esBooleano(tc))
                errores.reportar(rama.getCondicion().getLinea(), rama.getCondicion().getColumna(),
                        "Condición del 'si' debe ser bool");
            AmbitoBloque amb = new AmbitoBloque(ambito, false);
            rama.getCuerpo().verificar(amb, errores);
        }
        if (contrario != null) {
            AmbitoBloque amb = new AmbitoBloque(ambito, false);
            contrario.verificar(amb, errores);
        }
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, con backpatching múltiple, por cada rama:
     *   [cond]
     *   if_false c goto ?          <- destino pendiente
     *   [cuerpo de la rama]
     *   goto ?                     <- pendiente hacia L_fin (omitido en la última rama sin contrario)
     *   L_siguiente:               <- aquí se rellena el if_false de esta rama
     * Al terminar todas las ramas: el cuerpo de "contrario" (si existe), L_fin:
     * y se rellenan con L_fin todos los saltos pendientes. Si la última rama no tiene
     * "contrario", su if_false salta directo a L_fin (sin goto ni etiqueta intermedia).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        List<Integer> pendientesFin = new ArrayList<>();

        for (int i = 0; i < ramas.size(); i++) {
            RamaSi rama = ramas.get(i);
            boolean ultimaSinContrario = (i == ramas.size() - 1) && contrario == null;

            ResultadoC3D condicion = rama.getCondicion().generarC3D(generador);

            int indiceIfFalse = generador.siguienteIndice();
            generador.emitirIfFalse(condicion.getLugar(), null);

            rama.getCuerpo().generarC3D(generador);

            if (ultimaSinContrario) {
                pendientesFin.add(indiceIfFalse);
            } else {
                pendientesFin.add(generador.siguienteIndice());
                generador.emitirGoto(null);

                String siguiente = generador.nuevaEtiqueta();
                generador.emitirEtiqueta(siguiente);
                parchear(generador, indiceIfFalse, siguiente);
            }
        }

        if (contrario != null) {
            contrario.generarC3D(generador);
        }

        String fin = generador.nuevaEtiqueta();
        generador.emitirEtiqueta(fin);
        for (int indice : pendientesFin) {
            parchear(generador, indice, fin);
        }
        return ResultadoC3D.vacio();
    }

    private static void parchear(GeneradorC3D generador, int indice, String etiqueta) {
        Cuadrupla actual = generador.getCuadruplas().get(indice);
        generador.reemplazar(indice, ((CuadruplaSalto) actual).conEtiquetaDestino(etiqueta));
    }
}