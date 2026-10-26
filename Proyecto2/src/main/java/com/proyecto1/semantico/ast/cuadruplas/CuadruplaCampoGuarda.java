package com.proyecto1.semantico.ast.cuadruplas;

/** objeto.campo = valor. Mismo caso especial que CuadruplaIndiceGuarda: el tercer campo es el valor.
 *  claseObjeto es opcional: ver CuadruplaCampoCarga para la explicacion del mismo campo. */
public record CuadruplaCampoGuarda(String objeto, String campo, String valor, String claseObjeto) implements Cuadrupla {

    // Compatibilidad: sin clase.
    public CuadruplaCampoGuarda(String objeto, String campo, String valor) {
        this(objeto, campo, valor, null);
    }

    @Override
    public String toStringLegible() {
        String base = objeto + "." + campo + " = " + valor;
        return claseObjeto != null ? base + "  [" + claseObjeto + "]" : base;
    }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}