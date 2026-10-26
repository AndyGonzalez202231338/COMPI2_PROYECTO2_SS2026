package com.proyecto1.semantico.ast.cuadruplas;

/** destino = objeto.campo. El campo va por NOMBRE, no por offset numérico.
 *  tipoCampo es opcional: "flotante", "entero", "cadena", etc.
 *  Si es null el backend no puede inferir el tipo y lo trata como entero.
 *  esPuntero indica si el campo es un arreglo dinámico (puntero en heap) o un arreglo inline (Y?).
 *  claseObjeto es opcional: el nombre de la clase declarada del objeto, para resolver el
 *  offset del campo sin ambiguedad cuando dos clases distintas tienen un campo con el
 *  mismo nombre en posiciones diferentes (ej. "tamanio" en Pila vs en Cola).
 * */
public record CuadruplaCampoCarga(String objeto, String campo, String destino, String tipoCampo,
                                  boolean esPuntero, String claseObjeto) implements Cuadrupla {

    // Compatibilidad: sin tipo ni puntero ni clase.
    public CuadruplaCampoCarga(String objeto, String campo, String destino) {
        this(objeto, campo, destino, null, false, null);
    }

    // Compatibilidad: sin puntero ni clase.
    public CuadruplaCampoCarga(String objeto, String campo, String destino, String tipoCampo) {
        this(objeto, campo, destino, tipoCampo, false, null);
    }

    // Compatibilidad: sin clase.
    public CuadruplaCampoCarga(String objeto, String campo, String destino, String tipoCampo, boolean esPuntero) {
        this(objeto, campo, destino, tipoCampo, esPuntero, null);
    }

    @Override
    public String toStringLegible() {
        String base = destino + " = " + objeto + "." + campo;
        if (claseObjeto != null) base += " [" + claseObjeto + "]";
        return tipoCampo != null ? base + "  [" + tipoCampo + "]" : base;
    }

    @Override
    public <T> T aceptar(VisitanteCuadrupla<T> visitante) { return visitante.visitar(this); }
}