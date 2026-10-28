package com.proyecto1.codigo.riscv;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registra los literales de cadena del programa y los coloca en la seccion .data.
 *  En RISC-V una cadena no cabe en un registro: se guarda una sola vez en memoria estatica (.data) con una etiqueta, y
 *  en el codigo se trabaja con su DIRECCION:
 *      .data
 *      str_0: .asciiz "Hola"          <- bytes de la cadena + un 0 al final
 *      .text
 *      la a1, str_0                   <- a1 = direccion donde empieza "Hola"
 * Por eso una variable de tipo cadena guarda en su slot de la pila una direccion, no el texto (paso por referencia).
 *
 */
public final class TablaCadenas {

    // texto ya decodificado -> etiqueta (str_0, str_1, ...). LinkedHashMap para que la seccion .data salga en el orden en que aparecieron los literales.
    private final Map<String, String> etiquetas = new LinkedHashMap<>();

    // Literales flotantes: bits IEEE 754 -> etiqueta (flt_0, flt_1, ...)..
    private final Map<Integer, String> flotantes = new LinkedHashMap<>();

    // Recibe el literal tal como viene en el C3D y devuelve la etiqueta de .data donde quedara guardado.
    public String etiquetaDe(String literalC3D) {
        String texto = decodificar(literalC3D);
        return etiquetas.computeIfAbsent(texto, k -> "str_" + etiquetas.size());
    }

    // Registra un literal flotante por sus bits IEEE 754 y devuelve su etiqueta. Ejemplo: 3.14f -> bits 1078523331 -> etiqueta "flt_0".
    public String etiquetaDeFlotante(int bitsIEEE) {
        return flotantes.computeIfAbsent(bitsIEEE, k -> "flt_" + flotantes.size());
    }

    public boolean estaVacia() {
        return etiquetas.isEmpty() && flotantes.isEmpty();
    }

    // Emite la seccion .data: primero cadenas (.asciiz), luego flotantes (.word).
    public void emitirSeccionData(StringBuilder sb) {
        for (Map.Entry<String, String> e : etiquetas.entrySet()) {
            sb.append(e.getValue()).append(": .string ")
              .append(aJsonAscii(e.getKey())).append("\n");
        }
        if (!flotantes.isEmpty()) {
            sb.append("# --- literales flotantes (bits IEEE 754 como .word) ---\n");
            for (Map.Entry<Integer, String> e : flotantes.entrySet()) {
                sb.append(e.getValue()).append(": .word ")
                  .append(e.getKey()).append("  # float ")
                  .append(Float.intBitsToFloat(e.getKey())).append("\n");
            }
        }
    }

    // Quita las comillas y resuelve los escapes que genera el C3D (\n, \t, \r, \0, \\, \", \'), dejando el texto real.
    private static String decodificar(String literal) {
        String s = literal;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '0' -> out.append('\0');
                    default -> out.append(n);   // \\  \"  \'  y cualquier otro
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // Vuelve a escribir el texto como cadena JSON solo con caracteres ASCII, que es lo unico que acepta .asciiz
    private static String aJsonAscii(String texto) {
        String ascii = aAscii(texto);
        StringBuilder sb = new StringBuilder("\"");
        for (char c : ascii.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    // Reemplaza lo que no es ASCII: quita tildes (a con tilde -> a), enie -> n, y lo que no tenga equivalente -> '?'.
    private static String aAscii(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        StringBuilder sb = new StringBuilder();
        for (char c : sinTildes.toCharArray()) {
            if (c < 128) sb.append(c);
            else if (c == '\u00BF' || c == '\u00A1') { /* signos de apertura: se omiten */ }
            else sb.append('?');
        }
        return sb.toString();
    }
}
