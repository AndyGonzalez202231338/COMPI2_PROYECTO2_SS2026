package com.proyecto1.semantico;


import com.proyecto1.semantico.ast.y.Estructura;
import com.proyecto1.semantico.ast.y.Funcion;
import com.proyecto1.semantico.ast.y.Programa;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

public class AnalizadorSemanticoY {

    public ManejadorErrores analizar(Programa programa) {
        return analizar(programa, new AmbitoGlobal());
    }

    /**
     * Igual que {@link #analizar(Programa)} pero llenando el {@code global} que entrega quien
     * llama. Así, al terminar, quien llamó conserva las estructuras y funciones ya resueltas
     * (con sus campos y parámetros) -- es lo que necesita un .pig para poder importarlas.
     */
    public ManejadorErrores analizar(Programa programa, AmbitoGlobal global) {
        ManejadorErrores errores = new ManejadorErrores();

        // ---- PRIMERA PASADA: recolectar estructuras y funciones ----
        for (Estructura e : programa.getEstructuras()) {
            Simbolo s = new Simbolo(e.getNombre(), CategoriaSimbolo.ESTRUCTURA,
                    null, e.getLinea(), e.getColumna());
            if (!global.declarar(s)) {
                errores.reportar(e.getLinea(), e.getColumna(),
                        "Estructura duplicada: '" + e.getNombre() + "'");
            }
        }

        for (Funcion f : programa.getFunciones()) {
            // Resolver el tipo de retorno AHORA (ya hay estructuras en el global)
            Tipo tRet = (f.getTipoRetorno() == null)
                    ? TipoPrimitivo.VOID
                    : f.getTipoRetorno().resolver(global, errores);

            Simbolo s = new Simbolo(f.getNombre(), CategoriaSimbolo.FUNCION,
                    tRet, f.getLinea(), f.getColumna());
            if (!global.declarar(s)) {
                errores.reportar(f.getLinea(), f.getColumna(),
                        "Función duplicada: '" + f.getNombre() + "'");
            }
        }

        // ---- SEGUNDA PASADA: verificar cuerpos ----
        for (Estructura e : programa.getEstructuras()) e.verificar(global, errores);
        for (Funcion f : programa.getFunciones())    f.verificar(global, errores);

        // Reporte final
        errores.imprimir();
        return errores;
    }
}