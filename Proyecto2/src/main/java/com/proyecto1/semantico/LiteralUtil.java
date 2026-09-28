package com.proyecto1.semantico;


public final class LiteralUtil {

    private LiteralUtil() {}

    /** Quita la comilla inicial/final y desescapa el contenido de un CADENA_LIT o CARACTER_LIT. */
    public static String textoSinComillasNiEscapes(String textoCrudo) {
        String interior = textoCrudo.substring(1, textoCrudo.length() - 1);
        StringBuilder resultado = new StringBuilder(interior.length());
        for (int i = 0; i < interior.length(); i++) {
            char actual = interior.charAt(i);
            if (actual == '\\' && i + 1 < interior.length()) {
                char siguiente = interior.charAt(++i);
                switch (siguiente) {
                    case 'n' -> resultado.append('\n');
                    case 't' -> resultado.append('\t');
                    case 'r' -> resultado.append('\r');
                    case '\'' -> resultado.append('\'');
                    case '"' -> resultado.append('"');
                    case '\\' -> resultado.append('\\');
                    default -> resultado.append(siguiente); // escape desconocido: se deja tal cual
                }
            } else {
                resultado.append(actual);
            }
        }
        return resultado.toString();
    }

    /** Convierte el texto de un CARACTER_LIT ya desescapado a su único char. */
    public static char aCaracter(String textoCrudo) {
        return textoSinComillasNiEscapes(textoCrudo).charAt(0);
    }

    public static long aEntero(String textoCrudo) {
        return Long.parseLong(textoCrudo);
    }

    public static double aFlotante(String textoCrudo) {
        return Double.parseDouble(textoCrudo);
    }
}
