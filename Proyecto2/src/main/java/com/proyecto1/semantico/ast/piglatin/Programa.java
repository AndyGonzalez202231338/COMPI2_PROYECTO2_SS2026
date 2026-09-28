package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;

import java.util.List;

/**
 * Nodo raíz del AST de PigLatin: programa importaciones? seccionVariables?
 * funcionPrincipal EOF).
 */
public final class Programa extends NodoPigLatin {

    private final List<Importacion> importaciones;
    private final List<InstruccionPigLatin> variablesGlobales; // DeclaracionVariable | DeclaracionArreglo
    private final FuncionPrincipal principal;

    public Programa(List<Importacion> importaciones, List<InstruccionPigLatin> variablesGlobales,
                    FuncionPrincipal principal, int linea, int columna) {
        super(linea, columna);
        this.importaciones = importaciones;
        this.variablesGlobales = variablesGlobales;
        this.principal = principal;
    }

    public List<Importacion> getImportaciones() { return importaciones; }
    public List<InstruccionPigLatin> getVariablesGlobales() { return variablesGlobales; }
    public FuncionPrincipal getPrincipal() { return principal; }

    /**
     * Punto de entrada de la generación de C3D (es la raíz del AST). Delega todo en
     * FuncionPrincipal#generarC3D(GeneradorC3D, List), pasándole las variables
     * globales para que las declare dentro del main
     *
     * <p>Las importaciones NO generan C3D propio: son metadata de compilación, ya
     * resueltas por CargadorImports
     * */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        principal.generarC3D(generador, variablesGlobales);
        return ResultadoC3D.vacio();
    }
}