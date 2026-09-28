package com.proyecto1.semantico.errores;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recolector de errores semánticos. Una sola instancia se crea al arrancar el análisis
 * de un archivo y se le va PASANDO (no compartiendo por estático/singleton, para poder
 * analizar varios archivos en paralelo o en pruebas unitarias sin que se pisen) a cada
 * llamada de verificar(ambito, errores) en todo el árbol.
 *
 * Esto es justo lo que permite que el análisis semántico NO esté en una sola clase
 * gigante: cada nodo reporta sus propios errores en el momento en que los detecta y
 * sigue analizando a sus hijos igual (no se detiene en el primer error), para que al
 * final el usuario vea de una vez todos los problemas de su programa.
 */
public class ManejadorErrores {

    private final List<ErrorSemantico> errores = new ArrayList<>();

    public void reportar(int linea, int columna, String mensaje) {
        errores.add(new ErrorSemantico(linea, columna, mensaje));
    }

    public boolean tieneErrores() { return !errores.isEmpty(); }

    public int cantidad() { return errores.size(); }

    /** Copia ordenada por línea y luego columna, para que la salida sea fácil de leer. */
    public List<ErrorSemantico> obtenerErrores() {
        List<ErrorSemantico> copia = new ArrayList<>(errores);
        copia.sort(Comparator.comparingInt(ErrorSemantico::getLinea)
                .thenComparingInt(ErrorSemantico::getColumna));
        return copia;
    }

    public void imprimir() {
        if (!tieneErrores()) {
            System.out.println("Análisis semántico: sin errores.");
            return;
        }
        System.out.println("Análisis semántico: " + cantidad() + " error(es):");
        for (ErrorSemantico e : obtenerErrores()) {
            System.out.println("  " + e);
        }
    }
}
