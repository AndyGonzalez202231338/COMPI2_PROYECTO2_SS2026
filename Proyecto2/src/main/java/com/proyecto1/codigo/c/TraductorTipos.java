package com.proyecto1.codigo.c;

import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Traduce un Tipo del lenguaje a su representación en C.
 *
 * <Clases y estructuras son SIEMPRE punteros.No hay distinción entre
 * "valor" y "referencia" en el C3D: un valor de tipo Persona se representa
 * como Persona*, porque:
 *   Las clases de Z viven en heap.
 *   Las estructuras de Y también, cuando se crean con new (decisión
 *       tomada enPigLatin: new de una estructura hace malloc igual que una clase).
 * Esto es lo que justifica que TraductorCuadrupla traduzca todo acceso a
 * campo con -> y nunca con .: el lado izquierdo de un acceso es
 * siempre un puntero.
 *
 * El método no valida el programa ni lanza excepciones: si el tipo no encaja en
 * ningún caso conocido (p. ej. DESCONOCIDO por errores semánticos
 * anteriores), cae a "int" como valor neutro.
 */
public final class TraductorTipos {

    private TraductorTipos() {}  // clase de utilidades, no instanciable

    /**
     * Traduce tipo a su representación en C:
     *   ENTERO -> "int"
     *   FLOTANTE -> "double"
     *   CARACTER -> "char"
     *   CADENA -> "char*"
     *   BOOL -> "int" (C no tiene bool nativo antes de C99)
     *   VOID -> "void"
     *   Cualquier otro caso (incluye DESCONOCIDO,NULO, etc.)
     *   TipoClase -> "<NombreClase>*"
     *   TipoEstructura -> "<NombreEstructura>*"
     *   TipoArreglo -> aC(tipoBase) + "*"
     */
    public static String aC(Tipo tipo) {
        if (tipo == null) return "int";

        if (tipo == TipoPrimitivo.ENTERO)   return "int";
        if (tipo == TipoPrimitivo.FLOTANTE) return "double";
        if (tipo == TipoPrimitivo.CARACTER) return "char";
        if (tipo == TipoPrimitivo.CADENA)   return "char*";
        if (tipo == TipoPrimitivo.BOOL)     return "int";
        if (tipo == TipoPrimitivo.VOID)     return "void";

        if (tipo instanceof TipoClase tc)
            return tc.getDefinicion().getNombre() + "*";
        if (tipo instanceof TipoEstructura te)
            return te.getDefinicion().getNombre() + "*";
        if (tipo instanceof TipoArreglo ta)
            return aC(ta.getBase()) + "*";

        return "int";  // DESCONOCIDO, NULO, o cualquier tipo futuro sin mapeo
    }

    /**
     * Traduce el NOMBRE de un tipo tal como se escribe en el codigo fuente
     * ("entero", "int", "boolean", "cadena", "String", ...) al tipo C.
     * Lo usan las cuadruplas que guardan el tipo como texto (new, newarray):
     * sin esto, "new boolean[n]" emitia "boolean*" en C, que no existe.
     * Un nombre desconocido se asume clase/estructura del usuario.
     */
    public static String nombreFuenteAC(String nombre) {
        if (nombre == null) return "int";
        // El descriptor de un arreglo multidimensional viene con estrellas
        // ("boolean*" para la dimension externa de boolean[a][b]). Se separan,
        // se traduce la base y se vuelven a pegar.
        int estrellas = 0;
        while (nombre.endsWith("*")) {
            nombre = nombre.substring(0, nombre.length() - 1);
            estrellas++;
        }
        nombre = nombre.replace("[]", "").trim();
        String base = baseFuenteAC(nombre);
        return estrellas == 0 ? base : base + "*".repeat(estrellas);
    }

    private static String baseFuenteAC(String nombre) {
        switch (nombre) {
            case "entero": case "int": case "numerus":
                return "int";
            case "flotante": case "double": case "float": case "decimalis":
                return "double";
            case "caracter": case "char": case "littera":
                return "char";
            case "cadena": case "String": case "string": case "textum":
                return "char*";
            case "bool": case "boolean": case "falsus": case "verum":
                return "int";
            case "void":
                return "void";
            default:
                return nombre;   // clase o estructura del usuario
        }
    }
}