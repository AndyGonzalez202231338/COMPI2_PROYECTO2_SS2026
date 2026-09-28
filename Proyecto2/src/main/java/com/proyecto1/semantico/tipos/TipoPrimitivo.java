package com.proyecto1.semantico.tipos;

/**
 * Tipos primitivos. Se comparten entre Y y Zetariano usando un solo enum porque
 * semánticamente "entero"(Y) == "int"(Z), "flotante" == "double", "caracter" == "char",
 * "cadena" == "String" y "bool" == "boolean": la validación de compatibilidad es la
 * misma sin importar con qué palabra se haya escrito en el código fuente.
 */
public enum TipoPrimitivo implements Tipo {
    ENTERO("entero"),
    FLOTANTE("flotante"),
    CARACTER("caracter"),
    CADENA("cadena"),
    BOOL("bool"),
    VOID("void"),
    NULO("null"),          // tipo del literal "null" de Zetariano
    DESCONOCIDO("<?>");    // comodín para no propagar errores en cascada

    private final String nombre;

    TipoPrimitivo(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public String nombre() {
        return nombre;
    }

    @Override
    public boolean esNumerico() {
        return this == ENTERO || this == FLOTANTE;
    }

    @Override
    public boolean esVoid() {
        return this == VOID;
    }

    @Override
    public boolean esDesconocido() {
        return this == DESCONOCIDO;
    }
}