package com.proyecto1.ui.resaltado;

import com.proyecto1.LenguajeLexer;
import javafx.concurrent.Task;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;

import java.util.ArrayList;
import java.util.List;

/**
 * Tarea en segundo plano ({@link Task}) que ejecuta
 * {@code com.proyecto1.LenguajeLexer} sobre un texto completo y produce la
 * lista de {@link TramoColoreado} correspondiente.
 * <p>
 * Se ejecuta fuera del hilo de JavaFX (ver {@link ResaltadorSintaxis}, que
 * la lanza en un {@code ExecutorService} tras el debounce). Esta clase NO
 * toca ningun nodo de la interfaz grafica: solo calcula datos.
 * </p>
 * <p>
 * IMPORTANTE: solo se usa el LEXER compartido del backend (paquete
 * {@code com.proyecto1}), nunca el parser ni el analizador semantico -
 * esos siguen reservados para la fase de conexion real con el backend.
 * </p>
 *
 * @author Proyecto1
 */
public class HiloResaltado extends Task<List<TramoColoreado>> {

    private final String texto;

    /**
     * Crea la tarea de resaltado para un texto dado. El texto se captura
     * en el momento de crear la tarea (no cambia mientras esta corre).
     *
     * @param texto contenido completo del editor a tokenizar
     */
    public HiloResaltado(String texto) {
        this.texto = texto;
    }

    @Override
    protected List<TramoColoreado> call() {
        List<TramoColoreado> tramos = new ArrayList<>();
        if (texto.isEmpty()) {
            return tramos;
        }

        CharStream entrada = CharStreams.fromString(texto);
        LenguajeLexer lexer = new LenguajeLexer(entrada);

        // Reemplazamos el listener de errores por defecto (que solo imprime
        // en consola) por uno que registra la posicion de cada error lexico
        // para poder pintarlo en rojo.
        ColectorErroresLexicos colectorErrores = new ColectorErroresLexicos();
        lexer.removeErrorListeners();
        lexer.addErrorListener(colectorErrores);

        Vocabulary vocabulario = lexer.getVocabulary();

        while (!isCancelled()) {
            Token token = lexer.nextToken();
            if (token.getType() == Token.EOF) {
                break;
            }
            int inicio = token.getStartIndex();
            int fin = token.getStopIndex();
            if (inicio < 0 || fin < inicio) {
                // Token sintetico sin texto real asociado; no deberia ocurrir
                // con este lexer usado de forma independiente (sin
                // IndentTokenStream), pero se ignora por seguridad.
                continue;
            }
            int longitud = fin - inicio + 1;
            String claseCss = MapaColores.obtenerClaseCss(token.getType(), vocabulario);
            tramos.add(new TramoColoreado(inicio, longitud, claseCss));
        }

        if (isCancelled()) {
            return tramos;
        }

        // Los errores lexicos se agregan al final y luego se ordena todo
        // por posicion de inicio, para que ResaltadorSintaxis pueda
        // recorrer la lista de una sola pasada.
        tramos.addAll(colectorErrores.getErrores());
        tramos.sort((a, b) -> Integer.compare(a.getInicio(), b.getInicio()));
        return tramos;
    }

    /**
     * Recolector de errores lexicos: por cada caracter que el lexer no
     * pudo reconocer, registra un {@link TramoColoreado} de un caracter
     * con la clase {@link MapaColores#CLASE_ERROR}.
     * <p>
     * Se usa una longitud fija de 1 caracter como aproximacion razonable
     * (el caso tipico es un simbolo suelto no reconocido); no se intenta
     * calcular con precision cuantos caracteres exactos consumio la
     * recuperacion interna del lexer, para no depender de detalles
     * internos de ANTLR que podrian cambiar entre versiones.
     * </p>
     */
    private static class ColectorErroresLexicos extends BaseErrorListener {

        private final List<TramoColoreado> errores = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> reconocedor, Object simboloOfensivo, int linea,
                                int posicionEnLinea, String mensaje, RecognitionException excepcion) {
            int inicio;
            if (excepcion instanceof LexerNoViableAltException) {
                inicio = ((LexerNoViableAltException) excepcion).getStartIndex();
            } else if (reconocedor instanceof Lexer) {
                inicio = Math.max(0, ((Lexer) reconocedor).getCharIndex() - 1);
            } else {
                return;
            }
            errores.add(new TramoColoreado(inicio, 1, MapaColores.CLASE_ERROR));
        }

        List<TramoColoreado> getErrores() {
            return errores;
        }
    }
}