package com.proyecto1.semantico.ast;

import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;

public interface NodoAST {

    int getLinea();

    int getColumna();

    Tipo verificar(Ambito ambito, ManejadorErrores errores);

    default ResultadoC3D generarC3D(GeneradorC3D generador) {
        throw new UnsupportedOperationException(
                "Generación de C3D pendiente (Fase 3): " + getClass().getSimpleName());
    }
}
