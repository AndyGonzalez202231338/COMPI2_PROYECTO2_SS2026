package com.proyecto1.ui.resaltado;

import org.antlr.v4.runtime.Vocabulary;

import java.util.Set;

/**
 * Traduce un tipo de token del {@code LenguajeLexer} (compartido por los
 * tres lenguajes: Y?, Zetariano y PigLatin) a una clase CSS de color.
 * <p>
 * Como el lexer es UNICO para los tres lenguajes, un mismo tipo de token
 * (por ejemplo {@code PUBLIC} o {@code SI}) siempre representa lo mismo
 * sin importar la extension del archivo activo; por eso este mapa NO
 * varia segun el lenguaje detectado, a diferencia de lo planteado
 * originalmente. La clasificacion se hace por el NOMBRE SIMBOLICO del
 * token (via {@link Vocabulary#getSymbolicName(int)}) en lugar de sus
 * constantes enteras generadas, para no depender de que esos numeros no
 * cambien si la gramatica se regenera.
 * </p>
 *
 * @author Proyecto1
 */
public final class MapaColores {

    // ======================================================================
    // NOMBRES DE CLASES CSS (definidas en claro.css / oscuro.css, Fase D)
    // ======================================================================

    public static final String CLASE_RESERVADA = "token-reservada";
    public static final String CLASE_SIMBOLO = "token-simbolo";
    public static final String CLASE_OPERADOR = "token-operador";
    public static final String CLASE_COMENTARIO = "token-comentario";
    public static final String CLASE_NUMERO = "token-numero";
    public static final String CLASE_CADENA = "token-cadena";
    public static final String CLASE_ERROR = "token-error";

    /** Sin clase especial: el texto conserva el color normal del editor. */
    public static final String SIN_CLASE = null;

    // ======================================================================
    // CLASIFICACION POR NOMBRE SIMBOLICO DEL TOKEN
    // ======================================================================

    /** Palabras reservadas de los tres lenguajes (Y?, Zetariano, PigLatin). */
    private static final Set<String> PALABRAS_RESERVADAS = Set.of(
            // -- Y? --
            "ESTRUCTURA", "ENTERO", "FLOTANTE", "CARACTER", "CADENA", "BOOL",
            "VERDADERO", "FALSO", "DEFINIR", "RETORNAR", "SI", "ENTONCES",
            "SINO", "CONTRARIO", "ELEGIR", "CASO", "SIEMPRE", "PARA",
            "MIENTRAS", "HACER", "CONTINUAR", "ROMPER", "IMPRIMIR", "LEER",
            "SEC_ESTRUCTURAS", "SEC_FUNCIONES",
            // -- Zetariano --
            "PUBLIC", "CLASS", "VOID", "STRING", "INT", "DOUBLE", "CHAR",
            "BOOLEAN", "TRUE", "FALSE", "NULL", "NEW", "IF", "ELSE", "SWITCH",
            "DEFAULT", "BREAK", "CONTINUE", "FOR", "WHILE", "DO", "RETURN",
            "PRINTLN", "PRINT", "READLN",
            // -- PigLatin --
            "IMPORT", "ESTO", "SERIES", "NUMERUS", "DECIMALIS", "TEXTUM",
            "LITTERA", "FALSUS", "VERUM", "NOVUS", "ALITER", "FINIS", "DUM",
            "FACERE", "PER", "PERGE", "INTERRUMPE", "VARIABILES", "MAIOR",
            "FIN_PRINCIPAL"
            // NOTA: "CASO" ya esta listado arriba (Y?) y tambien existe
            // "CASE" (Zetariano) por separado; ambos quedan cubiertos.
    );

    /** Simbolos de puntuacion / agrupacion (no son operaciones en si mismas). */
    private static final Set<String> SIMBOLOS = Set.of(
            "DOSPUNTOS", "PUNTOYCOMA", "LPAREN", "RPAREN", "LLAVEIZQ",
            "LLAVEDER", "CORIZQ", "CORDER", "COMA", "PUNTO"
    );

    /** Operadores aritmeticos, logicos, relacionales y de asignacion. */
    private static final Set<String> OPERADORES = Set.of(
            "IGUALIGUAL", "DISTINTO", "Y_LOGICO", "O_LOGICO", "INCREMENTO",
            "DECREMENTO", "MENORIGUAL", "MAYORIGUAL", "MENORQUE", "MAYORQUE",
            "NEGACION", "MAS_ASIGNA", "MENOS_ASIGNA", "MULT_ASIGNA",
            "DIV_ASIGNA", "ASIGNAR", "FLECHA", "MAS", "MENOS", "MULT", "DIV",
            "MODULO", "MOD_ASIGNA", "INTERROGACION", "HASHHASH", "DOSMAYOR",
            "DOSMENOR"
    );

    /** Comentarios de los tres lenguajes (van al canal HIDDEN pero igual se pintan). */
    private static final Set<String> COMENTARIOS = Set.of(
            "COMENTARIO_LINEA", "COMENTARIO_BLOQUE", "COMENTARIO_BLOQUE_PIG"
    );

    /** Literales numericos. */
    private static final Set<String> LITERALES_NUMERICOS = Set.of(
            "FLOTANTE_LIT", "ENTERO_LIT"
    );

    /** Literales de texto (cadenas y caracteres). */
    private static final Set<String> LITERALES_TEXTO = Set.of(
            "CARACTER_LIT", "CADENA_LIT"
    );

    private MapaColores() {
        // Clase de utilidad: no debe instanciarse.
    }

    /**
     * Determina la clase CSS que corresponde a un tipo de token dado.
     *
     * @param tipoToken  tipo de token, tal como lo entrega {@code Token.getType()}
     * @param vocabulario vocabulario del lexer (para resolver el nombre simbolico)
     * @return el nombre de la clase CSS, o {@link #SIN_CLASE} si el token no
     *         requiere un color especial (identificadores, espacios, saltos
     *         de linea, INDENT/DEDENT, etc.)
     */
    public static String obtenerClaseCss(int tipoToken, Vocabulary vocabulario) {
        String nombre = vocabulario.getSymbolicName(tipoToken);
        if (nombre == null) {
            return SIN_CLASE;
        }
        if (PALABRAS_RESERVADAS.contains(nombre)) {
            return CLASE_RESERVADA;
        }
        if (COMENTARIOS.contains(nombre)) {
            return CLASE_COMENTARIO;
        }
        if (LITERALES_NUMERICOS.contains(nombre)) {
            return CLASE_NUMERO;
        }
        if (LITERALES_TEXTO.contains(nombre)) {
            return CLASE_CADENA;
        }
        if (OPERADORES.contains(nombre)) {
            return CLASE_OPERADOR;
        }
        if (SIMBOLOS.contains(nombre)) {
            return CLASE_SIMBOLO;
        }
        // ID, NEWLINE, ESPACIO, INDENT, DEDENT y cualquier token futuro no
        // clasificado: se muestran con el color normal del editor.
        return SIN_CLASE;
    }
}