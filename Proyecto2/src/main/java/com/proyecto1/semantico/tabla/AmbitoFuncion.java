package com.proyecto1.semantico.tabla;

import com.proyecto1.semantico.tipos.Tipo;

/**
 * Ámbito de una función de Y?, o de un método/constructor de Zetariano. Aquí viven los
 * parámetros y las variables locales declaradas directo en el cuerpo (las que están
 * dentro de un si/para/mientras además viven en un AmbitoBloque hijo de este).
 *
 * Guarda el tipo de retorno esperado para poder validar cada "retornar"/"return" contra
 * él (incluye el caso "sin tipo" = void, representado con Tipo.esVoid() == true).
 */
public class AmbitoFuncion extends Ambito {

    private final Simbolo simboloFuncion;
    private boolean tuvoRetorno = false; // ¿al menos un camino ya pasó por un retornar/return?

    public AmbitoFuncion(Ambito padre, Simbolo simboloFuncion) {
        super(padre);
        this.simboloFuncion = simboloFuncion;
    }

    public Simbolo getSimboloFuncion() {
        return simboloFuncion;
    }

    public Tipo getTipoRetorno() {
        return simboloFuncion.getTipo();
    }

    public boolean esVoid() {
        return simboloFuncion.getTipo() == null || simboloFuncion.getTipo().esVoid();
    }

    public boolean isTuvoRetorno() {
        return tuvoRetorno;
    }

    public void marcarTuvoRetorno() {
        this.tuvoRetorno = true;
    }

    @Override
    public String descripcion() {
        return simboloFuncion.descripcionCorta();
    }
}