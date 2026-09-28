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

/**
 * instruccionSi (#condicionSiDef): la rama "si" más cero o más "sino" con su
 * propia condición, y opcionalmente un "contrario" final sin condición.
 */
public final class Si extends NodoY implements InstruccionY {

    private final List<RamaSi> ramas;    // ramas.get(0) es el "si"; el resto son los "sino"
    private final Bloque contrario;       // null si no hay "contrario"

    public Si(List<RamaSi> ramas, Bloque contrario, int linea, int columna) {
        super(linea, columna);
        this.ramas = ramas;
        this.contrario = contrario;
    }

    public List<RamaSi> getRamas() {
        return ramas;
    }

    public Bloque getContrario() {
        return contrario;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // Cada rama (si / sino) tiene su condicion (bool) y su propio sub-ambito, igual que en Z.
        // El ambito de cada rama es hijo del actual, asi un 'continuar'/'romper' dentro de un si
        // que esta dentro de un ciclo sigue viendo el ciclo (dentroDeAlgunCiclo sube por los padres).
        for (RamaSi rama : ramas) {
            Tipo tc = rama.getCondicion().verificar(ambito, errores);
            if (!Tipos.esBooleano(tc))
                errores.reportar(rama.getCondicion().getLinea(), rama.getCondicion().getColumna(),
                        "La condición de 'si' debe ser bool, se recibió " + tc.nombre());
            rama.getCuerpo().verificar(new AmbitoBloque(ambito, false), errores);
        }
        if (contrario != null) {
            contrario.verificar(new AmbitoBloque(ambito, false), errores);
        }
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, con backpatching, para cada rama (si / sino):
     *   [cuádruplas de la condición]
     *   if_false c goto ?          <- destino pendiente
     *   [cuerpo de la rama]
     *   goto ?                     <- pendiente hacia L_fin (omitido en la última rama sin contrario)
     *   L_siguiente:               <- aquí se rellena el if_false de esta rama
     * Al terminar todas las ramas: el cuerpo de "contrario" (si existe) y L_fin:,
     * y entonces se rellenan con L_fin todos los saltos pendientes. Si la última rama no
     * tiene "contrario", su if_false salta directamente a L_fin (no hace falta un goto ni
     * una etiqueta intermedia).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        List<Integer> pendientesFin = new ArrayList<>();
        /*
            si (x > 0) entonces
                //imprimir("positivo")
            sino (x == 0) entonces
                //imprimir("cero")
            contrario
                //imprimir("negativo")
         */
        // Se recorren todas las ramas condicionales ("si" y "sino")
        for (int i = 0; i < ramas.size(); i++) {
            RamaSi rama = ramas.get(i);

            // Verifica si estamos en la última condición y no hay bloque "contrario" (else)
            boolean ultimaSinContrario = (i == ramas.size() - 1) && contrario == null;

            // Genera el código C3D para evaluar la expresión condicional.
            // Iteración 1 (si): Genera "t0 = x > 0"
            // Iteración 2 (sino): Genera "t1 = x == 0"
            ResultadoC3D condicion = rama.getCondicion().generarC3D(generador);

            // Obtenemos el índice donde se colocará el salto condicional (para parchearlo después)
            int indiceIfFalse = generador.siguienteIndice();

            // Emitimos el salto por si la condición es falsa, pero dejamos el destino "pendiente" (null).
            // Iteración 1: Emite "3: if_false t0 goto null"
            // Iteración 2: Emite "7: if_false t1 goto null"
            generador.emitirIfFalse(condicion.getLugar(), null);

            rama.getCuerpo().generarC3D(generador);

            if (ultimaSinContrario) {
                // Si es la última rama y no hay un "contrario", si la condición falla,
                // el flujo debe ir directo a la etiqueta final de salida.
                pendientesFin.add(indiceIfFalse);
            } else {
                // Si la condición fue verdadera y ejecutó su cuerpo, debemos saltar
                // al final de toda la estructura para ignorar los demás "sino/contrario".
                pendientesFin.add(generador.siguienteIndice()); // Guardamos el índice para parchearlo al final.

                // Emitimos el salto incondicional con destino pendiente.
                // Iteración 1: goto null (luego será L2)
                // Iteración 2: goto null (luego será L2)
                generador.emitirGoto(null);

                // Se crea la etiqueta donde debe aterrizar el programa si la condición actual fue falsa.
                String siguiente = generador.nuevaEtiqueta();

                // Iteración 1: "L0:" osea sino (x == 0) entonces
                // Iteración 2: "L1:" contrario
                generador.emitirEtiqueta(siguiente);


                // Regresamos a la instrucción 'if_false' que dejamos pendiente y le colocamos
                // esta nueva etiqueta como destino.
                // Iteración 1: Modifica la instrucción 3 para que sea "if_false t0 goto L0"
                // Iteración 2: Modifica la instrucción 7 para que sea "if_false t1 goto L1"
                parchear(generador, indiceIfFalse, siguiente);
            }
        }

        // Si la estructura tiene un bloque "contrario", se genera su C3D aquí.
        if (contrario != null) {
            contrario.generarC3D(generador);
        }

        // Se crea la etiqueta final a la que llegarán todas las ramas que se ejecutaron con éxito.
        String fin = generador.nuevaEtiqueta();

        // Emite "L2:" la etiqueta de salida para todos
        generador.emitirEtiqueta(fin);


        // Recorremos todos los "goto" pendientes que dejamos guardados en la lista
        // y les asignamos esta etiqueta de salida final.
        for (int indice : pendientesFin) {

            // Modifica los indices para que sea "goto L2"
            parchear(generador, indice, fin);
        }

        return ResultadoC3D.vacio();
    }

    private static void parchear(GeneradorC3D generador, int indice, String etiqueta) {
        Cuadrupla actual = generador.getCuadruplas().get(indice);
        generador.reemplazar(indice, ((CuadruplaSalto) actual).conEtiquetaDestino(etiqueta));
    }
}