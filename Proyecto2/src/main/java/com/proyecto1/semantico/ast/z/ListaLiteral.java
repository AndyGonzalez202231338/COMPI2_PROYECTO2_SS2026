package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.List;

/** LLAVEIZQ initializerList? LLAVEDER (#primarioListaLiteral): "{1, 2, 3}". */
public final class ListaLiteral extends NodoZ implements ExpresionZ {

    private final List<ExpresionZ> elementos;

    /** TipoArreglo(tipoElemento), cacheado por verificar(). */
    private TipoArreglo tipoArreglo;

    public ListaLiteral(List<ExpresionZ> elementos, int linea, int columna) {
        super(linea, columna);
        this.elementos = elementos;
    }

    public List<ExpresionZ> getElementos() { return elementos; }
    public TipoArreglo getTipoArreglo()    { return tipoArreglo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tipoElemento = null;
        for (ExpresionZ e : elementos) {
            Tipo t = e.verificar(ambito, errores);
            if (tipoElemento == null) tipoElemento = t;
            else if (!Tipos.esAsignable(tipoElemento, t) && !Tipos.esAsignable(t, tipoElemento))
                errores.reportar(e.getLinea(), e.getColumna(),
                        "Elemento de lista incompatible: " + t.nombre() + " vs " + tipoElemento.nombre());
        }
        if (tipoElemento == null) tipoElemento = TipoPrimitivo.DESCONOCIDO;
        tipoArreglo = new TipoArreglo(tipoElemento, elementos.size());
        return tipoArreglo;
    }

    /**
     * Emite el arreglo en construcción en FLAT (los literales tienen dimensiones
     * conocidas en compile-time):
     *   (newarr, tipoElementoBase, [N], t): un solo malloc del bloque
     *       completo.
     *   Por cada elemento en orden: ([]=, t, i, v)}con índice constante.
     * Devuelve temporal(t, tipoArreglo)
     *
     * En Z siempre es 1D (los literales multidimensionales se escriben como
     * literales anidados)
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        Tipo base = (tipoArreglo != null) ? tipoArreglo.getBase() : TipoPrimitivo.DESCONOCIDO;
        String descriptor = descriptorDeTipo(base);

        List<String> tamanos = List.of(String.valueOf(elementos.size()));
        String arr = generador.nuevoTemporal();
        generador.emitirNewArray(descriptor, tamanos, arr);

        for (int i = 0; i < elementos.size(); i++) {
            ResultadoC3D v = elementos.get(i).generarC3D(generador);
            generador.emitirGuardarIndice(arr, String.valueOf(i), v.getLugar());
        }

        Tipo tipoResultado = (tipoArreglo != null) ? tipoArreglo : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(arr, tipoResultado);
    }

    /** Descriptor C del tipo base: "int", "char*", "Persona*", etc. */
    private static String descriptorDeTipo(Tipo t) {
        if (t == null) return "int";
        if (t == TipoPrimitivo.ENTERO)   return "int";
        if (t == TipoPrimitivo.FLOTANTE) return "double";
        if (t == TipoPrimitivo.CARACTER) return "char";
        if (t == TipoPrimitivo.CADENA)   return "char*";
        if (t == TipoPrimitivo.BOOL)     return "int";
        if (t instanceof TipoClase tc)      return tc.getDefinicion().getNombre() + "*";
        if (t instanceof TipoEstructura te) return te.getDefinicion().getNombre() + "*";
        if (t instanceof TipoArreglo ta)    return descriptorDeTipo(ta.getBase()) + "*";
        return "int";
    }
}