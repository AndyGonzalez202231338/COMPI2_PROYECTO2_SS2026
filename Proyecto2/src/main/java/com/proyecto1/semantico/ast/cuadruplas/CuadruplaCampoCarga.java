package com.proyecto1.semantico.ast.cuadruplas;

/** destino = objeto.campo. El campo va por NOMBRE, no por offset numérico.
 *  tipoCampo es opcional: "flotante", "entero", "cadena", etc.
 *  Si es null el backend no puede inferir el tipo y lo trata como entero.
 *  esPuntero indica si el campo es un arreglo dinámico (puntero en heap) o un arreglo inline (Y?).
 * */
public record CuadruplaCampoCarga(String objeto, String campo, String destino, String tipoCampo, boolean esPuntero) implements Cuadrupla {

    // Compatibilidad: sin tipo ni puntero (se comporta como antes).
    public CuadruplaCampoCarga(String objeto, String campo, String destino) {
        this(objeto, campo, destino, null, false);
    }

    // Compatibilidad: sin puntero.
    public CuadruplaCampoCarga(String objeto, String campo, String destino, String tipoCampo) {
        this(objeto, campo, destino, tipoCampo, false);
    }

    @Override
    public String toStringLegible() {
        String base = destino + " = " + objeto + "." + campo;
        return tipoCampo != null ? base + "  [" + tipoCampo + "]" : base;
    }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}