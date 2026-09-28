package com.proyecto1.semantico.tipos;

/**
 * Contrato mínimo que debe cumplir cualquier tipo del lenguaje (primitivo, arreglo,
 * estructura de Y? o clase de Zetariano). Las implementaciones concretas
 * (TipoPrimitivo, TipoArreglo, TipoEstructura, TipoClase) y la clase de utilidades
 * de compatibilidad (Tipos) se agregan en la siguiente parte de la Fase 2.
 *
 * Se define ya (y no hasta la Parte 2) porque {@link com.proyecto1.semantico.tabla.Simbolo}
 * necesita guardar el tipo de cada símbolo desde el día uno.
 */
public interface Tipo {

    /** Nombre legible del tipo, usado en mensajes de error ("entero", "MiEstructura", "int[]"). */
    String nombre();

    default boolean esNumerico() { return false; }

    default boolean esArreglo() { return false; }

    /** true para estructuras (Y?) y clases (Zetariano): tipos compuestos con miembros. */
    default boolean esCompuesto() { return false; }

    default boolean esVoid() { return false; }

    /**
     * Tipo comodín usado para no propagar errores en cascada: cuando ya se reportó un
     * error semántico sobre una expresión, se le asigna DESCONOCIDO como su tipo, y las
     * validaciones posteriores tratan a DESCONOCIDO como compatible con cualquier cosa
     * (para no generar 10 errores más a partir del primero).
     */
    default boolean esDesconocido() { return false; }
}