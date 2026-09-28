package com.proyecto1.semantico.ast;

import com.proyecto1.semantico.ast.cuadruplas.Cuadrupla;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lista ordenada y global de cuádruplas emitidas por todo el árbol.
 *
 * Es la ÚNICA fuente de verdad del código generado: los nodos no guardan cuádruplas,
 * las emiten aquí a través del GeneradorC3D. Así un índice (siguienteIndice()) es
 * estable durante toda la generación, y se puede volver a una cuádrupla ya emitida
 * (por ejemplo un goto cuyo destino aún no se conocía) y reemplazarla: backpatching.
 *
 * Sin cambios de comportamiento respecto a antes — Cuadrupla pasó de ser una clase
 * a una interfaz sellada (com.proyecto1.semantico.ast.cuadruplas.Cuadrupla), pero
 * esta tabla la sigue tratando de forma genérica (List<Cuadrupla>), así que solo
 * cambió el import.
 */
public class TablaCuadruplas {

    private final List<Cuadrupla> cuadruplas = new ArrayList<>();

    public void agregar(Cuadrupla cuadrupla) {
        if (cuadrupla == null) {
            throw new IllegalArgumentException("La cuádrupla no puede ser null");
        }
        cuadruplas.add(cuadrupla);
    }

    public int tamanio() {
        return cuadruplas.size();
    }

    public Cuadrupla get(int indice) {
        return cuadruplas.get(indice);
    }

    /** Vista inmutable de las cuádruplas actuales. */
    public List<Cuadrupla> getCuadruplas() {
        return Collections.unmodifiableList(cuadruplas);
    }

    /** Índice que tendrá la próxima cuádrupla que se agregue. */
    public int siguienteIndice() {
        return cuadruplas.size();
    }

    /** Backpatching: sustituye la cuádrupla en la posición indicada. */
    public void reemplazar(int indice, Cuadrupla nueva) {
        if (nueva == null) {
            throw new IllegalArgumentException("La cuádrupla no puede ser null");
        }
        if (indice < 0 || indice >= cuadruplas.size()) {
            throw new IndexOutOfBoundsException(
                    "Índice de cuádrupla fuera de rango: " + indice + " (tamaño " + cuadruplas.size() + ")");
        }
        cuadruplas.set(indice, nueva);
    }

    /** Una cuádrupla legible por línea, numerada: "0: t0 = a + b". */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cuadruplas.size(); i++) {
            sb.append(i).append(": ").append(cuadruplas.get(i).toStringLegible()).append('\n');
        }
        return sb.toString();
    }
}