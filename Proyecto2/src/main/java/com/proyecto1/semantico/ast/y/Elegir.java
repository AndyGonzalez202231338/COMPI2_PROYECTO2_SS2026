package com.proyecto1.semantico.ast.y;

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

/** (#condicionElegirDef): "elegir(control): caso* siempre?". */
public final class Elegir extends NodoY implements InstruccionY {

    private final ExpresionY control;
    private final List<CasoElegir> casos;
    private final Bloque siempre; // null si no hay "siempre"

    public Elegir(ExpresionY control, List<CasoElegir> casos, Bloque siempre, int linea, int columna) {
        super(linea, columna);
        this.control = control;
        this.casos = casos;
        this.siempre = siempre;
    }

    public ExpresionY getControl() {
        return control;
    }

    public List<CasoElegir> getCasos() {
        return casos;
    }

    public Bloque getSiempre() {
        return siempre;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tControl = control.verificar(ambito, errores);

        // El control debe ser entero/caracter/cadena según (caso literal)
        // Validamos que cada caso sea compatible
        for (CasoElegir c : casos) {
            Tipo tCaso = c.getValor().verificar(ambito, errores);
            if (!Tipos.esComparableIgualdad(tControl, tCaso))
                errores.reportar(c.getValor().getLinea(), c.getValor().getColumna(),
                        "Caso incompatible con el control: " + tCaso.nombre() + " vs " + tControl.nombre());

            // esCiclo=false, permiteRomper=true: "romper" sí puede usarse acá (sale del
            // elegir, como el break de un switch); "continuar" NO (dentroDeAlgunCiclo()
            // solo mira esCiclo, y sigue sin ver este ámbito como un ciclo).
            AmbitoBloque amb = new AmbitoBloque(ambito, false, true);
            c.getCuerpo().verificar(amb, errores);
        }

        if (siempre != null) {
            AmbitoBloque amb = new AmbitoBloque(ambito, false, true);
            siempre.verificar(amb, errores);
        }
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, en este orden:
     *   El C3D de la expresión de control (una sola vez).
     *   Bloque de pruebas: por cada caso t = c == literal y
     *       if_true t goto ? (destino pendiente: la etiqueta del caso).
     *   goto ?: hacia "siempre" si existe, o hacia L_fin si no (pendiente).
     *   Los cuerpos: cada caso lleva su etiqueta (con ella se rellena su if_true),
     *       su C3D y termina con goto ? hacia L_fin (pendiente). Mientras se
     *       generan estos cuerpos (y el de "siempre") el generador tiene registrado
     *       L_fin como destino de "romper" (entrarBloqueRompible/salirBloqueRompible),
     *       así un "romper" explícito dentro de un caso emite el mismo goto que ya se
     *       emite automáticamente al terminar el caso.
     *   "siempre" (si existe) con su etiqueta, que rellena el goto del paso 3; no
     *       necesita goto porque cae directo en L_fin.
     *   L_fin: y el relleno de todos los saltos pendientes hacia el fin.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        /*
            elegir(opcion):
                caso 1:
                    x = 10
                    romper
                caso 2:
                    x = 20
                    romper
                siempre:
                    x = 30
                    romper
         */
        // opcion y su valor
        ResultadoC3D valorControl = control.generarC3D(generador);

        // Pruebas: un if_true por caso, con destino pendiente.
        List<Integer> saltosACaso = new ArrayList<>();
        for (CasoElegir caso : casos) {
            ResultadoC3D literal = caso.getValor().generarC3D(generador);
            String t = generador.nuevoTemporal();
            //t0 = opcion == 1
            // cuadrupla (==, opcion, 1, t0)
            generador.emitirBinaria("==", valorControl.getLugar(), literal.getLugar(), t);
            // en la lista de indices de los saltos que tiene uqe hacer entre caso
            saltosACaso.add(generador.siguienteIndice());
            // (condicion, etiqueta aun no dada)
            generador.emitirIfTrue(t, null);
        }

        // Ningún caso coincidió: a "siempre" o al final (pendiente).
        int indiceSaltoDefecto = generador.siguienteIndice();
        generador.emitirGoto(null);

        // Se calcula ya L_fin (aunque se emite al final) para poder registrarla como
        // destino de "romper" mientras se generan los cuerpos.
        String fin = generador.nuevaEtiqueta(); //L0
        generador.entrarBloqueRompible(fin);

        // Cuerpos de los casos.
        List<Integer> pendientesFin = new ArrayList<>();
        for (int i = 0; i < casos.size(); i++) {
            //L1: se crea la etiqueta donde llevara el cuerpo del caso
            String etiquetaCaso = generador.nuevaEtiqueta();
            generador.emitirEtiqueta(etiquetaCaso);
            //se le asigna ala cuadrupla a que etiqueta moverse para resolver su cuerpo
            parchear(generador, saltosACaso.get(i), etiquetaCaso);
            // se genera el C3D del cuerpo
            casos.get(i).getCuerpo().generarC3D(generador);
            //salir del elegir con goto L0 (salida)
            pendientesFin.add(generador.siguienteIndice());
            generador.emitirGoto(null);
        }

        // "siempre": cae directo en L0.
        if (siempre != null) {
            String etiquetaSiempre = generador.nuevaEtiqueta();
            generador.emitirEtiqueta(etiquetaSiempre);
            parchear(generador, indiceSaltoDefecto, etiquetaSiempre);
            siempre.generarC3D(generador);
        } else {
            pendientesFin.add(indiceSaltoDefecto);
        }

        generador.salirBloqueRompible();

        // Fin y relleno.
        //pasarle L0 a los indices de las cuadruplas que sera de salida anteriormente todos nulo
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