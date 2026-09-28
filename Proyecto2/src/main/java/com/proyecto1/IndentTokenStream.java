package com.proyecto1;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * IndentTokenStream — filtro de tokens para indentación (chunk 6).
 *
 * ------------------------------------------------------------------
 * IDEA GENERAL:
 *   1) El lexer (LenguajeLexer.g4) ya hizo su parte: por cada salto de
 *      línea real emite un token NEWLINE, y todos los espacios/tabs y
 *      comentarios quedan en el canal oculto (HIDDEN).
 *   2) Esta clase recorre TODO el archivo ya tokenizado en una sola
 *      pasada y reconstruye la lista de tokens:
 *        - Mientras recorre, si encuentra uno o más NEWLINE seguidos
 *          (con o sin comentarios/espacios en medio -- es decir,
 *          líneas en blanco o líneas que son solo un comentario), NO
 *          los agrega de inmediato: simplemente marca "hay un salto
 *          de línea pendiente" y recuerda cuál fue el primero de
 *          ellos (para conservar su posición/línea real).
 *        - Ese salto de línea pendiente recién se materializa -- como
 *          UN SOLO token NEWLINE -- justo antes del siguiente token
 *          de contenido real que aparezca, y ahí mismo se calcula el
 *          nivel de indentación de ese token (usando el último bloque
 *          de espacios/tabs visto) para decidir si hace falta INDENT,
 *          DEDENT, o ninguno.
 *        - Si el archivo empieza con líneas en blanco o de puro
 *          comentario (antes de cualquier token real), ese salto
 *          pendiente se descarta sin más: nunca se emite un NEWLINE
 *          antes del primer token del programa.
 *      Esto evita el bug de una versión anterior, en la que una línea
 *      en blanco o de solo comentario dejaba un NEWLINE "suelto" en
 *      medio del stream que ninguna regla del parser esperaba
 *      (errores tipo "extraneous input '\n'").
 *   3) Dentro de paréntesis "()" o corchetes "[]" (por ejemplo, el
 *      encabezado de un "para (...)") los saltos de línea se ignoran
 *      por completo para la indentación, llevando la cuenta de un
 *      nivel de anidamiento mientras se recorre el archivo.
 *   4) Al llegar a EOF se cierra (con DEDENT) toda indentación que
 *      haya quedado abierta.
 *
 * ------------------------------------------------------------------
 * USO (por ejemplo, en el main del compilador):
 *
 *     CharStream input      = CharStreams.fromFileName(rutaArchivo);
 *     LenguajeLexer lexer   = new LenguajeLexer(input);
 *     IndentTokenStream tokens = new IndentTokenStream(lexer);
 *     GramaticaY parser     = new GramaticaY(tokens);
 *     ParseTree arbol = parser.programa();
 *
 * No hace falta llamar nada más: el constructor ya deja el stream
 * completamente procesado (INDENT/DEDENT insertados) y el cursor de
 * lectura en la posición 0, listo para que el parser lo consuma.
 */
public class IndentTokenStream extends CommonTokenStream {

    /** Cuánto vale una tabulación en espacios, para calcular el nivel. */
    private static final int ANCHO_TAB = 8;

    public IndentTokenStream(TokenSource lexer) {
        super(lexer);
        fill();                // fuerza a tokenizar TODO el archivo de una vez
        insertarIndentacion(); // recorre la lista y agrega INDENT/DEDENT
        seek(0);               // reinicia el cursor para que el parser lea desde el inicio
    }

    /**
     * Recorre this.tokens (la lista ya completa, heredada de
     * BufferedTokenStream) en una sola pasada y construye una nueva
     * lista con los NEWLINE "reales" e INDENT/DEDENT sintéticos ya
     * insertados en su lugar, colapsando líneas en blanco / de solo
     * comentario en el proceso.
     */
    private void insertarIndentacion() {
        List<Token> original = getTokens();     // misma lista que "tokens" internamente
        List<Token> resultado = new ArrayList<>(original.size());

        Deque<Integer> pilaIndentacion = new ArrayDeque<>(Collections.singletonList(0));
        int nivelAnidamiento = 0;      // cuenta de '(' '[' abiertos: ignora NEWLINE ahí dentro

        boolean saltoPendiente = false;        // ¿hay uno o más NEWLINE sin materializar?
        Token primerSaltoPendiente = null;     // el primer NEWLINE de esa racha (para su posición)
        int nivelEspaciosPendiente = 0;        // indentación medida desde el último salto
        boolean huboContenidoPrevio = false;   // ¿ya agregamos algún token real antes?

        for (Token actual : original) {
            int tipo = actual.getType();

            if (tipo == LenguajeLexer.NEWLINE) {
                if (nivelAnidamiento == 0) {
                    if (!saltoPendiente) {
                        saltoPendiente = true;
                        primerSaltoPendiente = actual;
                    }
                    nivelEspaciosPendiente = 0; // cada salto reinicia la medición de espacios
                }
                continue; // el NEWLINE nunca se agrega directamente aquí
            }

            if (tipo == Token.EOF) {
                if (saltoPendiente && huboContenidoPrevio) {
                    resultado.add(crearToken(LenguajeLexer.NEWLINE, primerSaltoPendiente, "<NEWLINE>"));
                    cerrarOAbrirNiveles(pilaIndentacion, nivelEspaciosPendiente, actual, resultado);
                } else if (huboContenidoPrevio && nivelAnidamiento == 0) {
                    // El archivo NO termina en salto de linea (muy comun al guardar desde un editor):
                    // la ultima instruccion igual necesita su NEWLINE, o el parser se queja de
                    // "missing NEWLINE at '<DEDENT>'". Se sintetiza uno en la posicion del EOF.
                    resultado.add(crearToken(LenguajeLexer.NEWLINE, actual, "<NEWLINE>"));
                }
                while (pilaIndentacion.peek() != 0) {
                    pilaIndentacion.pop();
                    resultado.add(crearToken(LenguajeLexer.DEDENT, actual, "<DEDENT>"));
                }
                resultado.add(actual);
                continue;
            }

            if (actual.getChannel() != Token.DEFAULT_CHANNEL) {
                // ESPACIO / comentarios ocultos: se conservan igual en la lista,
                // pero si hay un salto pendiente, el ESPACIO mide su indentación.
                if (tipo == LenguajeLexer.ESPACIO && saltoPendiente) {
                    nivelEspaciosPendiente = calcularNivel(actual.getText());
                }
                resultado.add(actual);
                continue;
            }

            // A partir de aquí: token "real", visible para el parser.
            // Si el archivo aún no tuvo NINGÚN token real, cualquier salto
            // pendiente (comentario/línea en blanco iniciales) se descarta.
            if (saltoPendiente && huboContenidoPrevio) {
                resultado.add(crearToken(LenguajeLexer.NEWLINE, primerSaltoPendiente, "<NEWLINE>"));
                cerrarOAbrirNiveles(pilaIndentacion, nivelEspaciosPendiente, actual, resultado);
            }
            saltoPendiente = false;

            resultado.add(actual);
            huboContenidoPrevio = true;

            if (tipo == LenguajeLexer.LPAREN || tipo == LenguajeLexer.CORIZQ) {
                nivelAnidamiento++;
            } else if (tipo == LenguajeLexer.RPAREN || tipo == LenguajeLexer.CORDER) {
                nivelAnidamiento = Math.max(0, nivelAnidamiento - 1);
            }
        }

        // Reemplaza el contenido de la lista interna del stream
        // (getTokens() devuelve la misma referencia que "tokens").
        original.clear();
        original.addAll(resultado);
    }

    /** Compara el nivel medido contra el tope de la pila y agrega INDENT/DEDENT si hace falta. */
    private void cerrarOAbrirNiveles(Deque<Integer> pilaIndentacion, int nivelEspacios,
                                     Token modelo, List<Token> resultado) {
        int nivelActual = pilaIndentacion.peek();
        if (nivelEspacios > nivelActual) {
            pilaIndentacion.push(nivelEspacios);
            resultado.add(crearToken(LenguajeLexer.INDENT, modelo, "<INDENT>"));
        } else if (nivelEspacios < nivelActual) {
            while (pilaIndentacion.peek() > nivelEspacios) {
                pilaIndentacion.pop();
                resultado.add(crearToken(LenguajeLexer.DEDENT, modelo, "<DEDENT>"));
            }
            // Si, tras desapilar, pilaIndentacion.peek() != nivelEspacios, la
            // indentación no calza con ningún nivel abierto antes (indentación
            // inconsistente). Se podría lanzar un error léxico aquí si el
            // curso lo exige; por ahora se ignora.
        }
        // nivelEspacios == nivelActual -> no se agrega INDENT ni DEDENT
    }

    /** Una tabulación avanza hasta la siguiente columna múltiplo de ANCHO_TAB. */
    private int calcularNivel(String espacios) {
        int nivel = 0;
        for (int i = 0; i < espacios.length(); i++) {
            char c = espacios.charAt(i);
            if (c == ' ') {
                nivel += 1;
            } else if (c == '\t') {
                nivel += ANCHO_TAB - (nivel % ANCHO_TAB);
            }
        }
        return nivel;
    }

    /** Crea un token sintético (INDENT/DEDENT) copiando la posición de un token modelo. */
    private Token crearToken(int tipo, Token modelo, String texto) {
        CommonToken token = new CommonToken(modelo);
        token.setType(tipo);
        token.setText(texto);
        token.setChannel(Token.DEFAULT_CHANNEL);
        return token;
    }
}