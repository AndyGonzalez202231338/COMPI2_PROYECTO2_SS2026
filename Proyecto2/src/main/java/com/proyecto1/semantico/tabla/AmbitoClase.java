package com.proyecto1.semantico.tabla;

/** Ámbito usado mientras se procesan atributos/constructores/métodos de una CLASE de Zetariano. */
public class AmbitoClase extends AmbitoContenedor {

    public AmbitoClase(Ambito padre, Simbolo simboloClase) {
        super(padre, simboloClase);
    }

    @Override
    public String descripcion() {
        return "clase '" + simboloContenedor.getNombre() + "'";
    }
}