package com.proyecto1.semantico.tipos;

public final class Tipos {

    private Tipos() {}

    private static boolean esDesconocido(Tipo t) {
        return t == null || t.esDesconocido();
    }

    /**
     * ¿Se puede asignar un valor de tipo "origen" a una variable/campo/parámetro de
     * tipo "destino"? (declaracionVariable con inicializador, asignacion, argumento de
     * llamada, retornar/return).
     */
    public static boolean esAsignable(Tipo destino, Tipo origen) {
        if (esDesconocido(destino) || esDesconocido(origen)) return true;
        if (destino.equals(origen)) return true;

        // Promoción implícita numérica: entero -> flotante (no al revés).
        if (destino == TipoPrimitivo.FLOTANTE && origen == TipoPrimitivo.ENTERO) return true;

        // null solo es asignable a tipos compuestos o arreglos (nunca a primitivos).
        //if (origen == TipoPrimitivo.NULO && (destino.esCompuesto() || destino.esArreglo())) return true;

        if (origen == TipoPrimitivo.NULO) return true;   // lenguaje propio: null asignable a cualquier tipo

        // Arreglos: se exige tipo base idéntico y misma cantidad de niveles.
        if (destino instanceof TipoArreglo da && origen instanceof TipoArreglo oa) {
            return esAsignable(da.getBase(), oa.getBase());
        }

        return false;
    }

    /**
     * Tipo resultante de una operación aritmética (+, -, *, /, %). Devuelve null si la
     * combinación no es válida (el llamador reporta el error con el mensaje que mejor
     * le convenga, ya que "+" con cadenas puede ser válido para concatenar mientras que
     * "-", "*", "/", "%" con cadenas nunca lo son).
     *
     * @param permitirConcatenacionCadena true para '+', false para -,*,/,%
     */
    public static Tipo resultadoAritmetico(Tipo a, Tipo b, boolean permitirConcatenacionCadena) {
        if (esDesconocido(a) || esDesconocido(b)) return TipoPrimitivo.DESCONOCIDO;

        if (permitirConcatenacionCadena && (a == TipoPrimitivo.CADENA || b == TipoPrimitivo.CADENA)) {
            return TipoPrimitivo.CADENA;
        }
        if (a.esNumerico() && b.esNumerico()) {
            return (a == TipoPrimitivo.FLOTANTE || b == TipoPrimitivo.FLOTANTE)
                    ? TipoPrimitivo.FLOTANTE
                    : TipoPrimitivo.ENTERO;
        }
        return null; // combinación inválida
    }

    /** ¿Los operandos de == / != son comparables entre sí? */
    public static boolean esComparableIgualdad(Tipo a, Tipo b) {
        if (esDesconocido(a) || esDesconocido(b)) return true;
        if (a.equals(b)) return true;
        if (a.esNumerico() && b.esNumerico()) return true;
        // null contra cualquier compuesto/arreglo (y viceversa).
        if (a == TipoPrimitivo.NULO && (b.esCompuesto() || b.esArreglo())) return true;
        if (b == TipoPrimitivo.NULO && (a.esCompuesto() || a.esArreglo())) return true;
        return false;
    }

    /** ¿Los operandos de <, >, <=, >= son comparables entre sí? (solo numéricos). */
    public static boolean esComparableOrden(Tipo a, Tipo b) {
        if (esDesconocido(a) || esDesconocido(b)) return true;
        return a.esNumerico() && b.esNumerico();
    }

    /** ¿t puede usarse como condición de si/mientras/para/elegir (debe ser bool)? */
    public static boolean esBooleano(Tipo t) {
        return esDesconocido(t) || t == TipoPrimitivo.BOOL;
    }

    /** ¿t admite ++ / -- (prefijo o postfijo)? Solo numéricos. */
    public static boolean admiteIncrementoDecremento(Tipo t) {
        return esDesconocido(t) || t.esNumerico();
    }

    /** ¿t es un tipo válido para ser índice de arreglo (Y?/Z: siempre entero)? */
    public static boolean esIndiceValido(Tipo t) {
        return esDesconocido(t) || t == TipoPrimitivo.ENTERO;
    }
}