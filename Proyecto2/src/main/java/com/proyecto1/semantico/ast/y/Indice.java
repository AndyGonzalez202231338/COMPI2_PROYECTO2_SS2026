package com.proyecto1.semantico.ast.y;

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

public final class Indice extends NodoY implements ExpresionY {

    private final ExpresionY arreglo;
    private final ExpresionY indice;
    private Tipo tipoElemento;   // ya lo tenías como campo

    public Indice(ExpresionY arreglo, ExpresionY indice, int linea, int columna) {
        super(linea, columna);
        this.arreglo = arreglo;
        this.indice = indice;
    }

    public ExpresionY getArreglo() { return arreglo; }
    public ExpresionY getIndice()  { return indice; }
    public Tipo getTipoElemento()  { return tipoElemento; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tArr = arreglo.verificar(ambito, errores);
        Tipo tIdx = indice.verificar(ambito, errores);

        if (!Tipos.esIndiceValido(tIdx))
            errores.reportar(indice.getLinea(), indice.getColumna(),
                    "El índice debe ser entero, se recibió " + tIdx.nombre());

        if (!(tArr instanceof TipoArreglo ta)) {
            if (!tArr.esDesconocido())
                errores.reportar(linea, columna,
                        "Se indexó algo que no es arreglo: " + tArr.nombre());
            tipoElemento = TipoPrimitivo.DESCONOCIDO;
            return tipoElemento;
        }

        tipoElemento = ta.getBase();
        return tipoElemento;
    }

    // ========== AYUDANTES PARA APLANAMIENTO (FLATTENING) ==========

    public ExpresionY getBaseArray() {
        if (arreglo instanceof Indice parent) return parent.getBaseArray();
        return arreglo;
    }

    public List<ExpresionY> getIndices() {
        if (arreglo instanceof Indice parent) {
            List<ExpresionY> list = new ArrayList<>(parent.getIndices());
            list.add(indice);
            return list;
        }
        return List.of(indice);
    }

    // Genera la expresión C3D para el índice plano: i * dim2 + j
    public ResultadoC3D generarIndicePlano(GeneradorC3D generador) {
        List<ExpresionY> indices = getIndices();
        List<String> temps = new ArrayList<>();
        for (ExpresionY idxExpr : indices) {
            temps.add(idxExpr.generarC3D(generador).getLugar());
        }

        String flat = temps.get(0);
        Simbolo sim = null;
        ExpresionY baseArr = getBaseArray();
        if (baseArr instanceof Identificador id && generador.getAmbito() != null) {
            sim = generador.getAmbito().resolver(id.getNombre());
        }

        if (sim != null && sim.getTamanosArreglo().size() > 1) {
            List<Integer> dims = sim.getTamanosArreglo();
            for (int i = 1; i < temps.size(); i++) {
                String tMul = generador.nuevoTemporal();
                generador.emitirBinaria("*", flat, String.valueOf(dims.get(i)), tMul);
                String tAdd = generador.nuevoTemporal();
                generador.emitirBinaria("+", tMul, temps.get(i), tAdd);
                flat = tAdd;
            }
        }
        return ResultadoC3D.temporal(flat, TipoPrimitivo.ENTERO);
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        ResultadoC3D base = getBaseArray().generarC3D(generador);
        ResultadoC3D idxPlano = generarIndicePlano(generador);
        String t = generador.nuevoTemporal();
        generador.emitirCargaIndice(base.getLugar(), idxPlano.getLugar(), t);
        Tipo tipo = (tipoElemento != null) ? tipoElemento : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(t, tipo);
    }

    public void setTipoElemento(Tipo tipoElemento) {
        this.tipoElemento = tipoElemento;
    }
}