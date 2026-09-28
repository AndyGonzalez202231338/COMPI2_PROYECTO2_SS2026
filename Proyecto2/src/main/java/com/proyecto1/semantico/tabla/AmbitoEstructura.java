package com.proyecto1.semantico.tabla;

/** Ámbito usado mientras se procesan los CAMPO de una ESTRUCTURA de Y? (%estructuras). */
public class AmbitoEstructura extends AmbitoContenedor {

    public AmbitoEstructura(Ambito padre, Simbolo simboloEstructura) {
        super(padre, simboloEstructura);
    }

    @Override
    public String descripcion() {
        return "estructura '" + simboloContenedor.getNombre() + "'";
    }
}