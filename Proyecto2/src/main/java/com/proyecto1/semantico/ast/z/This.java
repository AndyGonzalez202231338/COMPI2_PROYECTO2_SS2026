package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoClase;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Palabra reservada "this": referencia al objeto actual.
 * Casos que cubre (todos llegan como This dentro de otros nodos):
 *   this.nombre = valor;          -> Asignacion(AccesoCampo(This, "nombre"), valor)
 *   this.metodo();                -> Llamada(AccesoCampo(This, "metodo"), [])
 *   return this;                  -> Retorno(This)
 *   otro.setDueno(this);          -> This como argumento
 */
public final class This extends NodoZ implements ExpresionZ {

    public This(int linea, int columna) {
        super(linea, columna);
    }

    // El tipo de "this" es la clase que lo contiene. Se sube por la cadena de ambitos
    // hasta encontrar el AmbitoClase (bloque -> funcion -> clase).
    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoClase ambClase = buscarAmbitoClase(ambito);
        if (ambClase == null) {
            errores.reportar(linea, columna, "'this' solo puede usarse dentro de una clase");
            return TipoPrimitivo.DESCONOCIDO;
        }
        return new TipoClase(ambClase.getSimboloContenedor());
    }

    // Se recalcula el tipo desde el ambito activo del generador (mismo criterio que
    // Identificador). Si no hay ambito se degrada a DESCONOCIDO, pero el lugar sigue
    // siendo "this", que es lo que importa para cargar/guardar campos.
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        Tipo tipo = TipoPrimitivo.DESCONOCIDO;
        AmbitoClase ambClase = buscarAmbitoClase(generador.getAmbito());
        if (ambClase != null) {
            tipo = new TipoClase(ambClase.getSimboloContenedor());
        }
        return ResultadoC3D.valor("this", tipo);
    }

    private static AmbitoClase buscarAmbitoClase(Ambito ambito) {
        Ambito a = ambito;
        while (a != null && !(a instanceof AmbitoClase)) {
            a = a.getPadre();
        }
        return (a instanceof AmbitoClase ac) ? ac : null;
    }
}
