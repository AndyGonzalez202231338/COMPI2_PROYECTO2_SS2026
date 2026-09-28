package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoEstructura;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoEstructura;

import java.util.List;

/** Una (#estructuraDef): "estructura Nombre: campo*". */
public final class Estructura extends NodoY {

    private final String nombre;
    private final List<CampoEstructura> campos;

    public Estructura(String nombre, List<CampoEstructura> campos, int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
        this.campos = campos;
    }

    public String getNombre() {
        return nombre;
    }

    public List<CampoEstructura> getCampos() {
        return campos;
    }

    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Simbolo simbolo = ambito.ambitoGlobal().resolverLocal(nombre);
        AmbitoEstructura amb = new AmbitoEstructura(ambito, simbolo);
        for (CampoEstructura campo : campos) {
            campo.verificar(amb, errores);
        }
        return new TipoEstructura(simbolo);
    }
}
