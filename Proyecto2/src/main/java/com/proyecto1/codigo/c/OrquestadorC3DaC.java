package com.proyecto1.codigo.c;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.cuadruplas.*;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.*;

/**
 * Ensambla un archivo C completo a partir de las cuádruplas y firmas de un programa
 * de Y, PigLatin o Zetariano.
 *
 * Responsabilidades:
 *
 *   Dividir la lista plana de cuádruplas por función, detectando begin_func...
 *       end_func.
 *   Para cada función: emitir su cabecera (con el tipo de retorno y los
 *       parámetros de la GeneradorC3D.Firma), las declaraciones locales
 *       (delegadas a nferenciaTiposC y su cuerpo (delegado a TraductorCuadrupla, salvo param/call, que se
 *       manejan agrupados aquí).
 *   Emitir prototipos de todas las funciones propias y de las funciones
 *       IMPORTADAS (las que un .pig usa de un .y o .z, sin definirlas él).
 *   Emitir un main de C. Dos estrategias:
 *       Y / PigLatin: llama por nombre a la función de entrada del C3D.
 *       Zetariano: instancia una clase y llama a un método de entrada por convención.
 *
 *
 *   Prototipos de funciones importadas: se pasan como Map<String,
 * Firma> (porque el Simbolo de un método o constructor de Z guarda el nombre PLANO ("saludar", "Estudiante"), no la
 * etiqueta MANGLADA que usa el C3D ("Estudiante_saludar", "Estudiante_init_a2").
 * Pasar la GeneradorC3D.Firma ya construida (con la etiqueta correcta,
 * parámetros tipados y tipo de retorno) evita tener que reconstruir el mangling
 * aquí.
 */
public final class OrquestadorC3DaC {

    private final List<Cuadrupla> cuadruplas;
    private final Map<String, GeneradorC3D.Firma> firmas;
    private final String prefijoLenguaje;
    private final String nombreFuncionEntrada;
    private final String claseEntradaZ;
    private final String metodoEntradaZ;
    private final List<Simbolo> definicionesTipo;
    private final Map<String, GeneradorC3D.Firma> firmasExternas;
    private final AmbitoGlobal ambitoGlobalDeMain;
    private final ProveedorRuntime proveedorRuntime;

    /**
     * Constructor central: asigna los 8 campos. Todos los demás delegan aquí.
     */
    /**
     * Constructor central: asigna todos los campos. Todos los demás delegan aquí.
     * Si proveedorRuntime es null se usa RuntimeC.INSTANCIA (compatibilidad con
     * el pipeline actual, que siempre genera C).
     */
    private OrquestadorC3DaC(List<Cuadrupla> cuadruplas,
                             Map<String, GeneradorC3D.Firma> firmas,
                             String prefijoLenguaje,
                             String nombreFuncionEntrada,
                             String claseEntradaZ,
                             String metodoEntradaZ,
                             List<Simbolo> definicionesTipo,
                             Map<String, GeneradorC3D.Firma> firmasExternas,
                             AmbitoGlobal ambitoGlobalDeMain,
                             ProveedorRuntime proveedorRuntime) {
        this.cuadruplas = cuadruplas != null ? cuadruplas : List.of();
        this.firmas = firmas != null ? firmas : Map.of();
        this.prefijoLenguaje = (prefijoLenguaje != null) ? prefijoLenguaje : "";
        this.nombreFuncionEntrada = nombreFuncionEntrada;
        this.claseEntradaZ = claseEntradaZ;
        this.metodoEntradaZ = metodoEntradaZ;
        this.definicionesTipo = (definicionesTipo != null) ? definicionesTipo : List.of();
        this.firmasExternas = (firmasExternas != null) ? firmasExternas : Map.of();
        this.ambitoGlobalDeMain = ambitoGlobalDeMain;
        this.proveedorRuntime = (proveedorRuntime != null) ? proveedorRuntime : RuntimeC.INSTANCIA;
    }

    public OrquestadorC3DaC(List<Cuadrupla> cuadruplas,
                            Map<String, GeneradorC3D.Firma> firmas,
                            String prefijoLenguaje,
                            String nombreFuncionEntrada,
                            List<Simbolo> definicionesTipo) {
        this(cuadruplas, firmas, prefijoLenguaje, nombreFuncionEntrada,
                null, null, definicionesTipo, Map.of(), null, null);
    }

    public OrquestadorC3DaC(List<Cuadrupla> cuadruplas,
                            Map<String, GeneradorC3D.Firma> firmas,
                            String prefijoLenguaje,
                            String nombreFuncionEntrada,
                            List<Simbolo> definicionesTipo,
                            Map<String, GeneradorC3D.Firma> firmasExternas) {
        this(cuadruplas, firmas, prefijoLenguaje, nombreFuncionEntrada,
                null, null, definicionesTipo, firmasExternas, null, null);
    }

    public OrquestadorC3DaC(List<Cuadrupla> cuadruplas,
                            Map<String, GeneradorC3D.Firma> firmas,
                            String prefijoLenguaje,
                            String nombreFuncionEntrada,
                            List<Simbolo> definicionesTipo,
                            Map<String, GeneradorC3D.Firma> firmasExternas,
                            AmbitoGlobal ambitoGlobalDeMain) {
        this(cuadruplas, firmas, prefijoLenguaje, nombreFuncionEntrada,
                null, null, definicionesTipo, firmasExternas, ambitoGlobalDeMain, null);
    }

    public static OrquestadorC3DaC paraZetariano(List<Cuadrupla> cuadruplas,
                                                 Map<String, GeneradorC3D.Firma> firmas,
                                                 String prefijoLenguaje,
                                                 List<Simbolo> definicionesTipo,
                                                 String claseEntrada,
                                                 String metodoEntrada) {
        return new OrquestadorC3DaC(cuadruplas, firmas, prefijoLenguaje,
                null, claseEntrada, metodoEntrada, definicionesTipo, Map.of(), null, null);
    }

    // Factoría para backends alternativos (RISC-V, etc.).
    // Recibe un ProveedorRuntime personalizado en lugar de usar RuntimeC.
    public static OrquestadorC3DaC conRuntime(List<Cuadrupla> cuadruplas,
                                              Map<String, GeneradorC3D.Firma> firmas,
                                              String prefijoLenguaje,
                                              String nombreFuncionEntrada,
                                              List<Simbolo> definicionesTipo,
                                              Map<String, GeneradorC3D.Firma> firmasExternas,
                                              AmbitoGlobal ambitoGlobalDeMain,
                                              ProveedorRuntime runtime) {
        return new OrquestadorC3DaC(cuadruplas, firmas, prefijoLenguaje,
                nombreFuncionEntrada, null, null, definicionesTipo,
                firmasExternas, ambitoGlobalDeMain, runtime);
    }

    /** Genera el archivo C completo: runtime + structs + prototipos + funciones + main. */
    public String generarArchivoCompleto() {
        List<FuncionCompilada> funciones = dividirPorFuncion();

        StringBuilder sb = new StringBuilder();
        sb.append("/* Archivo generado automáticamente por OrquestadorC3DaC */\n\n");

        sb.append(proveedorRuntime.codigo());
        sb.append("\n");

        sb.append(new GeneradorStructsC().generar(definicionesTipo));
        if (!definicionesTipo.isEmpty()) sb.append("\n");

        // Prototipos de funciones propias
        sb.append("/* Prototipos */\n");
        for (FuncionCompilada fc : funciones) {
            sb.append(prototipo(fc)).append(";\n");
        }

        // Prototipos de funciones importadas (no tienen definición en este archivo).
        for (GeneradorC3D.Firma f : firmasExternas.values()) {
            sb.append(cabeceraDesdeFirma(f)).append(";\n");
        }
        sb.append("\n");

        // Definiciones
        sb.append("/* Definiciones */\n");
        for (FuncionCompilada fc : funciones) {
            sb.append(definicion(fc)).append("\n");
        }

        // main wrapper
        if (nombreFuncionEntrada != null || claseEntradaZ != null) {
            sb.append(mainWrapper(funciones));
        }
        return sb.toString();
    }

    private record FuncionCompilada(CuadruplaBeginFunc begin, List<Cuadrupla> cuerpo) {}

    private List<FuncionCompilada> dividirPorFuncion() {
        List<FuncionCompilada> resultado = new ArrayList<>();
        int i = 0;
        while (i < cuadruplas.size()) {
            Cuadrupla c = cuadruplas.get(i);
            if (c instanceof CuadruplaBeginFunc bf) {
                int j = i + 1;
                while (j < cuadruplas.size() && !(cuadruplas.get(j) instanceof CuadruplaEndFunc)) {
                    j++;
                }
                resultado.add(new FuncionCompilada(bf, cuadruplas.subList(i + 1, j)));
                i = j + 1;
            } else {
                i++;
            }
        }
        return resultado;
    }

    // Cabecera + cuerpo

    private String prototipo(FuncionCompilada fc) {
        return cabecera(fc, false);
    }

    private String definicion(FuncionCompilada fc) {
        StringBuilder sb = new StringBuilder();
        sb.append(cabecera(fc, true)).append(" {\n");

        Map<String, String> tipos = new HashMap<>();
        GeneradorC3D.Firma firma = firmas.get(fc.begin().nombre());
        if (firma != null) {
            for (GeneradorC3D.ParametroFirma p : firma.parametros()) {
                tipos.put(p.nombre(), tipoAC(p.tipo()));
            }
        }

        Map<String, GeneradorC3D.Firma> firmasVisibles = new HashMap<>(firmas);
        firmasVisibles.putAll(firmasExternas);

        // El inferidor recibe también el ámbito global para resolver tipos de variables globales (típico en PigLatin) e indirectamente los accesos a
        // estructuras importadas.
        InferenciaTiposC inf = new InferenciaTiposC(fc.cuerpo(), firma, firmasVisibles,
                definicionesTipo, ambitoGlobalDeMain);

        // Declaraciones locales inferidas.
        for (String linea : inf.comoLineasDeC()) {
            sb.append("    ").append(linea).append("\n");
        }

        // Declaraciones de las variables GLOBALES del programa solo en la función de entrada (o en el main de Z), para no duplicarlas.
        // Se saltan las que ya están inferidas como locales.
        boolean esFuncionDeEntrada = (nombreFuncionEntrada != null
                && fc.begin().nombre().equals(nombreFuncionEntrada))
                || (claseEntradaZ != null);   // en Z, el main() es el wrapper, no "esta" función
        if (ambitoGlobalDeMain != null && !ambitoGlobalDeMain.simbolosLocales().isEmpty()) {
            for (Simbolo s : ambitoGlobalDeMain.simbolosLocales()) {
                if (s.getCategoria() != CategoriaSimbolo.VARIABLE) continue;
                if (inf.getDeclaraciones().containsKey(s.getNombre())) continue;
                sb.append("    ").append(tipoAC(s.getTipo())).append(" ")
                        .append(s.getNombre()).append(";\n");
                // Registrar el tipo para el traductor también.
                tipos.put(s.getNombre(), tipoAC(s.getTipo()));
            }
        }

        // Volcar los tipos inferidos al mapa que usará TraductorCuadrupla.
        tipos.putAll(inf.getDeclaraciones());

        if (!inf.getDeclaraciones().isEmpty()
                || (ambitoGlobalDeMain != null && !ambitoGlobalDeMain.simbolosLocales().isEmpty())) {
            sb.append("\n");
        }

        sb.append(cuerpoATexto(fc.cuerpo(), tipos));
        sb.append("}\n");
        return sb.toString();
    }

    private String cabecera(FuncionCompilada fc, boolean conNombre) {
        GeneradorC3D.Firma firma = firmas.get(fc.begin().nombre());
        String tipoRet = (firma != null && firma.tipoRetorno() != null)
                ? tipoAC(firma.tipoRetorno()) : "void";

        List<GeneradorC3D.ParametroFirma> params =
                (firma != null) ? firma.parametros() : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append(tipoRet).append(" ").append(prefijoLenguaje).append(fc.begin().nombre()).append("(");
        if (params.isEmpty()) {
            sb.append("void");
        } else {
            List<String> ps = new ArrayList<>();
            for (GeneradorC3D.ParametroFirma p : params) {
                ps.add(tipoAC(p.tipo()) + " " + p.nombre());
            }
            sb.append(String.join(", ", ps));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Cabecera C de una función importada, construida desde su GeneradorC3D.Firma.
     * Misma forma que cabecera pero sin necesitar la cuádrupla begin_func —
     * la firma ya trae la etiqueta manglada, los parámetros tipados y el retorno.
     */
    private String cabeceraDesdeFirma(GeneradorC3D.Firma firma) {
        String tipoRet = (firma.tipoRetorno() != null)
                ? tipoAC(firma.tipoRetorno()) : "void";
        StringBuilder sb = new StringBuilder();
        sb.append(tipoRet).append(" ").append(prefijoLenguaje).append(firma.etiqueta()).append("(");
        List<GeneradorC3D.ParametroFirma> params = firma.parametros();
        if (params.isEmpty()) {
            sb.append("void");
        } else {
            List<String> ps = new ArrayList<>();
            for (GeneradorC3D.ParametroFirma p : params) {
                ps.add(tipoAC(p.tipo()) + " " + p.nombre());
            }
            sb.append(String.join(", ", ps));
        }
        sb.append(")");
        return sb.toString();
    }

    // Traducción del cuerpo con contexto

    private String cuerpoATexto(List<Cuadrupla> cuerpo, Map<String, String> tipos) {
        StringBuilder sb = new StringBuilder();
        TraductorCuadrupla tr = new TraductorCuadrupla(tipos);
        List<String> paramsPendientes = new ArrayList<>();

        for (Cuadrupla c : cuerpo) {
            if (c instanceof CuadruplaParam p) {
                paramsPendientes.add(p.valor());
                continue;
            }
            if (c instanceof CuadruplaCall call) {
                sb.append("    ").append(traducirCall(call, paramsPendientes, tipos)).append("\n");
                paramsPendientes.clear();
                continue;
            }
            sb.append("    ").append(tr.traducir(c)).append("\n");
        }
        return sb.toString();
    }

    private String traducirCall(CuadruplaCall call, List<String> params, Map<String, String> tipos) {
        String fname = call.funcion();

        if ("rt_print".equals(fname) || "rt_println".equals(fname)) {
            String arg = params.isEmpty() ? "" : params.get(0);
            String tipo = tipos.getOrDefault(arg, "int");
            String sufijo = sufijoTipo(tipo);
            String fn = "rt_print".equals(fname) ? "rt_print_" + sufijo : "rt_println_" + sufijo;
            return fn + "(" + arg + ");";
        }

        if ("rt_readln".equals(fname)) {
            return (call.destino() != null)
                    ? call.destino() + " = rt_read_string();"
                    : "rt_read_string();";
        }

        String nombre = prefijoLenguaje + fname;
        String args = String.join(", ", params);
        String expr = nombre + "(" + args + ")";
        return (call.destino() != null) ? call.destino() + " = " + expr + ";" : expr + ";";
    }

    private static String sufijoTipo(String tipoC) {
        if (tipoC == null) return "int";
        if (tipoC.equals("double")) return "double";
        if (tipoC.equals("char"))   return "char";
        if (tipoC.equals("char*"))  return "string";
        return "int";
    }

    // main de C

    private String mainWrapper(List<FuncionCompilada> funciones) {
        if (claseEntradaZ != null) {
            return mainWrapperZetariano();
        }
        if (nombreFuncionEntrada == null) return "";
        return mainWrapperFuncionNombrada(funciones);
    }

    private String mainWrapperFuncionNombrada(List<FuncionCompilada> funciones) {
        boolean existeEntrada = funciones.stream()
                .anyMatch(fc -> fc.begin().nombre().equals(nombreFuncionEntrada));

        StringBuilder sb = new StringBuilder();
        sb.append("/* Punto de entrada */\n");
        sb.append("int main(void) {\n");
        if (existeEntrada) {
            sb.append("    ").append(prefijoLenguaje).append(nombreFuncionEntrada).append("();\n");
        }
        sb.append("    return 0;\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Estrategia Zetariano: genera un main() que instancia claseEntradaZ (constructor sin argumentos) y llama a
     * metodoEntradaZ sobre el objeto. Valida que exista el constructor y
     * que el método de entrada tenga aridad 1 (solo "this").
     */
    private String mainWrapperZetariano() {
        StringBuilder sb = new StringBuilder();
        sb.append("/* Punto de entrada (Zetariano: instancia ").append(claseEntradaZ)
                .append(" y llama a ").append(metodoEntradaZ).append(") */\n");
        sb.append("int main(void) {\n");

        String etiquetaCtor = GeneradorC3D.etiquetaConstructor(claseEntradaZ, List.of());
        if (!firmas.containsKey(etiquetaCtor)) {
            sb.append("    /* ERROR: la clase '").append(claseEntradaZ)
                    .append("' no tiene un constructor sin argumentos (se esperaba '")
                    .append(etiquetaCtor).append("'). */\n");
            sb.append("    return 1;\n}\n");
            return sb.toString();
        }

        String etiquetaMetodo = GeneradorC3D.etiquetaMetodo(claseEntradaZ, metodoEntradaZ, List.of());
        GeneradorC3D.Firma firmaMetodo = firmas.get(etiquetaMetodo);
        if (firmaMetodo == null) {
            sb.append("    /* ERROR: el método de entrada '").append(metodoEntradaZ)
                    .append("' no existe en la clase '").append(claseEntradaZ).append("'. */\n");
            sb.append("    return 1;\n}\n");
            return sb.toString();
        }
        if (firmaMetodo.parametros().size() != 1) {
            sb.append("    /* ERROR: el método de entrada '").append(metodoEntradaZ)
                    .append("' no puede recibir parámetros propios (tiene ")
                    .append(firmaMetodo.parametros().size() - 1).append("). */\n");
            sb.append("    return 1;\n}\n");
            return sb.toString();
        }

        String obj = "obj";
        sb.append("    ").append(claseEntradaZ).append("* ").append(obj)
                .append(" = (").append(claseEntradaZ).append("*) malloc(sizeof(")
                .append(claseEntradaZ).append("));\n");
        sb.append("    ").append(prefijoLenguaje).append(etiquetaCtor).append("(").append(obj).append(");\n");
        sb.append("    ").append(prefijoLenguaje).append(etiquetaMetodo).append("(").append(obj).append(");\n");
        sb.append("    return 0;\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    private static String tipoAC(Tipo t) {
        if (t == null) return "void";
        if (t == TipoPrimitivo.ENTERO)      return "int";
        if (t == TipoPrimitivo.FLOTANTE)    return "double";
        if (t == TipoPrimitivo.CARACTER)    return "char";
        if (t == TipoPrimitivo.CADENA)      return "char*";
        if (t == TipoPrimitivo.BOOL)        return "int";
        if (t == TipoPrimitivo.VOID)        return "void";
        if (t == TipoPrimitivo.NULO)        return "void*";
        if (t == TipoPrimitivo.DESCONOCIDO) return "int";
        if (t instanceof com.proyecto1.semantico.tipos.TipoClase tc)
            return tc.getDefinicion().getNombre() + "*";
        if (t instanceof com.proyecto1.semantico.tipos.TipoEstructura te)
            return te.getDefinicion().getNombre() + "*";
        if (t instanceof com.proyecto1.semantico.tipos.TipoArreglo ta)
            return tipoAC(ta.getBase()) + "*";
        return "int";
    }
}