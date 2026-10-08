package com.proyecto1.codigo.c;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.cuadruplas.*;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Infiere el tipo C de cada variable local y temporal de UNA función a partir de
 * las cuádruplas de su cuerpo más su GeneradorC3D.Firma.
 *
 * Ámbito global:cuando el cuerpo referencia un identificador que no es
 * parámetro ni ya fue  declarado por una cuádrupla anterior (típico de las variables
 * globales de PigLatin: personas, suma, contador, ...),
 * se consulta el AmbitoGlobal que se pasó al constructor. Ese ámbito es
 * el del programa completo, con todas las globales ya declaradas por el semántico.
 * Sin esto, un acceso a personas trataría personas como int y todo lo derivado saldría mal tipado.
 */
public final class InferenciaTiposC implements VisitanteCuadrupla<Void> {

    private final GeneradorC3D.Firma firma;
    private final Map<String, GeneradorC3D.Firma> firmasPrograma;

    /** Tipos definidos por el usuario (estructuras de Y, clases de Z) por nombre. */
    private final Map<String, Simbolo> tiposPorNombre = new HashMap<>();

    /**
     * Símbolos del ámbito global del programa (variables y funciones de nivel
     * superior). Se usan como fuente de tipos para identificadores que no son
     * parámetros ni locales del cuerpo.
     */
    private final Map<String, Simbolo> simbolosGlobales = new HashMap<>();

    private final Map<String, String> tiposConocidos = new LinkedHashMap<>();
    private final Map<String, String> declaracionesLocales = new LinkedHashMap<>();

    /** Constructor sin ámbito global (retrocompatible). */
    public InferenciaTiposC(List<Cuadrupla> cuadruplas,
                            GeneradorC3D.Firma firma,
                            Map<String, GeneradorC3D.Firma> firmasPrograma,
                            List<Simbolo> tiposDefinidos) {
        this(cuadruplas, firma, firmasPrograma, tiposDefinidos, null);
    }

    /**
     * Constructor completo.
     *
     * ambitoGlobal ámbito del programa (con las variables globales del .pig,
     *                     si aplica). Puede ser null.
     */
    public InferenciaTiposC(List<Cuadrupla> cuadruplas,
                            GeneradorC3D.Firma firma,
                            Map<String, GeneradorC3D.Firma> firmasPrograma,
                            List<Simbolo> tiposDefinidos,
                            AmbitoGlobal ambitoGlobal) {
        this.firma = firma;
        this.firmasPrograma = firmasPrograma;
        if (tiposDefinidos != null) {
            for (Simbolo s : tiposDefinidos) {
                if (s != null && s.getNombre() != null) {
                    tiposPorNombre.put(s.getNombre(), s);
                }
            }
        }
        if (ambitoGlobal != null) {
            for (Simbolo s : ambitoGlobal.simbolosLocales()) {
                if (s != null && s.getNombre() != null) {
                    simbolosGlobales.put(s.getNombre(), s);
                }
            }
        }
        registrarParametros();
        if (cuadruplas != null) {
            for (Cuadrupla c : cuadruplas) {
                c.aceptar(this);
            }
        }
    }

    public Map<String, String> getDeclaraciones() { return declaracionesLocales; }

    public List<String> comoLineasDeC() {
        List<String> lineas = new ArrayList<>();
        for (Map.Entry<String, String> e : declaracionesLocales.entrySet()) {
            lineas.add(e.getValue() + " " + e.getKey() + ";");
        }
        return lineas;
    }

    private void registrarParametros() {
        if (firma == null || firma.parametros() == null) return;
        for (GeneradorC3D.ParametroFirma p : firma.parametros()) {
            tiposConocidos.put(p.nombre(), tipoAC(p.tipo()));
        }
    }

    //Visitor

    @Override
    public Void visitar(CuadruplaBinaria c) {
        String aTipo = tipoDeConLiterales(c.a());
        String bTipo = tipoDeConLiterales(c.b());
        boolean aEsString = "char*".equals(aTipo);
        boolean bEsString = "char*".equals(bTipo);

        String tipo;
        if ("+".equals(c.operador()) && (aEsString || bEsString)) {
            tipo = "char*";
        } else if (esAritmetico(c.operador())) {
            tipo = masAncho(aTipo, bTipo);
        } else {
            tipo = "int";
        }
        declararSiNuevo(c.t(), tipo);
        return null;
    }

    @Override
    public Void visitar(CuadruplaUnaria c) {
        String op = c.operador();
        String tipo = ("not".equals(op) || "!".equals(op)) ? "int" : tipoDeConLiterales(c.a());
        declararSiNuevo(c.t(), tipo);
        return null;
    }

    @Override
    public Void visitar(CuadruplaAsignacion c) {
        declararSiNuevo(c.destino(), tipoDeConLiterales(c.valor()));
        return null;
    }

    @Override
    public Void visitar(CuadruplaCall c) {
        String tipo = "int";
        if (firmasPrograma != null && c.funcion() != null) {
            GeneradorC3D.Firma f = firmasPrograma.get(c.funcion());
            if (f != null && f.tipoRetorno() != null) {
                tipo = tipoAC(f.tipoRetorno());
            }
        }
        if (c.destino() != null && !"void".equals(tipo)) {
            declararSiNuevo(c.destino(), tipo);
        }
        return null;
    }

    @Override
    public Void visitar(CuadruplaRead c) {
        declararSiNuevo(c.destino(), TraductorTipos.nombreFuenteAC(c.tipo()));
        return null;
    }

    @Override
    public Void visitar(CuadruplaNew c) {
        declararSiNuevo(c.destino(), c.clase() + "*");
        return null;
    }

    @Override
    public Void visitar(CuadruplaNewArray c) {
        String tipoC = TraductorTipos.nombreFuenteAC(c.tipoElemento().replace("[]", "")) + "*";
        declararSiNuevo(c.destino(), tipoC);
        return null;
    }

    /**
     * destino = arreglo[idx]. Tipo del elemento = tipo del arreglo menos
     * un nivel de indirección. Si el arreglo no está en locales, se consulta el
     * ámbito global (caso típico: personas[i] donde personas es
     * una global del .pig).
     */
    @Override
    public Void visitar(CuadruplaIndiceCarga c) {
        String tipoArr = tiposConocidos.get(c.arreglo());
        if (tipoArr == null) {
            Simbolo sGlobal = simbolosGlobales.get(c.arreglo());
            if (sGlobal != null && sGlobal.getTipo() != null) {
                tipoArr = tipoAC(sGlobal.getTipo());
            }
        }
        String tipoElem = "int";
        if (tipoArr != null && tipoArr.endsWith("*")) {
            tipoElem = tipoArr.substring(0, tipoArr.length() - 1);
        }
        declararSiNuevo(c.destino(), tipoElem);
        return null;
    }

    @Override
    public Void visitar(CuadruplaCampoCarga c) {
        declararSiNuevo(c.destino(), resolverTipoCampo(c.objeto(), c.campo()));
        return null;
    }

    @Override public Void visitar(CuadruplaGoto c)        { return null; }
    @Override public Void visitar(CuadruplaIfFalse c)     { return null; }
    @Override public Void visitar(CuadruplaIfTrue c)      { return null; }
    @Override public Void visitar(CuadruplaEtiqueta c)    { return null; }
    @Override public Void visitar(CuadruplaPrint c)       { return null; }
    @Override public Void visitar(CuadruplaParam c)       { return null; }
    @Override public Void visitar(CuadruplaReturn c)      { return null; }
    @Override public Void visitar(CuadruplaBeginFunc c)   { return null; }
    @Override public Void visitar(CuadruplaEndFunc c)     { return null; }
    @Override public Void visitar(CuadruplaIndiceGuarda c){ return null; }
    @Override public Void visitar(CuadruplaCampoGuarda c) { return null; }

    @Override
    public Void visitar(CuadruplaConcat c) {
        declararSiNuevo(c.t(), "char*");
        return null;
    }

    @Override
    public Void visitar(CuadruplaCompCadena c) {
        declararSiNuevo(c.t(), "int");
        return null;
    }

    @Override
    public Void visitar(CuadruplaConversion c) {
        declararSiNuevo(c.destino(), TraductorTipos.nombreFuenteAC(c.tipoDestino()));
        return null;
    }

    private String resolverTipoCampo(String objeto, String campo) {
        if (objeto == null || campo == null) return "int";

        String tipoObjC = tiposConocidos.get(objeto);
        if (tipoObjC == null) {
            Simbolo sGlobal = simbolosGlobales.get(objeto);
            if (sGlobal != null && sGlobal.getTipo() != null) {
                tipoObjC = tipoAC(sGlobal.getTipo());
            }
        }
        if (tipoObjC == null) return "int";

        String nombreTipo = tipoObjC.endsWith("*")
                ? tipoObjC.substring(0, tipoObjC.length() - 1)
                : tipoObjC;

        Simbolo sTipo = tiposPorNombre.get(nombreTipo);
        if (sTipo == null) return "int";

        Simbolo sCampo = sTipo.buscarMiembro(campo);
        if (sCampo == null) return "int";

        return tipoAC(sCampo.getTipo());
    }

    private void declararSiNuevo(String lugar, String tipoC) {
        if (lugar == null) return;
        if (tiposConocidos.containsKey(lugar)) {
            String actual = tiposConocidos.get(lugar);
            if ("void*".equals(actual) && tipoC != null && !"void*".equals(tipoC)) {
                tiposConocidos.put(lugar, tipoC);
                declaracionesLocales.put(lugar, tipoC);
            }
            return;
        }
            tiposConocidos.put(lugar, tipoC);
        declaracionesLocales.put(lugar, tipoC);
    }

    private String tipoDe(String lugar) {
        if (lugar == null) return "int";
        String t = tiposConocidos.get(lugar);
        if (t != null) return t;
        Simbolo sGlobal = simbolosGlobales.get(lugar);
        if (sGlobal != null && sGlobal.getTipo() != null) return tipoAC(sGlobal.getTipo());
        return "int";
    }

    private String tipoDeConLiterales(String lugar) {
        if (lugar == null) return "int";
        if (lugar.length() >= 2 && lugar.startsWith("\"") && lugar.endsWith("\"")) return "char*";
        if (lugar.length() >= 2 && lugar.startsWith("'") && lugar.endsWith("'")) return "char";
        if ("true".equals(lugar) || "false".equals(lugar)) return "int";
        if ("null".equals(lugar) || "NULL".equals(lugar)) return "void*";
        if (lugar.matches("-?\\d+\\.\\d+")) return "double";
        if (lugar.matches("-?\\d+")) return "int";
        return tipoDe(lugar);
    }

    private static boolean esAritmetico(String op) {
        return op != null && (
                op.equals("+") || op.equals("-") || op.equals("*")
                        || op.equals("/") || op.equals("%")
        );
    }

    private static String masAncho(String a, String b) {
        if ("double".equals(a) || "double".equals(b)) return "double";
        return "int";
    }

    private static String tipoAC(Tipo t) {
        if (t == null) return "int";
        if (t == TipoPrimitivo.ENTERO)      return "int";
        if (t == TipoPrimitivo.FLOTANTE)    return "double";
        if (t == TipoPrimitivo.CARACTER)    return "char";
        if (t == TipoPrimitivo.CADENA)      return "char*";
        if (t == TipoPrimitivo.BOOL)        return "int";
        if (t == TipoPrimitivo.VOID)        return "void";
        if (t == TipoPrimitivo.NULO)        return "void*";
        if (t == TipoPrimitivo.DESCONOCIDO) return "int";
        if (t instanceof TipoClase tc)      return tc.getDefinicion().getNombre() + "*";
        if (t instanceof TipoEstructura te) return te.getDefinicion().getNombre() + "*";
        if (t instanceof TipoArreglo ta)    return tipoAC(ta.getBase()) + "*";
        return "int";
    }
}