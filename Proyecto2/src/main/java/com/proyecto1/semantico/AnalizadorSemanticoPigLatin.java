package com.proyecto1.semantico;

import com.proyecto1.semantico.ast.piglatin.InstruccionPigLatin;
import com.proyecto1.semantico.ast.piglatin.Programa;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.AmbitoGlobal;

public class AnalizadorSemanticoPigLatin {

    /**
     * Resultado del análisis: los errores encontrados Y el ámbito global del .pig
     * (con las variables globales del programa declaradas), para que quien llamó
     * pueda reutilizarlo — típicamente para pasar el ámbito al generador de C3D.
     */
    public record Resultado(ManejadorErrores errores, AmbitoGlobal globalPig) {}

    /**
     * @param programa el AST del .pig ya construido
     * @param globalImports el AmbitoGlobal compartido de TODOS los .y y .z importados
     *                      (debe haberse construido antes analizando esos archivos)
     */
    public Resultado analizar(Programa programa, AmbitoGlobal globalImports) {
        ManejadorErrores errores = new ManejadorErrores();

        // Ámbito global del .pig, con el de imports como padre
        AmbitoGlobal globalPig = new AmbitoGlobal(globalImports);

        // ---- Declarar variables globales del .pig ----
        for (InstruccionPigLatin v : programa.getVariablesGlobales()) {
            v.verificar(globalPig, errores);
        }

        // ---- Verificar la función principal ----
        programa.getPrincipal().verificar(globalPig, errores);

        errores.imprimir();
        return new Resultado(errores, globalPig);
    }
}