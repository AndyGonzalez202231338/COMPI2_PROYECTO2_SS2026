package com.proyecto1.codigo.c;

import com.proyecto1.semantico.ast.cuadruplas.*;

import java.util.List;
import java.util.Map;

/**
 * Traduce UNA cuádrupla del C3D a su línea equivalente en C.
 *
 * El traductor recibe un mapa lugar -> tipoC (típicamente provisto por InferenciaTiposC), con el que decide:
 * Concatenación de strings (rt_concat) cuando + involucra un char*.
 * Comparación de contenido (rt_strcmp) cuando ==/!= compara dos char*.
 * Formato correcto de printf/scanf según el tipo.
 * Elección de rt_print_<tipo> vs rt_println_<tipo> al traducir las llamadas al runtime de Z.
 * Literales: los literales string/char/numéricos NO están en el mapa
 * (nunca son destino de una cuádrupla). Se detectan por su forma con tipoEfectivo(String). Esto es clave para que "Hola" + t2 se trate como
 * concatenación y no como operación aritmética.
 */
public final class TraductorCuadrupla implements VisitanteCuadrupla<String> {

    private final Map<String, String> tipos;

    public TraductorCuadrupla() {
        this(Map.of());
    }

    public TraductorCuadrupla(Map<String, String> tipos) {
        this.tipos = (tipos != null) ? tipos : Map.of();
    }

    public String traducir(Cuadrupla c) {
        return c.aceptar(this);
    }

    // Aritmética / lógica / relacionales

    @Override
    public String visitar(CuadruplaBinaria c) {
        String op = c.operador();
        String aTipo = tipoEfectivo(c.a());
        String bTipo = tipoEfectivo(c.b());
        boolean aEsString = "char*".equals(aTipo);
        boolean bEsString = "char*".equals(bTipo);

        // Concatenación si alguno de los operandos es string.
        if ("+".equals(op) && (aEsString || bEsString)) {
            String aLugar = aEsString ? c.a() : convertirAString(c.a(), aTipo);
            String bLugar = bEsString ? c.b() : convertirAString(c.b(), bTipo);
            return c.t() + " = rt_concat(" + aLugar + ", " + bLugar + ");";
        }

        // Comparación de strings por contenido.
        if (("==".equals(op) || "!=".equals(op)) && aEsString && bEsString) {
            String cmp = "==".equals(op) ? "== 0" : "!= 0";
            return c.t() + " = (rt_strcmp(" + c.a() + ", " + c.b() + ") " + cmp + ");";
        }

        return c.t() + " = " + c.a() + " " + op + " " + c.b() + ";";
    }

    @Override
    public String visitar(CuadruplaUnaria c) {
        String op = "not".equals(c.operador()) ? "!" : c.operador();
        return c.t() + " = " + op + c.a() + ";";
    }

    @Override
    public String visitar(CuadruplaAsignacion c) {
        return c.destino() + " = " + c.valor() + ";";
    }

    // Control de flujo

    @Override public String visitar(CuadruplaGoto c)     { return "goto " + c.etiqueta() + ";"; }
    @Override public String visitar(CuadruplaIfFalse c)  { return "if (!" + c.condicion() + ") goto " + c.etiqueta() + ";"; }
    @Override public String visitar(CuadruplaIfTrue c)   { return "if (" + c.condicion() + ") goto " + c.etiqueta() + ";"; }
    @Override public String visitar(CuadruplaEtiqueta c) { return c.etiqueta() + ":;"; }
    @Override public String visitar(CuadruplaReturn c)   { return c.valor() != null ? "return " + c.valor() + ";" : "return;"; }

    // Heap

    @Override
    public String visitar(CuadruplaNew c) {
        return c.destino() + " = (" + c.clase() + "*) malloc(sizeof(" + c.clase() + "));";
    }

    @Override
    public String visitar(CuadruplaCampoCarga c) {
        return c.destino() + " = " + c.objeto() + "->" + c.campo() + ";";
    }

    @Override
    public String visitar(CuadruplaCampoGuarda c) {
        return c.objeto() + "->" + c.campo() + " = " + c.valor() + ";";
    }

    // I/O

    @Override
    public String visitar(CuadruplaPrint c) {
        String valor = c.valor();
        String tipo = tipoEfectivo(valor);
        String fmt = formatoPrintf(tipo) + (c.nuevaLinea() ? "\\n" : "");
        return "printf(\"" + fmt + "\", " + valor + ");";
    }

    @Override
    public String visitar(CuadruplaRead c) {
        String tipo = tipos.getOrDefault(c.destino(), "int");
        if ("char*".equals(tipo)) {
            return c.destino() + " = rt_read_string();";
        }
        return "scanf(\"" + formatoScanf(tipo) + "\", &" + c.destino() + ");";
    }

    // Funciones

    @Override public String visitar(CuadruplaBeginFunc c) { throw pendiente("begin_func"); }
    @Override public String visitar(CuadruplaEndFunc c)   { throw pendiente("end_func"); }
    @Override public String visitar(CuadruplaCall c)      { throw pendiente("call"); }
    @Override public String visitar(CuadruplaParam c)     { throw pendiente("param"); }

    // Arreglos

    @Override
    public String visitar(CuadruplaIndiceCarga c) {
        return c.destino() + " = " + c.arreglo() + "[" + c.indice() + "];";
    }

    @Override
    public String visitar(CuadruplaIndiceGuarda c) {
        return c.arreglo() + "[" + c.indice() + "] = " + c.valor() + ";";
    }

    // Cuadruplas nuevas de la Fase 2 (cadenas y conversion). En C se traducen igual que antes: rt_concat, rt_strcmp y casts.
    @Override
    public String visitar(CuadruplaConcat c) {
        String aTipo = tipoEfectivo(c.a());
        String bTipo = tipoEfectivo(c.b());
        String aLugar = "char*".equals(aTipo) ? c.a() : convertirAString(c.a(), aTipo);
        String bLugar = "char*".equals(bTipo) ? c.b() : convertirAString(c.b(), bTipo);
        return c.t() + " = rt_concat(" + aLugar + ", " + bLugar + ");";
    }

    @Override
    public String visitar(CuadruplaCompCadena c) {
        String cmp = "==".equals(c.operador()) ? "== 0" : "!= 0";
        return c.t() + " = (rt_strcmp(" + c.a() + ", " + c.b() + ") " + cmp + ");";
    }

    @Override
    public String visitar(CuadruplaConversion c) {
        // Conversiones a cadena usan las funciones del runtime C.
        if ("cadena".equals(c.tipoDestino())) {
            String fn = switch (c.tipoOrigen()) {
                case "entero" -> "rt_int_to_string";
                case "flotante" -> "rt_double_to_string";
                case "caracter" -> "rt_char_to_string";
                case "bool" -> "rt_int_to_string";
                default -> "rt_int_to_string";
            };
            return c.destino() + " = " + fn + "(" + c.valor() + ");";
        }
        // El resto son casts numericos (entero -> flotante, etc.)
        String tipoC = TraductorTipos.nombreFuenteAC(c.tipoDestino());
        return c.destino() + " = (" + tipoC + ") " + c.valor() + ";";
    }

    @Override
    public String visitar(CuadruplaNewArray c) {
        List<String> ts = c.tamanos();
        String producto = (ts.size() == 1) ? ts.get(0) : String.join(" * ", ts);
        String tipoC = TraductorTipos.nombreFuenteAC(c.tipoElemento());
        return c.destino() + " = (" + tipoC + "*) malloc(("+ producto + ") * sizeof(" + tipoC + "));";
    }

    /**
     * Tipo C efectivo de un operando. Detecta literales por forma (no están en el
     * mapa porque nunca son destino de una cuádrupla) y cae al mapa para variables
     * y temporales.
     */
    private String tipoEfectivo(String lugar) {
        return tipoEfectivo(lugar, tipos);
    }

    static String tipoEfectivo(String lugar, java.util.Map<String, String> tipos) {
        if (lugar == null) return "int";
        if (lugar.length() >= 2 && lugar.startsWith("\"") && lugar.endsWith("\"")) return "char*";
        if (lugar.length() >= 2 && lugar.startsWith("'") && lugar.endsWith("'")) return "char";
        if ("true".equals(lugar) || "false".equals(lugar)) return "int";
        if ("null".equals(lugar)) return "void*";
        if (lugar.matches("-?\\d+")) return "int";
        if (lugar.matches("-?\\d*\\.\\d+")) return "double";
        return tipos.getOrDefault(lugar, "int");
    }

    private static String convertirAString(String lugar, String tipoC) {
        if ("double".equals(tipoC)) return "rt_double_to_string(" + lugar + ")";
        if ("char".equals(tipoC))   return "rt_char_to_string(" + lugar + ")";
        return "rt_int_to_string(" + lugar + ")";
    }

    private static String formatoPrintf(String tipoC) {
        if (tipoC == null) return "%d";
        return switch (tipoC) {
            case "double" -> "%lf";
            case "char"   -> "%c";
            case "char*"  -> "%s";
            default       -> "%d";
        };
    }

    private static String formatoScanf(String tipoC) {
        if (tipoC == null) return "%d";
        return switch (tipoC) {
            case "double" -> "%lf";
            case "char"   -> " %c";
            default       -> "%d";
        };
    }

    private static UnsupportedOperationException pendiente(String op) {
        return new UnsupportedOperationException(
                "TraductorCuadrupla: '" + op + "' todavía no está implementado");
    }
}