package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;

import java.util.ArrayList;
import java.util.List;

/**
 * NEW tipoBase (CORIZQ CORDER)+ LLAVEIZQ initializerList? LLAVEDER
 * (#primarioArregloConInicializador): "new int[]{1, 2, 3}" o "new int[][]{{1,2},{3,4}}".
 *
 * SIEMPRE se emite con estrategia FLAT: las dimensiones del literal anidado
 * son constantes conocidas en compile-time, así que no hay razón para jagged.
 * El literal se "aplanan" a UN solo bloque contiguo, y cada elemento hoja se
 * guarda con una cuádrupla []= usando un índice aplanado constante.
 */
public final class NuevoArregloConInicializador extends NodoZ implements ExpresionZ {

    private final NodoTipoRef tipoElemento;
    private final int dimensiones;
    private final List<ExpresionZ> elementos;

    public NuevoArregloConInicializador(NodoTipoRef tipoElemento, int dimensiones,
                                        List<ExpresionZ> elementos, int linea, int columna) {
        super(linea, columna);
        this.tipoElemento = tipoElemento;
        this.dimensiones = dimensiones;
        this.elementos = elementos;
    }

    public NodoTipoRef getTipoElemento() { return tipoElemento; }
    public int getDimensiones()           { return dimensiones; }
    public List<ExpresionZ> getElementos() { return elementos; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo base = tipoElemento.resolver(ambito, errores);
        for (ExpresionZ e : elementos) e.verificar(ambito, errores);
        // Deducción de dimensiones del literal anidado (siempre conocidas).
        List<Integer> dims = deducirDimensiones(elementos, dimensiones);
        for (int i = dims.size() - 1; i >= 0; i--) {
            base = new TipoArreglo(base, dims.get(i));
        }
        return base;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        List<Integer> dims = deducirDimensiones(elementos, dimensiones);

        // 1) Un solo newarr con todas las dimensiones (flat).
        List<String> tamanosStr = new ArrayList<>();
        for (int d : dims) tamanosStr.add(String.valueOf(d));

        String arr = generador.nuevoTemporal();
        generador.emitirNewArray(tipoElemento.getNombreBase(), tamanosStr, arr);

        // 2) Recorrer el literal anidado y emitir un []= por hoja, con índice
        //    aplanado constante (calculado en compile-time).
        int[] dimsArr = new int[dims.size()];
        for (int i = 0; i < dims.size(); i++) dimsArr[i] = dims.get(i);
        asignarFlat(generador, arr, dimsArr, 0, elementos, 0);

        Tipo tipoResultado = verificar(null, null);
        return ResultadoC3D.temporal(arr, tipoResultado);
    }

    // ---------- Helpers ----------

    /**
     * Deduce las dimensiones del literal anidado, en orden del externo al interno.
     * Lanza IllegalStateException si encuentra inconsistencias (sub-listas
     * de distinto tamaño → arreglo irregular, no soportado en flat).
     */
    private static List<Integer> deducirDimensiones(List<ExpresionZ> elems, int dimensiones) {
        List<Integer> dims = new ArrayList<>();
        dims.add(elems.size());
        if (dimensiones > 1 && !elems.isEmpty()) {
            for (ExpresionZ e : elems) {
                if (!(e instanceof NuevoArregloConInicializador sub)) {
                    throw new IllegalStateException(
                            "Arreglo multidimensional: se esperaba sub-lista, se encontró "
                                    + e.getClass().getSimpleName());
                }
                List<Integer> subDims = deducirDimensiones(sub.getElementos(), dimensiones - 1);
                if (dims.size() == 1) {
                    dims.addAll(subDims);
                } else {
                    // Chequear consistencia: todas las sub-listas deben tener las mismas dims internas.
                    for (int k = 1; k < subDims.size(); k++) {
                        if (!subDims.get(k).equals(dims.get(k))) {
                            throw new IllegalStateException(
                                    "Arreglo multidimensional irregular: sub-listas de tamaños distintos");
                        }
                    }
                }
            }
        }
        return dims;
    }

    /**
     * Recorre el literal anidado y emite una cuádrupla []= por cada hoja,
     * con el índice aplanado calculado en compile-time.
     *
     * baseOffset es el offset acumulado del sub-arreglo actual; cada
     * elemento del nivel nivel  suma i * peso donde
     * peso = dims[nivel+1] * ... * dims[n-1].
     */
    private static void asignarFlat(GeneradorC3D g, String arr, int[] dims, int nivel,
                                    List<ExpresionZ> elems, int baseOffset) {
        int weight = 1;
        for (int k = nivel + 1; k < dims.length; k++) weight *= dims[k];
        boolean esUltimaDimension = (nivel == dims.length - 1);

        for (int i = 0; i < elems.size(); i++) {
            int offsetActual = baseOffset + i * weight;
            if (esUltimaDimension) {
                ResultadoC3D v = elems.get(i).generarC3D(g);
                g.emitirGuardarIndice(arr, String.valueOf(offsetActual), v.getLugar());
            } else {
                NuevoArregloConInicializador sub = (NuevoArregloConInicializador) elems.get(i);
                asignarFlat(g, arr, dims, nivel + 1, sub.getElementos(), offsetActual);
            }
        }
    }
}