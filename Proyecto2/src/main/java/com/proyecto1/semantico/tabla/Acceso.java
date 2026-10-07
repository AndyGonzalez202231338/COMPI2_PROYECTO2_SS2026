package com.proyecto1.semantico.tabla;

import com.proyecto1.semantico.errores.ManejadorErrores;

/**
 * Reglas de encapsulamiento para miembros de clases de Zetariano.
 *  public    -> sin restriccion
 *  default   -> mismo paquete; en este proyecto todas las clases estan en el mismo, asi que equivale a public
 *  protected -> la clase duena y sus subclases
 *  private   -> solo la clase duena
 * "claseDesde" es la clase donde esta escrito el codigo que accede. Es null cuando el
 * acceso se hace desde fuera de cualquier clase (por ejemplo desde PigLatin): en ese caso solo pasan public y default.
 */
public final class Acceso {

    private Acceso() {}

    public static boolean permite(Simbolo miembro, Simbolo claseDesde) {
        Simbolo duena = miembro.getClaseDuena();
        if (duena == null) return true; // simbolo sin clase (Y?, PigLatin): sin restricciones

        return switch (miembro.getModificador()) {
            case PUBLIC, DEFAULT -> true;
            case PRIVATE -> claseDesde != null && claseDesde.getNombre().equals(duena.getNombre());
            case PROTECTED -> claseDesde != null && claseDesde.esSubclaseDe(duena);
        };
    }

    // Igual que permite() pero reporta el error. Devuelve true si el acceso es valido.
    // El ambito se usa para saber desde que clase se accede.
    public static boolean verificar(Simbolo miembro, Ambito desde, ManejadorErrores errores,
                                    int linea, int columna) {
        Simbolo claseDesde = (desde == null) ? null : desde.claseActual();
        if (permite(miembro, claseDesde)) return true;

        String duena = miembro.getClaseDuena().getNombre();
        String quien = describir(miembro);
        String mensaje = switch (miembro.getModificador()) {
            case PRIVATE -> quien + " es private en '" + duena + "'; solo se puede usar dentro de esa clase";
            case PROTECTED -> quien + " es protected en '" + duena
                    + "'; solo se puede usar dentro de esa clase y sus subclases";
            default -> quien + " no es accesible desde aqui";
        };
        errores.reportar(linea, columna, mensaje);
        return false;
    }

    // "El atributo 'edad'", "El metodo 'hablar(int)'", "El constructor 'Animal(String)'"
    private static String describir(Simbolo m) {
        return switch (m.getCategoria()) {
            case ATRIBUTO -> "El atributo '" + m.getNombre() + "'";
            case METODO -> "El metodo '" + m.firmaLegible() + "'";
            case CONSTRUCTOR -> "El constructor '" + m.firmaLegible() + "'";
            default -> "'" + m.getNombre() + "'";
        };
    }

    // Orden de visibilidad para validar sobrescrituras: un metodo que sobrescribe no
    // puede ser menos visible que el original (regla de Java).
    public static int rango(ModificadorAcceso m) {
        return switch (m) {
            case PRIVATE -> 0;
            case DEFAULT -> 1;
            case PROTECTED -> 2;
            case PUBLIC -> 3;
        };
    }
}
