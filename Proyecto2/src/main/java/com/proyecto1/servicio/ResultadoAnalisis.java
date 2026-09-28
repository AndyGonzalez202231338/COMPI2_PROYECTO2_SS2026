package com.proyecto1.servicio;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.errores.ErrorSemantico;
import com.proyecto1.semantico.tabla.AmbitoGlobal;

import java.util.Collections;
import java.util.List;

/**
 * Resultado de analizar un archivo (.y, .z o .pig): las tres listas de errores por
 * separado (léxicos, sintácticos, semánticos) más un mensaje de resumen listo para
 * mostrar. Es un DTO puro -- no sabe nada de JavaFX ni de la consola; quien la
 * imprima (Fase 3, en MainController) decide el formato exacto (colores, prefijos
 * "[OK]"/"[ERROR]", etc.).
 *
 * Reutiliza ErrorSemantico (línea, columna, mensaje) para las tres
 * categorías en vez de inventar ErrorLexico/ErrorSintactico casi idénticas: lo que
 * distingue a un error léxico de uno semántico aquí es en QUÉ LISTA cae, no la
 * forma de la clase que lo representa.
 */
public final class ResultadoAnalisis {

    private final String lenguaje;
    private final List<ErrorSemantico> erroresLexicos;
    private final List<ErrorSemantico> erroresSintacticos;
    private final List<ErrorSemantico> erroresSemanticos;
    private final List<ErrorSemantico> advertencias;
    private final int cantidadLineas;
    private final AmbitoGlobal ambitoGlobal; // solo .y/.z que llegaron hasta el análisis semántico; si no, null
    private final GeneradorC3D generadorC3D; // null si no se generó C3D (por errores o por fallo del generador)

    private ResultadoAnalisis(String lenguaje, List<ErrorSemantico> erroresLexicos,
                              List<ErrorSemantico> erroresSintacticos, List<ErrorSemantico> erroresSemanticos,
                              List<ErrorSemantico> advertencias, int cantidadLineas, AmbitoGlobal ambitoGlobal,
                              GeneradorC3D generadorC3D) {
        this.lenguaje = lenguaje;
        this.erroresLexicos = erroresLexicos;
        this.erroresSintacticos = erroresSintacticos;
        this.erroresSemanticos = erroresSemanticos;
        this.advertencias = advertencias;
        this.cantidadLineas = cantidadLineas;
        this.ambitoGlobal = ambitoGlobal;
        this.generadorC3D = generadorC3D;
    }

    public static ResultadoAnalisis conErrores(String lenguaje, List<ErrorSemantico> erroresLexicos,
                                               List<ErrorSemantico> erroresSintacticos,
                                               List<ErrorSemantico> erroresSemanticos, int cantidadLineas) {
        return conErrores(lenguaje, erroresLexicos, erroresSintacticos, erroresSemanticos,
                Collections.emptyList(), cantidadLineas);
    }

    /** Igual que {@link #conErrores(String, List, List, List, int)} pero con advertencias no bloqueantes (no cuentan para isExito()). */
    public static ResultadoAnalisis conErrores(String lenguaje, List<ErrorSemantico> erroresLexicos,
                                               List<ErrorSemantico> erroresSintacticos,
                                               List<ErrorSemantico> erroresSemanticos,
                                               List<ErrorSemantico> advertencias, int cantidadLineas) {
        return new ResultadoAnalisis(lenguaje, erroresLexicos, erroresSintacticos, erroresSemanticos,
                advertencias, cantidadLineas, null, null);
    }

    /**
     * Igual que la anterior pero además guarda el {@link AmbitoGlobal} resultante del análisis
     * semántico de un .y/.z (sus estructuras, funciones o clase ya resueltas). Es lo que permite
     * que un .pig que hace {@code import} de ese archivo pueda usar sus símbolos.
     */
    public static ResultadoAnalisis conErrores(String lenguaje, List<ErrorSemantico> erroresLexicos,
                                               List<ErrorSemantico> erroresSintacticos,
                                               List<ErrorSemantico> erroresSemanticos,
                                               List<ErrorSemantico> advertencias, int cantidadLineas,
                                               AmbitoGlobal ambitoGlobal) {
        return new ResultadoAnalisis(lenguaje, erroresLexicos, erroresSintacticos, erroresSemanticos,
                advertencias, cantidadLineas, ambitoGlobal, null);
    }

    /**
     * Devuelve una copia de base con el GeneradorC3D seteado. Se usa
     * desde ServicioAnalisis cuando el análisis semántico terminó limpio y la
     * generación de C3D también: en ese caso el resultado base ya tiene todos los
     * errores/advertencias y el ámbito, y solo le falta el generador.
     *
     * Este factory evita duplicar la lista de parámetros de los otros factories
     * (que son muchos y no queremos que se desincronicen): cualquier cambio futuro a
     * los campos del DTO se hace en el constructor privado y los factories existentes
     * siguen funcionando porque pasan null para el generador.
     */
    public static ResultadoAnalisis conC3D(ResultadoAnalisis base, GeneradorC3D generadorC3D) {
        return new ResultadoAnalisis(
                base.lenguaje,
                base.erroresLexicos,
                base.erroresSintacticos,
                base.erroresSemanticos,
                base.advertencias,
                base.cantidadLineas,
                base.ambitoGlobal,
                generadorC3D
        );
    }

    /** Para cuando el lexer/parser/analizador lanzó una excepción inesperada (no debería pasar, pero no debe tumbar la UI). */
    public static ResultadoAnalisis errorInterno(String lenguaje, String mensaje) {
        ErrorSemantico error = new ErrorSemantico(0, 0, "Error interno: " + mensaje);
        return new ResultadoAnalisis(lenguaje, Collections.emptyList(), Collections.emptyList(),
                List.of(error), Collections.emptyList(), 0, null, null);
    }

    public static ResultadoAnalisis extensionNoSoportada(String nombreArchivo) {
        ErrorSemantico error = new ErrorSemantico(0, 0,
                "No se reconoce el tipo de archivo de '" + nombreArchivo + "' (se esperaba .y, .z o .pig).");
        return new ResultadoAnalisis("Desconocido", Collections.emptyList(), Collections.emptyList(),
                List.of(error), Collections.emptyList(), 0, null, null);
    }

    public boolean isExito() { return getTotalErrores() == 0; }

    public int getTotalErrores() {
        return erroresLexicos.size() + erroresSintacticos.size() + erroresSemanticos.size();
    }

    public String getLenguaje() { return lenguaje; }
    public List<ErrorSemantico> getErroresLexicos() { return erroresLexicos; }
    public List<ErrorSemantico> getErroresSintacticos() { return erroresSintacticos; }
    public List<ErrorSemantico> getErroresSemanticos() { return erroresSemanticos; }
    public List<ErrorSemantico> getAdvertencias() { return advertencias; }
    public int getCantidadLineas() { return cantidadLineas; }

    /** Ámbito global del .y/.z analizado (null si no hubo análisis semántico, p. ej. por errores de sintaxis, o si es un .pig). */
    public AmbitoGlobal getAmbitoGlobal() { return ambitoGlobal; }

    /**
     * Generador de C3D del programa, ya ejecutado sobre el AST verificado. Es null
     * cuando NO se generó C3D:
     *
     *   Hubo errores (léxicos, sintácticos o semánticos).
     *   El análisis no llegó hasta la fase semántica (p. ej. errores de sintaxis).
     *   La generación de C3D lanzó una excepción (ver {@link ServicioAnalisis}).
     * Si no es null, el generador ya tiene todas las cuádruplas y firmas del
     */
    public GeneradorC3D getGeneradorC3D() { return generadorC3D; }

    /** Mensaje corto, sin prefijos de presentación (esos los agrega quien imprima en consola). */
    public String getMensajeResumen() {
        if (isExito()) {
            return "Análisis correcto: " + cantidadLineas + " línea" + (cantidadLineas == 1 ? "" : "s") + ".";
        }
        int total = getTotalErrores();
        return total + " error" + (total == 1 ? "" : "es") + " encontrado" + (total == 1 ? "" : "s") + ".";
    }
}