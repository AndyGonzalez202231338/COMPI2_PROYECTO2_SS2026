package com.proyecto1.semantico.ast.z;

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

/**(#switchStatementDef): "switch(control) { caso* default? }". */
public final class Elegir extends NodoZ implements InstruccionZ {

    private final ExpresionZ control;
    private final List<CasoElegir> casos;
    private final CasoDefecto porDefecto; // null si no hay "default"

    public Elegir(ExpresionZ control, List<CasoElegir> casos, CasoDefecto porDefecto, int linea, int columna) {
        super(linea, columna);
        this.control = control;
        this.casos = casos;
        this.porDefecto = porDefecto;
    }

    public ExpresionZ getControl()         { return control; }
    public List<CasoElegir> getCasos()     { return casos; }
    public CasoDefecto getPorDefecto()     { return porDefecto; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tControl = control.verificar(ambito, errores);

        for (CasoElegir c : casos) {
            Tipo tCaso = c.getValor().verificar(ambito, errores);
            if (!Tipos.esComparableIgualdad(tControl, tCaso))
                errores.reportar(c.getValor().getLinea(), c.getValor().getColumna(),
                        "Caso incompatible con el control: " + tCaso.nombre() + " vs " + tControl.nombre());

            // esCiclo=false, permiteRomper=true: "break" sí puede usarse acá (sale del
            // switch, como el break de C); "continue" NO (dentroDeAlgunCiclo() solo mira
            // esCiclo, y este ámbito ya no se ve como un ciclo).
            AmbitoBloque ambCaso = new AmbitoBloque(ambito, false, true); // antes: (ambito, true)
            for (InstruccionZ i : c.getInstrucciones()) i.verificar(ambCaso, errores);
        }

        if (porDefecto != null) {
            AmbitoBloque ambDef = new AmbitoBloque(ambito, false, true); // antes: (ambito, true)
            for (InstruccionZ i : porDefecto.getInstrucciones()) i.verificar(ambDef, errores);
        }
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, en este orden:
     *   C3D de la expresión de control (una sola vez).
     *   Bloque de pruebas: por cada caso t = control == valor y
     *       if_true t goto ? (destino pendiente: la etiqueta del caso).
     *   goto ? hacia "default" si existe, o hacia L_fin si no.
     *   Cuerpos: cada caso lleva su etiqueta (rellena el if_true correspondiente),
     *       sus instrucciones y, SOLO si tieneRomper, un goto L_fin
     *       pendiente. Sin tieneRomper el cuerpo no cierra el caso: la última
     *       instrucción cae directamente al siguiente caso (fall-through estilo C).
     *   "default" (si existe): etiqueta + instrucciones. Su tieneRomper
     *       solo decide si se emite un goto L_fin explícito; en cualquier caso
     *       termina en L_fin por caída natural.
     *   L_fin: y relleno de todos los saltos pendientes hacia el fin.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        ResultadoC3D valorControl = control.generarC3D(generador);

        // 1) Pruebas: un if_true por caso, con destino pendiente.
        List<Integer> saltosACaso = new ArrayList<>();
        for (CasoElegir caso : casos) {
            ResultadoC3D valor = caso.getValor().generarC3D(generador);
            String t = generador.nuevoTemporal();
            generador.emitirBinaria("==", valorControl.getLugar(), valor.getLugar(), t);
            saltosACaso.add(generador.siguienteIndice());
            generador.emitirIfTrue(t, null);
        }

        // 2) Sin coincidencia: a "default" o al fin (pendiente).
        int indiceSaltoDefault = generador.siguienteIndice();
        generador.emitirGoto(null);

        // 3) Reservar L_fin por adelantado, registrarlo como destino de break.
        String fin = generador.nuevaEtiqueta();
        generador.entrarBloqueRompible(fin);

        // 4) Cuerpos de los casos (con fall-through cuando !tieneRomper).
        List<Integer> pendientesFin = new ArrayList<>();
        for (int i = 0; i < casos.size(); i++) {
            CasoElegir caso = casos.get(i);
            String etiquetaCaso = generador.nuevaEtiqueta();
            generador.emitirEtiqueta(etiquetaCaso);
            parchear(generador, saltosACaso.get(i), etiquetaCaso);

            for (InstruccionZ inst : caso.getInstrucciones()) {
                inst.generarC3D(generador);
            }
            if (caso.isTieneRomper()) {
                pendientesFin.add(generador.siguienteIndice());
                generador.emitirGoto(null);
            }
            // Sin romper: NO se emite goto — la ejecución cae al siguiente caso.
        }

        // 5) "default" (si existe): cae directo a L_fin por su posición.
        if (porDefecto != null) {
            String etiquetaDefault = generador.nuevaEtiqueta();
            generador.emitirEtiqueta(etiquetaDefault);
            parchear(generador, indiceSaltoDefault, etiquetaDefault);

            for (InstruccionZ inst : porDefecto.getInstrucciones()) {
                inst.generarC3D(generador);
            }
            if (porDefecto.isTieneRomper()) {
                pendientesFin.add(generador.siguienteIndice());
                generador.emitirGoto(null);
            }
        } else {
            // Sin default, el goto del paso 2 debe caer al fin.
            pendientesFin.add(indiceSaltoDefault);
        }

        generador.salirBloqueRompible();

        // 6) Fin y relleno.
        generador.emitirEtiqueta(fin);
        for (int indice : pendientesFin) {
            parchear(generador, indice, fin);
        }
        return ResultadoC3D.vacio();
    }

    /** Backpatching: escribe "etiqueta" como destino de la cuádrupla de salto en "indice". */
    private static void parchear(GeneradorC3D generador, int indice, String etiqueta) {
        Cuadrupla actual = generador.getCuadruplas().get(indice);
        generador.reemplazar(indice, ((CuadruplaSalto) actual).conEtiquetaDestino(etiqueta));
    }
}