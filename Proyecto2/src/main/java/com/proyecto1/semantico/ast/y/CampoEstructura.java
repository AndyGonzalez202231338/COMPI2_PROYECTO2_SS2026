package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.AmbitoEstructura;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;

import java.util.List;

public final class CampoEstructura extends NodoY {

    private final NodoTipoRef tipo;
    private final String nombre;
    private final List<Integer> tamanosArreglo;

    public CampoEstructura(NodoTipoRef tipo, String nombre, List<Integer> tamanosArreglo, int linea, int columna) {
        super(linea, columna);
        this.tipo = tipo;
        this.nombre = nombre;
        this.tamanosArreglo = tamanosArreglo;
    }

    public NodoTipoRef getTipo() { return tipo; }
    public String getNombre() { return nombre; }
    public List<Integer> getTamanosArreglo() { return tamanosArreglo; }

    public void verificar(AmbitoEstructura amb, ManejadorErrores errores) {
        Tipo t = tipo.resolver(amb, errores);

        if (!tamanosArreglo.isEmpty()) {
            // Y? solo admite arreglos de un nivel (según especificación)
            if (tamanosArreglo.size() > 1)
                errores.reportar(linea, columna, "Y solo admite arreglos de un nivel en campos");
            t = new TipoArreglo(t, tamanosArreglo.get(0));
        }

        Simbolo s = new Simbolo(nombre, CategoriaSimbolo.CAMPO, t, linea, columna);
        if (!tamanosArreglo.isEmpty()) s.getTamanosArreglo().addAll(tamanosArreglo);

        if (!amb.declararMiembro(s))
            errores.reportar(linea, columna, "Campo duplicado: '" + nombre + "'");
    }
}