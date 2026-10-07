package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Palabra reservada "super": el objeto actual visto como su clase padre.
 * Formas en que aparece en el AST:
 *   super(args);        -> Llamada(Super, args)                  constructor del padre
 *   super.metodo(args); -> Llamada(AccesoCampo(Super, "metodo"), args)
 *   super.campo         -> AccesoCampo(Super, "campo")
 * El tipo de super es la clase padre. Como AccesoCampo y Llamada buscan los miembros
 * sobre ese tipo, super.metodo() encuentra la version del padre aunque la clase actual
 * la sobrescriba, y el encapsulamiento se valida desde la clase actual (que es subclase,
 * asi que protected pasa y private no).
 */
public final class Super extends NodoZ implements ExpresionZ {

    // Tipo de la clase padre, cacheado por verificar() para el C3D.
    private Tipo tipoPadre = TipoPrimitivo.DESCONOCIDO;

    public Super(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Simbolo clase = ambito.claseActual();
        if (clase == null) {
            errores.reportar(linea, columna, "'super' solo puede usarse dentro de una clase");
            return TipoPrimitivo.DESCONOCIDO;
        }
        Simbolo padre = clase.getClasePadre();
        if (padre == null) {
            // Si declaro extends pero el enlace fallo, el error ya se reporto al enlazar.
            if (clase.getNombreClasePadre() == null) {
                errores.reportar(linea, columna,
                        "'super' no se puede usar en '" + clase.getNombre()
                                + "' porque no hereda de ninguna clase");
            }
            return TipoPrimitivo.DESCONOCIDO;
        }
        tipoPadre = new TipoClase(padre);
        return tipoPadre;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        return ResultadoC3D.valor("this", tipoPadre);
    }
}
