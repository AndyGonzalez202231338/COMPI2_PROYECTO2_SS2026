package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.List;

/** LLAVEIZQ (expresion (COMA expresion)*)? LLAVEDER (#primariaListaLiteral): "{1, 2, 3}". */
public final class ListaLiteral extends NodoY implements ExpresionY {

    private final List<ExpresionY> elementos;

    /** TipoArreglo(tipoElemento), cacheado por verificar(). */
    private Tipo tipoArreglo;

    public ListaLiteral(List<ExpresionY> elementos, int linea, int columna) {
        super(linea, columna);
        this.elementos = elementos;
    }

    public List<ExpresionY> getElementos() { return elementos; }
    public Tipo getTipoArreglo() { return tipoArreglo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tipoElemento = null;
        for (ExpresionY e : elementos) {
            Tipo t = e.verificar(ambito, errores);
            if (tipoElemento == null) tipoElemento = t;
            else if (!Tipos.esAsignable(tipoElemento, t) && !Tipos.esAsignable(t, tipoElemento))
                errores.reportar(e.getLinea(), e.getColumna(),
                        "Elemento de lista incompatible: "
                                + t.nombre() + " vs " + tipoElemento.nombre());
        }
        if (tipoElemento == null) tipoElemento = TipoPrimitivo.DESCONOCIDO;
        tipoArreglo = new TipoArreglo(tipoElemento, elementos.size());
        return tipoArreglo;
    }

    /**
     * Emite: pide un temporal que hará de "arreglo", luego por cada elemento genera su
     * C3D y emite ([]=, tArr, i, v) con i como literal entero. NO hay
     * cuádrupla de "reservar" ni "malloc": el arreglo es un símbolo más; Fase 4 lo
     * declarará como arreglo de tamaño {@code elementos.size()} (ver nota abajo sobre
     * TipoArreglo).
     *
     * Devuelve temporal(tArr, tipoArreglo) - es un temporal (viene de
     * nuevoTemporal()), no un valor.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String tArr = generador.nuevoTemporal();
        for (int i = 0; i < elementos.size(); i++) {
            ResultadoC3D v = elementos.get(i).generarC3D(generador);
            generador.emitirGuardarIndice(tArr, String.valueOf(i), v.getLugar());
        }
        Tipo tipo = (tipoArreglo != null) ? tipoArreglo : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(tArr, tipo);
    }
}