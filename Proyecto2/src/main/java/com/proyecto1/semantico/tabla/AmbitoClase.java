package com.proyecto1.semantico.tabla;

/**
 * Ambito usado mientras se procesan atributos/constructores/metodos de una CLASE de Zetariano.
 * Herencia: la clase padre no se guarda como un segundo AmbitoClase sino a traves del
 * simbolo de la clase (getSimboloContenedor().getClasePadre()). Los AmbitoClase de las
 * clases hermanas no se conservan despues de cargarlas, pero sus simbolos si, asi que
 * el simbolo es el unico enlace que siempre esta disponible.
 */
public class AmbitoClase extends AmbitoContenedor {

    public AmbitoClase(Ambito padre, Simbolo simboloClase) {
        super(padre, simboloClase);
    }

    // Clase padre de esta clase (null si no hereda o si todavia no se enlazo).
    public Simbolo getClasePadre() {
        return simboloContenedor.getClasePadre();
    }

    /**
     * Resolucion por nombre simple dentro de un metodo, por ejemplo "return edad;":
     *  1) miembros propios de la clase
     *  2) atributos heredados, subiendo por la cadena de herencia
     *  3) ambito global
     * Un atributo private del padre se encuentra igual; quien lo usa (Identificador)
     * reporta el error de acceso. Asi el mensaje es "es private" y no "no declarada".
     * @param nombre
     * @return
     */
    @Override
    public Simbolo resolver(String nombre) {
        Simbolo propio = simbolos.obtener(nombre);
        if (propio != null) return propio;

        Simbolo padreClase = simboloContenedor.getClasePadre();
        if (padreClase != null) {
            Simbolo heredado = padreClase.buscarMiembro(nombre);
            if (heredado != null && heredado.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                return heredado;
            }
        }
        return (padre != null) ? padre.resolver(nombre) : null;
    }

    @Override
    public String descripcion() {
        return "clase '" + simboloContenedor.getNombre() + "'";
    }
}