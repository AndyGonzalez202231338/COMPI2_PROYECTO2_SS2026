package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.List;

/** primaryExpression CORIZQ expression CORDER (#primarioIndice): "arreglo[indice]". */
public final class Indice extends NodoZ implements ExpresionZ {

    private final ExpresionZ arreglo;
    private final ExpresionZ indice;

    /** Cacheado por verificar() para que generarC3D pueda decidir flat/jagged sin navegar el ámbito. */
    private TipoArreglo tipoArregloBase;
    private Tipo tipoElemento;

    public Indice(ExpresionZ arreglo, ExpresionZ indice, int linea, int columna) {
        super(linea, columna);
        this.arreglo = arreglo;
        this.indice = indice;
    }

    public ExpresionZ getArreglo() { return arreglo; }
    public ExpresionZ getIndice()  { return indice;  }
    public TipoArreglo getTipoArregloBase() { return tipoArregloBase; }
    public Tipo getTipoElemento() { return tipoElemento; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tArr = arreglo.verificar(ambito, errores);
        Tipo tIdx = indice.verificar(ambito, errores);

        if (!Tipos.esIndiceValido(tIdx))
            errores.reportar(indice.getLinea(), indice.getColumna(),
                    "El índice debe ser entero, se recibió " + tIdx.nombre());

        if (!(tArr instanceof TipoArreglo ta)) {
            if (!tArr.esDesconocido())
                errores.reportar(linea, columna, "Se indexó algo que no es arreglo: " + tArr.nombre());
            return TipoPrimitivo.DESCONOCIDO;
        }

        // Chequeo de rango para arreglos de tamaño fijo (Y y PigLatin).
        if (arreglo instanceof Identificador idArr) {
            Simbolo sArr = ambito.resolver(idArr.getNombre());
            if (sArr != null && sArr.esArregloDeTamanoFijo()
                    && indice instanceof Literal litIdx
                    && litIdx.getCategoria() == CategoriaLiteral.ENTERO) {
                int idx = ((Long) litIdx.getValor()).intValue();
                int size = sArr.getTamanosArreglo().get(0);
                if (idx < 0 || idx >= size) {
                    errores.reportar(indice.getLinea(), indice.getColumna(),
                            "Índice " + idx + " fuera de rango (tamaño " + size + ")");
                }
            }
        }

        this.tipoArregloBase = ta;
        this.tipoElemento = ta.getBase();
        return tipoElemento;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // 1) Detectar la cadena de índices: arr[i][j][k] es Indice(Indice(Indice(arr,i),j),k).
        List<ExpresionZ> indices = new ArrayList<>();
        ExpresionZ actual = this;
        while (actual instanceof Indice ind) {
            indices.add(0, ind.getIndice());  // prepend: el externo va al final
            actual = ind.getArreglo();
        }
        ExpresionZ base = actual;

        // 2) C3D de la base una sola vez.
        ResultadoC3D baseRes = base.generarC3D(generador);

        // 3) 1D o no aplanable → ruta composicional (jagged).
        boolean puedeAplanar = tipoArregloBase != null
                && tipoArregloBase.esAplanable()
                && indices.size() >= 2;

        if (!puedeAplanar) {
            String lugarActual = baseRes.getLugar();
            for (ExpresionZ idx : indices) {
                ResultadoC3D idxRes = idx.generarC3D(generador);
                String t = generador.nuevoTemporal();
                generador.emitirCargaIndice(lugarActual, idxRes.getLugar(), t);
                lugarActual = t;
            }
            Tipo tipo = (tipoElemento != null) ? tipoElemento : TipoPrimitivo.DESCONOCIDO;
            return ResultadoC3D.temporal(lugarActual, tipo);
        }

        // 4) FLAT: aplanar todos los índices en uno solo.
        List<Integer> dims = tipoArregloBase.tamanosCompletos();
        // dims.size() == indices.size(); dims[0] no participa del aplanado.

        // 4.a) Evaluar cada índice en orden (efectos laterales una sola vez).
        List<String> idxLugares = new ArrayList<>();
        for (ExpresionZ idx : indices) {
            idxLugares.add(idx.generarC3D(generador).getLugar());
        }

        // 4.b) Calcular "pesos": peso[k] = d(k+1) * d(k+2) * ... * d(n-1).
        //      El último peso es 1.
        int n = indices.size();
        long[] pesos = new long[n];
        pesos[n - 1] = 1;
        for (int k = n - 2; k >= 0; k--) {
            int dInterna = dims.get(k + 1);
            pesos[k] = pesos[k + 1] * dInterna;   // todas las dInternas son literales (esAplanable)
        }

        // 4.c) Construir el índice aplanado con cuádruplas.
        String flat = construirFlat(generador, idxLugares, pesos);

        // 4.d) Emitir UNA SOLA cuádrupla =[] con el índice aplanado.
        String t = generador.nuevoTemporal();
        generador.emitirCargaIndice(baseRes.getLugar(), flat, t);
        Tipo tipo = (tipoElemento != null) ? tipoElemento : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(t, tipo);
    }

    /**
     * Construye el índice aplanado emitiendo cuádruplas. Optimización: los
     * términos con peso 1 no generan multiplicación (el índice se usa tal cual).
     * Los términos con peso > 1 emiten una multiplicación. La suma acumulada se
     * construye en cadena.
     */
    private String construirFlat(GeneradorC3D generador, List<String> idxLugares, long[] pesos) {
        int n = idxLugares.size();
        String acumulado = null;

        for (int k = 0; k < n; k++) {
            String idx = idxLugares.get(k);
            long peso = pesos[k];

            // Término: idx * peso, o solo idx si peso == 1.
            String termino;
            if (peso == 1) {
                termino = idx;
            } else {
                String t = generador.nuevoTemporal();
                generador.emitirBinaria("*", idx, String.valueOf(peso), t);
                termino = t;
            }

            if (acumulado == null) {
                acumulado = termino;
            } else {
                String t = generador.nuevoTemporal();
                generador.emitirBinaria("+", acumulado, termino, t);
                acumulado = t;
            }
        }

        // n >= 2 garantizado por la rama flat.
        return acumulado;
    }
}