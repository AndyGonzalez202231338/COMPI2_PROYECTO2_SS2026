package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Un literal (#primariaEntero, #primariaFlotante, #primariaCaracter, #primariaCadena,
 * #primariaVerdadero, #primariaFalso; también usado por la regla independiente
 * literal dentro de los "caso" de un elegir: #litEntero, #litCaracter,
 * #litCadena). El valor ya viene "parseado" a su tipo Java correspondiente
 * (Long/Double/Character/String/Boolean), no como texto crudo así los nodos de más
 * arriba no tienen que volver a parsear números ni desescapar cadenas.
 */
public final class Literal extends NodoY implements ExpresionY {

    private final Object valor;
    private final CategoriaLiteral categoria;

    public Literal(Object valor, CategoriaLiteral categoria, int linea, int columna) {
        super(linea, columna);
        this.valor = valor;
        this.categoria = categoria;
    }

    public Object getValor() {
        return valor;
    }

    public CategoriaLiteral getCategoria() {
        return categoria;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        return switch (categoria) {
            case ENTERO    -> TipoPrimitivo.ENTERO;
            case FLOTANTE  -> TipoPrimitivo.FLOTANTE;
            case CARACTER  -> TipoPrimitivo.CARACTER;
            case CADENA    -> TipoPrimitivo.CADENA;
            case BOOLEANO  -> TipoPrimitivo.BOOL;
        };
    }

    /**
     * Emite: NADA (un literal no necesita cuádruplas, se usa directamente como operando).
     * Devuelve: ResultadoC3D.valor(texto, tipo), donde "texto" es la forma
     * literal lista para usarse como operando: enteros/flotantes tal cual ("5", "3.14"),
     * caracteres entre comillas simples ('a'), cadenas entre comillas dobles ("hola",
     * re-escapadas porque el AST guarda el valor ya desescapado) y booleanos como
     * "true"/"false". Las comillas permiten distinguir después un literal cadena "5" de
     * un entero 5 sin re-parsear.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String texto = switch (categoria) {
            case ENTERO, FLOTANTE, BOOLEANO -> String.valueOf(valor);
            case CARACTER -> "'" + escapar(String.valueOf(valor), '\'') + "'";
            case CADENA   -> "\"" + escapar(String.valueOf(valor), '"') + "\"";
        };
        return ResultadoC3D.valor(texto, verificar(null, null));
        // verificar() no usa ambito ni errores para un literal; se reutiliza para no
        // duplicar el mapeo categoría -> tipo.
    }

    /** Re-escapa un texto ya desescapado para poder escribirlo entre comillas. */
    private static String escapar(String texto, char delimitador) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '\0' -> sb.append("\\0");
                default -> {
                    if (c == delimitador) sb.append('\\');
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}