package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * conditionalExpression (#conditionalExpressionDef) cuando trae el '?:':
 * "condicion ? siVerdadero : siFalso". No existe equivalente en Y? — es exclusivo
 * de Zetariano.
 */
public final class Ternario extends NodoZ implements ExpresionZ {

    private final ExpresionZ condicion;
    private final ExpresionZ siVerdadero;
    private final ExpresionZ siFalso;

    /**
     * Tipo resultante, cacheado por verificar() para que generarC3D no tenga que
     * reevaluar el análisis semántico (que necesita ámbito y manejador de errores).
     */
    private Tipo tipoResultado;

    public Ternario(ExpresionZ condicion, ExpresionZ siVerdadero, ExpresionZ siFalso, int linea, int columna) {
        super(linea, columna);
        this.condicion = condicion;
        this.siVerdadero = siVerdadero;
        this.siFalso = siFalso;
    }

    public ExpresionZ getCondicion()    { return condicion; }
    public ExpresionZ getSiVerdadero()  { return siVerdadero; }
    public ExpresionZ getSiFalso()      { return siFalso; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tc = condicion.verificar(ambito, errores);
        if (!Tipos.esBooleano(tc))
            errores.reportar(condicion.getLinea(), condicion.getColumna(),
                    "La condición del ternario debe ser bool");

        Tipo tv = siVerdadero.verificar(ambito, errores);
        Tipo tf = siFalso.verificar(ambito, errores);

        if (Tipos.esAsignable(tv, tf)) { tipoResultado = tv; return tv; }
        if (Tipos.esAsignable(tf, tv)) { tipoResultado = tf; return tf; }
        if (!tv.esDesconocido() && !tf.esDesconocido())
            errores.reportar(linea, columna,
                    "Ramas del ternario incompatibles: " + tv.nombre() + " vs " + tf.nombre());
        tipoResultado = TipoPrimitivo.DESCONOCIDO;
        return TipoPrimitivo.DESCONOCIDO;
    }

    /**
     * Emite el ternario con el mismo esquema de backpatching que un if/else de
     * expresión, usando UN SOLO temporal T compartido por ambas ramas:
     *   [cond]
     *   if_false c goto L_falso
     *   [siVerdadero]              -> t = v1
     *   goto L_fin
     *   L_falso:
     *   [siFalso]                  -> t = v2
     *   L_fin:
     * La razón de reutilizar t en ambas ramas: como solo una se ejecuta en
     * runtime, el mismo temporal es válido para las dos. Al final del ternario,
     * t contiene el valor de la rama que se tomó. Devuelve
     * ResultadoC3D.temporal(t, tipoResultado).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // 1) Temporal único compartido por ambas ramas.
        String t = generador.nuevoTemporal();
        String etiquetaFalso = generador.nuevaEtiqueta();
        String etiquetaFin   = generador.nuevaEtiqueta();

        // 2) Evaluar la condición y saltar a la rama falsa si es false.
        ResultadoC3D c = condicion.generarC3D(generador);
        generador.emitirIfFalse(c.getLugar(), etiquetaFalso);

        // 3) Rama verdadera: emitir su C3D y copiar el resultado a t.
        ResultadoC3D v1 = siVerdadero.generarC3D(generador);
        generador.emitirAsignacion(v1.getLugar(), t);
        generador.emitirGoto(etiquetaFin);

        // 4) Rama falsa: emitir su C3D y copiar el resultado a t.
        generador.emitirEtiqueta(etiquetaFalso);
        ResultadoC3D v2 = siFalso.generarC3D(generador);
        generador.emitirAsignacion(v2.getLugar(), t);

        // 5) Fin: t contiene el valor de la rama tomada.
        generador.emitirEtiqueta(etiquetaFin);

        Tipo tipo = (tipoResultado != null) ? tipoResultado : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(t, tipo);
    }
}