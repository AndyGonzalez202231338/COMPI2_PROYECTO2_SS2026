package com.proyecto1.servicio;

import com.proyecto1.semantico.errores.ErrorSemantico;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Reemplaza el reporte por defecto de ANTLR (que imprime a stderr con un formato
 * poco amigable, tipo "line 3:5 token recognition error at: '@'") por dos listas
 * propias, en español. Una sola instancia sirve tanto para el lexer como para el
 * parser: se autoclasifica revisando de qué tipo es "recognizer" -- si es un
 * {@link Lexer}, el error es léxico; si no (es el Parser), es sintáctico.
 *
 * Convención de columna: se guarda tal cual la entrega ANTLR
 * (getCharPositionInLine(), base 0), igual que el resto del proyecto -- los nodos
 * del AST tampoco le suman 1, así que todo queda consistente.
 */
public final class ListenerErroresANTLR extends BaseErrorListener {

    private final List<ErrorSemantico> erroresLexicos = new ArrayList<>();
    private final List<ErrorSemantico> erroresSintacticos = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                            int charPositionInLine, String mensaje, RecognitionException excepcion) {
        ErrorSemantico error = new ErrorSemantico(line, charPositionInLine, mensaje);
        if (recognizer instanceof Lexer) {
            erroresLexicos.add(error);
        } else {
            erroresSintacticos.add(error);
        }
    }

    public List<ErrorSemantico> getErroresLexicos() { return erroresLexicos; }

    public List<ErrorSemantico> getErroresSintacticos() { return erroresSintacticos; }

    public boolean tieneErrores() { return !erroresLexicos.isEmpty() || !erroresSintacticos.isEmpty(); }
}