package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.List;

/**
 * NEW tipoBase (CORIZQ expression CORDER)+ (#primarioArregloConTamano):
 * "new int[5]" o "new int[3][3]". Los tamaños son expresiones en runtime.
 */
public final class NuevoArregloConTamano extends NodoZ implements ExpresionZ {

    private final NodoTipoRef tipoElemento;
    private final List<ExpresionZ> tamanos;

    public NuevoArregloConTamano(NodoTipoRef tipoElemento, List<ExpresionZ> tamanos,
                                 int linea, int columna) {
        super(linea, columna);
        this.tipoElemento = tipoElemento;
        this.tamanos = tamanos;
    }

    public NodoTipoRef getTipoElemento() { return tipoElemento; }
    public List<ExpresionZ> getTamanos()  { return tamanos; }
    public int getDimensiones()           { return tamanos.size(); }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo base = tipoElemento.resolver(ambito, errores);
        for (ExpresionZ tam : tamanos) {
            Tipo tTam = tam.verificar(ambito, errores);
            if (!Tipos.esIndiceValido(tTam))
                errores.reportar(tam.getLinea(), tam.getColumna(),
                        "Tamaño de arreglo debe ser entero, se recibió " + tTam.nombre());
        }
        // Construir el TipoArreglo de dentro hacia fuera, marcando longitud cuando
        // el tamaño es un literal. Ejemplos:
        //   new int[3][4]   -> TipoArreglo(TipoArreglo(ENTERO, 4), 3)
        //   new int[n][3]   -> TipoArreglo(TipoArreglo(ENTERO, 3), -1)
        //   new int[3][n]   -> TipoArreglo(TipoArreglo(ENTERO, -1), 3)
        //   new int[n][m]   -> TipoArreglo(TipoArreglo(ENTERO, -1), -1)
        for (int i = tamanos.size() - 1; i >= 0; i--) {
            ExpresionZ tam = tamanos.get(i);
            int longitud = TipoArreglo.LONGITUD_DESCONOCIDA;
            if (tam instanceof Literal lit && lit.getCategoria() == CategoriaLiteral.ENTERO) {
                longitud = ((Long) lit.getValor()).intValue();
            }
            base = new TipoArreglo(base, longitud);
        }
        return base;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // 1) Evaluar cada tamaño en orden, guardar sus lugares.
        List<ResultadoC3D> tamRes = new ArrayList<>();
        for (ExpresionZ tam : tamanos) {
            tamRes.add(tam.generarC3D(generador));
        }

        Tipo tipoResultado = verificar(null, null);
        TipoArreglo tipoArr = (tipoResultado instanceof TipoArreglo ta) ? ta : null;

        // 2) Rama FLAT si es aplanable.
        if (tipoArr != null && tipoArr.esAplanable()) {
            List<String> tamanosStr = new ArrayList<>();
            for (ResultadoC3D tam : tamRes) tamanosStr.add(tam.getLugar());

            String t = generador.nuevoTemporal();
            generador.emitirNewArray(tipoElemento.getNombreBase(), tamanosStr, t);
            return ResultadoC3D.temporal(t, tipoResultado);
        }

        // 3) Rama JAGGED.
        String resultado = allocArregloJagged(generador, tamRes, 0, tipoElemento.getNombreBase());
        return ResultadoC3D.temporal(resultado, tipoResultado);
    }

    /**
     * Asigna recursivamente el arreglo del nivel con los tamaños. Devuelve el temporal con la referencia al arreglo creado.
     *   Última dimensión: newarr directo (sin bucle), con el tipo
     *       escalar como descriptor.
     *   Niveles anteriores: newarr del tipo puntero correspondiente
     *       ("int*" para 2D, "int**" para 3D, ...) y un bucle que rellena cada
     *       slot llamándose recursivamente.
     */
    private String allocArregloJagged(GeneradorC3D g, List<ResultadoC3D> tamRes,
                                      int nivel, String tipoBaseEscalar) {
        ResultadoC3D tam = tamRes.get(nivel);
        boolean esUltimaDimension = (nivel == tamRes.size() - 1);

        // Descriptor del tipo de los elementos de ESTE nivel:
        //   - Última dimensión: tipo escalar ("int").
        //   - Nivel intermedio: tipo puntero con tantos "*" como dimensiones restantes.
        //     Ej. int[n][m] -> nivel 0 usa "int*"; int[a][b][c] -> nivel 0 "int**", nivel 1 "int*".
        String descriptor = esUltimaDimension
                ? tipoBaseEscalar
                : tipoBaseEscalar + "*".repeat(tamRes.size() - nivel - 1);

        String arr = g.nuevoTemporal();
        g.emitirNewArray(descriptor, tam.getLugar(), arr);

        if (esUltimaDimension) {
            return arr;   // última dimensión: nada que rellenar
        }

        // Bucle i = 0; i < tam; i++: arr[i] = allocArregloJagged(nivel+1)
        String i = g.nuevoTemporal();
        g.emitirAsignacion("0", i);

        String L0 = g.nuevaEtiqueta();
        String L1 = g.nuevaEtiqueta();
        g.emitirEtiqueta(L0);

        String cond = g.nuevoTemporal();
        g.emitirBinaria("<", i, tam.getLugar(), cond);
        g.emitirIfFalse(cond, L1);

        String inner = allocArregloJagged(g, tamRes, nivel + 1, tipoBaseEscalar);
        g.emitirGuardarIndice(arr, i, inner);

        String next = g.nuevoTemporal();
        g.emitirBinaria("+", i, "1", next);
        g.emitirAsignacion(next, i);
        g.emitirGoto(L0);
        g.emitirEtiqueta(L1);

        return arr;
    }
}