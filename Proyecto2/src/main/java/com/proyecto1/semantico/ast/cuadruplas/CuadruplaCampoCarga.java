package com.proyecto1.semantico.ast.cuadruplas;

/** destino = objeto.campo. El campo va por NOMBRE, no por offset numérico.
 *  tipoCampo es opcional: "flotante", "entero", "cadena", etc.
 *  Si es null el backend no puede inferir el tipo y lo trata como entero.
 * */
public record CuadruplaCampoCarga(String objeto, String campo, String destino, String tipoCampo) implements Cuadrupla {

    // Compatibilidad: sin tipo (se comporta como antes).
    public CuadruplaCampoCarga(String objeto, String campo, String destino) {
        this(objeto, campo, destino, null);
    }

    @Override
    public String toStringLegible() {
        String base = destino + " = " + objeto + "." + campo;
        return tipoCampo != null ? base + "  [" + tipoCampo + "]" : base;
    }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}
