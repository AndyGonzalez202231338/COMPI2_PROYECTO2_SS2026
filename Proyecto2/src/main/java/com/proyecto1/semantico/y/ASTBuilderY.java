package com.proyecto1.semantico.y;

import com.proyecto1.GramaticaY;
import com.proyecto1.GramaticaYBaseVisitor;
import com.proyecto1.semantico.LiteralUtil;
import com.proyecto1.semantico.ast.NodoAST;
// Cada nodo del AST de Y? (Programa, Estructura, Asignacion, Binaria, Literal, etc.)
// es ahora su propia clase pública en com.proyecto1.semantico.ast.y (una por archivo,
// para que cada una sea completamente independiente). Este visitor usa casi todas, así
// que se importa el paquete completo en vez de ~27 imports individuales uno por uno.
import com.proyecto1.semantico.ast.y.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class ASTBuilderY extends GramaticaYBaseVisitor<NodoAST> {

    /** Punto de entrada: úsese como {@code new ASTBuilderY().construir(parser.programa())}. */
    public Programa construir(GramaticaY.ProgramaContext arbol) {
        return (Programa) visit(arbol);
    }

    // POSICIÓN: todo nodo del AST propio necesita línea/columna de origen.
    private int linea(ParserRuleContext ctx) { return ctx.getStart().getLine(); }
    private int columna(ParserRuleContext ctx) { return ctx.getStart().getCharPositionInLine(); }


    /** PROGRAMA / ESTRUCTURAS / FUNCIONES
     *  programa
     *     : seccionEstructuras? seccionFunciones? EOF                          #programaDef
     *     ;
     *  definición de estructuras globales, la sección es opcional
     */
    @Override
    public NodoAST visitProgramaDef(GramaticaY.ProgramaDefContext ctx) {
        List<Estructura> estructuras = new ArrayList<>();
        if (ctx.seccionEstructuras() != null) {
            /** seccionEstructuras tiene una sola alternativa (#seccionEstructurasDef),
             * así que el cast es seguro: no hay otra forma en la que pudo haberse
             * construido este contexto.
             **/
            var seccion = (GramaticaY.SeccionEstructurasDefContext) ctx.seccionEstructuras();
            for (GramaticaY.DefinicionEstructuraContext def : seccion.definicionEstructura()) {
                estructuras.add(construirEstructura((GramaticaY.EstructuraDefContext) def));
            }
        }

        List<Funcion> funciones = new ArrayList<>();
        if (ctx.seccionFunciones() != null) {
            var seccion = (GramaticaY.SeccionFuncionesDefContext) ctx.seccionFunciones();
            for (GramaticaY.DefinicionFuncionContext def : seccion.definicionFuncion()) {
                funciones.add(construirFuncion((GramaticaY.FuncionDefContext) def));
            }
        }

        return new Programa(estructuras, funciones, linea(ctx), columna(ctx));
    }

    /**
     * definicionEstructura
     *     : ESTRUCTURA ID DOSPUNTOS NEWLINE INDENT campoEstructura+ DEDENT      #estructuraDef
     *     ;
     * estructura Persona:
     *  entero edad
     *  cadena nombre
     *  flotante promedio
     * @param ctx
     * @return
     */
    private Estructura construirEstructura(GramaticaY.EstructuraDefContext ctx) {
        List<CampoEstructura> campos = new ArrayList<>();
        for (GramaticaY.CampoEstructuraContext c : ctx.campoEstructura()) {
            campos.add(construirCampoEstructura((GramaticaY.CampoDefContext) c));
        }
        return new Estructura(ctx.ID().getText(), campos, linea(ctx), columna(ctx));
    }

    /**
     * campoEstructura
     *     : tipo ID (CORIZQ ENTERO_LIT CORDER)* NEWLINE                        #campoDef
     *     ;
     *  caracter letra     o     entero miArray[10]
     * @param ctx
     * @return
     */
    private CampoEstructura construirCampoEstructura(GramaticaY.CampoDefContext ctx) {
        NodoTipoRef tipo = construirTipoRef(ctx.tipo());
        List<Integer> tamanos = tamanosArreglo(ctx.ENTERO_LIT());
        return new CampoEstructura(tipo, ctx.ID().getText(), tamanos, linea(ctx), columna(ctx));
    }

    /**
     * definicionFuncion
     *     : DEFINIR ID LPAREN listaParametros? RPAREN (FLECHA tipo)? bloque     #funcionDef
     *     ;
     *
     * @param ctx
     * @return
     */
    private Funcion construirFuncion(GramaticaY.FuncionDefContext ctx) {
        List<Parametro> parametros = new ArrayList<>();
        if (ctx.listaParametros() != null) {
            var lista = (GramaticaY.ParametrosDefContext) ctx.listaParametros();
            for (GramaticaY.ParametroContext p : lista.parametro()) {
                // parametro sí tiene 3 alternativas -> se despacha con visit(), que
                // llamará a visitParametroPrimitivo/Arreglo/Estructura según toque.
                parametros.add((Parametro) visit(p));
            }
        }
        // "(FLECHA tipo)?" : si no hubo flecha, tipo() da null -> función void.
        NodoTipoRef tipoRetorno = (ctx.tipo() == null) ? null : construirTipoRef(ctx.tipo());
        Bloque cuerpo = construirBloque(ctx.bloque());
        return new Funcion(ctx.ID().getText(), parametros, tipoRetorno, cuerpo,
                linea(ctx), columna(ctx));
    }

    /** Lee los ENTERO_LIT capturados por "(CORIZQ ENTERO_LIT CORDER)*" como enteros ya parseados. */
    private List<Integer> tamanosArreglo(List<TerminalNode> literalesEntero) {
        if (literalesEntero.isEmpty()) return Collections.emptyList();
        List<Integer> tamanos = new ArrayList<>(literalesEntero.size());
        for (TerminalNode t : literalesEntero) tamanos.add(Integer.parseInt(t.getText()));
        return tamanos;
    }

    /**
     * PARÁMETROS
     * parametro tiene 3 alternativas -> 3 overrides, uno por etiqueta.
     * vairable, arreglos, estructuras
     * parametro
     *     : CORIZQ CORDER tipo ID                                              #parametroArreglo
     *     | LLAVEIZQ LLAVEDER ID ID                                            #parametroEstructura
     *     | tipo ID                                                            #parametroPrimitivo
     *     ;
     * @param ctx the parse tree
     * @return
     */
    @Override
    public NodoAST visitParametroPrimitivo(GramaticaY.ParametroPrimitivoContext ctx) {
        NodoTipoRef tipo = construirTipoRef(ctx.tipo());
        return Parametro.primitivo(tipo, ctx.ID().getText(), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitParametroArreglo(GramaticaY.ParametroArregloContext ctx) {
        NodoTipoRef tipoElemento = construirTipoRef(ctx.tipo());
        return Parametro.arreglo(tipoElemento, ctx.ID().getText(), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitParametroEstructura(GramaticaY.ParametroEstructuraContext ctx) {
        // "LLAVEIZQ LLAVEDER ID ID": el primer ID es el nombre del TIPO estructura,
        // el segundo es el nombre del parámetro. ctx.ID() da los dos en orden.
        List<TerminalNode> ids = ctx.ID();
        return Parametro.estructura(ids.get(0).getText(), ids.get(1).getText(),
                linea(ctx), columna(ctx));
    }

    // TIPOS  (regla 'tipo': 6 alternativas)
    /**
     * Como {@code tipo} tiene 6 alternativas, el despacho correcto es {@code visit(ctx)}
     * (no un cast manual): así ANTLR decide solo cuál de las 6 visitTipoXxx llamar.
     */
    private NodoTipoRef construirTipoRef(GramaticaY.TipoContext ctx) {
        return (NodoTipoRef) visit(ctx);
    }

    @Override public NodoAST visitTipoEntero(GramaticaY.TipoEnteroContext ctx) {
        return new NodoTipoRef("entero", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoFlotante(GramaticaY.TipoFlotanteContext ctx) {
        return new NodoTipoRef("flotante", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoCaracter(GramaticaY.TipoCaracterContext ctx) {
        return new NodoTipoRef("caracter", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoCadena(GramaticaY.TipoCadenaContext ctx) {
        return new NodoTipoRef("cadena", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoBool(GramaticaY.TipoBoolContext ctx) {
        return new NodoTipoRef("bool", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoEstructura(GramaticaY.TipoEstructuraContext ctx) {
        // Aquí el ID es el nombre de una estructura; si en verdad existe una
        // estructura con ese nombre definida antes se valida en la semántica),
        return new NodoTipoRef(ctx.ID().getText(), false, linea(ctx), columna(ctx));
    }


    // BLOQUES
    private Bloque construirBloque(GramaticaY.BloqueContext ctx) {
        var def = (GramaticaY.BloqueDefContext) ctx; // única alternativa
        List<InstruccionY> instrucciones = new ArrayList<>();
        for (GramaticaY.InstruccionContext i : def.instruccion()) {
            instrucciones.add((InstruccionY) visit(i));
        }
        return new Bloque(instrucciones, linea(ctx), columna(ctx));
    }

    private Bloque construirBloqueSimple(GramaticaY.BloqueSimpleContext ctx) {
        var def = (GramaticaY.BloqueSimpleDefContext) ctx; // única alternativa
        List<InstruccionY> instrucciones = new ArrayList<>();
        for (GramaticaY.InstruccionContext i : def.instruccion()) {
            instrucciones.add((InstruccionY) visit(i));
        }
        return new Bloque(instrucciones, linea(ctx), columna(ctx));
    }

    // INSTRUCCIONES  (regla 'instruccion': 12 alternativas -> 12 overrides)
    @Override
    public NodoAST visitInstDeclaracion(GramaticaY.InstDeclaracionContext ctx) {
        return construirDeclaracionVariable((GramaticaY.DeclVarDefContext) ctx.declaracionVariable());
    }

    @Override
    public NodoAST visitInstAsignacion(GramaticaY.InstAsignacionContext ctx) {
        return construirAsignacion((GramaticaY.AsigDefContext) ctx.asignacion());
    }

    @Override
    public NodoAST visitInstRetorno(GramaticaY.InstRetornoContext ctx) {
        ExpresionY valor = (ctx.expresion() == null) ? null : construirExpresion(ctx.expresion());
        return new Retorno(valor, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitInstSi(GramaticaY.InstSiContext ctx) {
        return construirSi((GramaticaY.CondicionSiDefContext) ctx.instruccionSi());
    }

    @Override
    public NodoAST visitInstElegir(GramaticaY.InstElegirContext ctx) {
        return construirElegir((GramaticaY.CondicionElegirDefContext) ctx.instruccionElegir());
    }

    @Override
    public NodoAST visitInstPara(GramaticaY.InstParaContext ctx) {
        return construirPara((GramaticaY.CicloParaDefContext) ctx.instruccionPara());
    }

    @Override
    public NodoAST visitInstMientras(GramaticaY.InstMientrasContext ctx) {
        return construirMientras((GramaticaY.CicloMientrasDefContext) ctx.instruccionMientras());
    }

    @Override
    public NodoAST visitInstHacerMientras(GramaticaY.InstHacerMientrasContext ctx) {
        return construirHacerMientras((GramaticaY.CicloHacerMientrasDefContext) ctx.instruccionHacerMientras());
    }

    @Override
    public NodoAST visitInstImprimir(GramaticaY.InstImprimirContext ctx) {
        List<ExpresionY> argumentos = new ArrayList<>();
        for (GramaticaY.ExpresionContext e : ctx.expresion()) argumentos.add(construirExpresion(e));
        return new Imprimir(argumentos, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitInstContinuar(GramaticaY.InstContinuarContext ctx) {
        return new Continuar(linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitInstRomper(GramaticaY.InstRomperContext ctx) {
        return new Romper(linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitInstExpresion(GramaticaY.InstExpresionContext ctx) {
        return new ExpresionStmt(construirExpresion(ctx.expresion()), linea(ctx), columna(ctx));
    }

    // --- auxiliares de instrucción, reutilizados también dentro de "para" ---
    /**
     * "tipo ID", "entero miEntero" o "entero miEntero = 5"
     * declaracionVariable
     *     : tipo ID (CORIZQ ENTERO_LIT CORDER)* (ASIGNAR expresion)?           #declVarDef
     *     ;
     * @param ctx
     * @return
     */
    private DeclaracionVariable construirDeclaracionVariable(GramaticaY.DeclVarDefContext ctx) {
        NodoTipoRef tipo = construirTipoRef(ctx.tipo());
        List<Integer> tamanos = tamanosArreglo(ctx.ENTERO_LIT());
        ExpresionY inicializador = (ctx.expresion() == null) ? null : construirExpresion(ctx.expresion());
        return new DeclaracionVariable(tipo, ctx.ID().getText(), tamanos, inicializador,
                linea(ctx), columna(ctx));
    }

    /**
     * Construye una Asignacion completa (objetivo + operador + valor). El objetivo
     * (lado izquierdo) se arma en {@link #construirObjetivoAsignacion}; ver esa
     * documentación para el porqué de recorrer los hijos "a mano" en ese caso
     * puntual.
     * asignacion
     *     : ID (PUNTO ID | CORIZQ expresion CORDER)* operadorAsignacion expresion #asigDef
     *     ;
     */
    private Asignacion construirAsignacion(GramaticaY.AsigDefContext ctx) {
        ExpresionY objetivo = construirObjetivoAsignacion(ctx);
        String operador = ctx.operadorAsignacion().getText(); // "=", "+=", "-=", "*=", "/="
        // ctx.expresion() devuelve TODAS las expresiones de esta alternativa: tanto
        // los índices "[expr]" del lado izquierdo (si hay) como la expresión del lado
        // derecho, en orden de aparición en el texto. La del lado derecho es, por
        // construcción de la gramática, siempre la ÚLTIMA de la lista.
        List<GramaticaY.ExpresionContext> todas = ctx.expresion();
        ExpresionY valor = construirExpresion(todas.get(todas.size() - 1));
        return new Asignacion(objetivo, operador, valor, linea(ctx), columna(ctx));
    }

    /**
     * Reconstruye el lado izquierdo de una asignación: {@code ID (PUNTO ID |
     * CORIZQ expresion CORDER)*}.
     *
     * <p><b>Por qué no basta con los accessors normales de ANTLR:</b> dentro de esa
     * repetición hay DOS alternativas posibles en cada vuelta (".campo" o "[índice]"),
     * mezcladas libremente (p. ej. "matriz[0].fila[1]" combina ambas varias veces).
     * ANTLR sí genera accessors ({@code ctx.ID()}, {@code ctx.expresion()}), pero cada
     * uno junta TODAS las coincidencias de su tipo en una sola lista sin importar en
     * qué orden ni con qué alternativa aparecieron — no dicen "el segundo ID vino
     * después de un PUNTO" o "vino después de un CORIZQ". Por eso aquí se recorren
     * los hijos directos del contexto en su orden real (getChild) y se decide, token
     * por token, si toca envolver el resultado parcial en un
     * {@link AccesoCampo} o en un {@link Indice}.
     */
    private ExpresionY construirObjetivoAsignacion(GramaticaY.AsigDefContext ctx) {
        TerminalNode primerId = (TerminalNode) ctx.getChild(0);
        ExpresionY objetivo = new Identificador(primerId.getText(),
                primerId.getSymbol().getLine(), primerId.getSymbol().getCharPositionInLine());

        int i = 1;
        while (i < ctx.getChildCount()) {
            ParseTree hijo = ctx.getChild(i);
            if (!(hijo instanceof TerminalNode token)) break; // llegamos a operadorAsignacion: paramos

            int tipoToken = token.getSymbol().getType();
            if (tipoToken == GramaticaY.PUNTO) {
                TerminalNode idCampo = (TerminalNode) ctx.getChild(i + 1);
                objetivo = new AccesoCampo(objetivo, idCampo.getText(),
                        token.getSymbol().getLine(), token.getSymbol().getCharPositionInLine());
                i += 2; // PUNTO, ID
            } else if (tipoToken == GramaticaY.CORIZQ) {
                // Cast normal (no genérico): "unchecked" no aplica aquí, por eso no
                // lleva @SuppressWarnings si el hijo no fuera en verdad una
                // ExpresionContext, esto lanzaría ClassCastException, lo cual solo
                // podría pasar si la gramática cambiara sin actualizar este visitor.
                GramaticaY.ExpresionContext expresionIndice = (GramaticaY.ExpresionContext) ctx.getChild(i + 1);
                ExpresionY indice = construirExpresion(expresionIndice);
                objetivo = new Indice(objetivo, indice,
                        token.getSymbol().getLine(), token.getSymbol().getCharPositionInLine());
                i += 3; // CORIZQ, expresion, CORDER
            } else {
                break; // no era ni PUNTO ni CORIZQ -> es el token de operadorAsignacion
            }
        }
        return objetivo;
    }

    private Si construirSi(GramaticaY.CondicionSiDefContext ctx) {
        /**
         * "SI (cond) ENTONCES bloqueSimple  (SINO (cond) ENTONCES bloqueSimple)*  (CONTRARIO bloqueSimple)?"
         * expresion() junta las condiciones de "si" + todos los "sino" (nunca incluye
         * "contrario", que no tiene condición). bloqueSimple() junta los cuerpos de
         * "si" + todos los "sino" + (si existe) el de "contrario", en ese orden. La
         * presencia del token CONTRARIO es lo único que permite saber si el ÚLTIMO
         * bloqueSimple de la lista es una rama más o es el "contrario" final.
         */
        List<GramaticaY.ExpresionContext> condiciones = ctx.expresion();
        List<GramaticaY.BloqueSimpleContext> cuerpos = ctx.bloqueSimple();
        boolean tieneContrario = ctx.CONTRARIO() != null;

        List<RamaSi> ramas = new ArrayList<>(condiciones.size());
        for (int i = 0; i < condiciones.size(); i++) {
            ExpresionY condicion = construirExpresion(condiciones.get(i));
            Bloque cuerpo = construirBloqueSimple(cuerpos.get(i));
            ramas.add(new RamaSi(condicion, cuerpo));
        }
        Bloque contrario = tieneContrario
                ? construirBloqueSimple(cuerpos.get(cuerpos.size() - 1))
                : null;
        return new Si(ramas, contrario, linea(ctx), columna(ctx));
    }

    private Elegir construirElegir(GramaticaY.CondicionElegirDefContext ctx) {
        ExpresionY control = construirExpresion(ctx.expresion());
        List<CasoElegir> casos = new ArrayList<>();
        for (GramaticaY.CasoElegirContext c : ctx.casoElegir()) {
            casos.add(construirCasoElegir((GramaticaY.CasoDefContext) c));
        }
        // "(SIEMPRE bloque)?": si no hubo "siempre", este bloque() (el de
        // instruccionElegir, no el de cada caso) da null.
        Bloque siempre = (ctx.bloque() == null) ? null : construirBloque(ctx.bloque());
        return new Elegir(control, casos, siempre, linea(ctx), columna(ctx));
    }

    private CasoElegir construirCasoElegir(GramaticaY.CasoDefContext ctx) {
        Literal valor = (Literal) visit(ctx.literal()); // literal: 3 alternativas
        Bloque cuerpo = construirBloque(ctx.bloque());
        return new CasoElegir(valor, cuerpo);
    }

    /**
     * "PARA (init? ; cond? ; act?) bloque". Igual que en
     * {@link #construirObjetivoAsignacion}: {@code declaracionVariable} solo puede
     * aparecer en "init" (accessor sin ambigüedad), pero {@code asignacion} y
     * {@code expresion} pueden aparecer TANTO en "init"/"act" (asignacion) COMO en
     * "cond"/"act" (expresion) es decir, la MISMA regla aparece en más de una
     * posición dentro de esta alternativa, así que sus accessors devuelven listas que
     * no dicen a qué posición pertenece cada elemento. La solución robusta es
     * recorrer los hijos directos y usar los dos PUNTOYCOMA de nivel superior como
     * separadores de las tres secciones.
     */
    private Para construirPara(GramaticaY.CicloParaDefContext ctx) {
        int n = ctx.getChildCount();
        int idxPrimerPYC = -1;
        int idxSegundoPYC = -1;
        for (int i = 0; i < n; i++) {
            ParseTree hijo = ctx.getChild(i);
            if (hijo instanceof TerminalNode token && token.getSymbol().getType() == GramaticaY.PUNTOYCOMA) {
                if (idxPrimerPYC == -1) idxPrimerPYC = i;
                else { idxSegundoPYC = i; break; }
            }
        }

        InstruccionY inicializacion = null; // hijos entre "(" y el primer ";"
        for (int i = 2; i < idxPrimerPYC; i++) {
            ParseTree hijo = ctx.getChild(i);
            if (hijo instanceof GramaticaY.DeclaracionVariableContext) {
                inicializacion = construirDeclaracionVariable((GramaticaY.DeclVarDefContext) hijo);
            } else if (hijo instanceof GramaticaY.AsignacionContext) {
                inicializacion = construirAsignacion((GramaticaY.AsigDefContext) hijo);
            }
        }

        ExpresionY condicion = null; // hijos entre el primer ";" y el segundo ";"
        for (int i = idxPrimerPYC + 1; i < idxSegundoPYC; i++) {
            ParseTree hijo = ctx.getChild(i);
            if (hijo instanceof GramaticaY.ExpresionContext expresionCtx) {
                condicion = construirExpresion(expresionCtx);
            }
        }

        InstruccionY actualizacion = null; // hijos entre el segundo ";" y ")"
        for (int i = idxSegundoPYC + 1; i < n; i++) {
            ParseTree hijo = ctx.getChild(i);
            if (hijo instanceof TerminalNode token && token.getSymbol().getType() == GramaticaY.RPAREN) break;
            if (hijo instanceof GramaticaY.AsignacionContext) {
                actualizacion = construirAsignacion((GramaticaY.AsigDefContext) hijo);
            } else if (hijo instanceof GramaticaY.ExpresionContext expresionCtx) {
                ExpresionY expr = construirExpresion(expresionCtx);
                actualizacion = new ExpresionStmt(expr, linea(expresionCtx), columna(expresionCtx));
            }
        }

        Bloque cuerpo = construirBloque(ctx.bloque());
        return new Para(inicializacion, condicion, actualizacion, cuerpo, linea(ctx), columna(ctx));
    }

    private Mientras construirMientras(GramaticaY.CicloMientrasDefContext ctx) {
        ExpresionY condicion = construirExpresion(ctx.expresion());
        Bloque cuerpo = construirBloqueSimple(ctx.bloqueSimple());
        return new Mientras(condicion, cuerpo, linea(ctx), columna(ctx));
    }

    private HacerMientras construirHacerMientras(GramaticaY.CicloHacerMientrasDefContext ctx) {
        Bloque cuerpo = construirBloque(ctx.bloque());
        ExpresionY condicion = construirExpresion(ctx.expresion());
        return new HacerMientras(cuerpo, condicion, linea(ctx), columna(ctx));
    }

    // EXPRESIONES

    /**
     * "expresion : expresionOr ;" se resuelve con este
     * auxiliar, que simplemente delega en expresionOr y sigue el despacho
     * normal de ahí hacia abajo.
     * @param ctx
     * @return
     */
    private ExpresionY construirExpresion(GramaticaY.ExpresionContext ctx) {
        return (ExpresionY) visit(ctx.expresionOr());
    }

    // --- Nivel 1: || ---
    @Override
    public NodoAST visitExpOrDef(GramaticaY.ExpOrDefContext ctx) {
        ExpresionY izq = (ExpresionY) visit(ctx.expresionOr());
        ExpresionY der = (ExpresionY) visit(ctx.expresionAnd());
        return new Binaria("||", izq, der, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpOrBase(GramaticaY.ExpOrBaseContext ctx) {
        return visit(ctx.expresionAnd());
    }

    // --- Nivel 2: && ---
    @Override
    public NodoAST visitExpAndDef(GramaticaY.ExpAndDefContext ctx) {
        ExpresionY izq = (ExpresionY) visit(ctx.expresionAnd());
        ExpresionY der = (ExpresionY) visit(ctx.expresionRelacional());
        return new Binaria("&&", izq, der, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpAndBase(GramaticaY.ExpAndBaseContext ctx) {
        return visit(ctx.expresionRelacional());
    }

    // --- Nivel 3: ==, !=, <, >, <=, >= (no encadenable: una sola alternativa con grupo opcional) ---
    @Override
    public NodoAST visitExpRelacionalDef(GramaticaY.ExpRelacionalDefContext ctx) {
        List<GramaticaY.ExpresionAditivaContext> lados = ctx.expresionAditiva();
        ExpresionY izq = (ExpresionY) visit(lados.get(0));
        if (lados.size() == 1) return izq; // no había operador relacional: pasa de largo

        ExpresionY der = (ExpresionY) visit(lados.get(1));
        String operador;
        if (ctx.IGUALIGUAL() != null) operador = "==";
        else if (ctx.DISTINTO() != null) operador = "!=";
        else if (ctx.MENORIGUAL() != null) operador = "<=";
        else if (ctx.MAYORIGUAL() != null) operador = ">=";
        else if (ctx.MENORQUE() != null) operador = "<";
        else operador = ">"; // MAYORQUE
        return new Binaria(operador, izq, der, linea(ctx), columna(ctx));
    }

    // --- Nivel 4: +, - ---
    @Override
    public NodoAST visitExpAditivaDef(GramaticaY.ExpAditivaDefContext ctx) {
        ExpresionY izq = (ExpresionY) visit(ctx.expresionAditiva());
        ExpresionY der = (ExpresionY) visit(ctx.expresionMultiplicativa());
        String operador = (ctx.MAS() != null) ? "+" : "-";
        return new Binaria(operador, izq, der, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpAditivaBase(GramaticaY.ExpAditivaBaseContext ctx) {
        return visit(ctx.expresionMultiplicativa());
    }

    // --- Nivel 5: *, /, % ---
    @Override
    public NodoAST visitExpMultiplicativaDef(GramaticaY.ExpMultiplicativaDefContext ctx) {
        ExpresionY izq = (ExpresionY) visit(ctx.expresionMultiplicativa());
        ExpresionY der = (ExpresionY) visit(ctx.expresionUnaria());
        String operador = (ctx.MULT() != null) ? "*" : (ctx.DIV() != null) ? "/" : "%";
        return new Binaria(operador, izq, der, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpMultiplicativaBase(GramaticaY.ExpMultiplicativaBaseContext ctx) {
        return visit(ctx.expresionUnaria());
    }

    // --- Nivel 6: !, -, ++, -- como PREFIJO ---
    @Override
    public NodoAST visitExpUnariaPrefijaDef(GramaticaY.ExpUnariaPrefijaDefContext ctx) {
        ExpresionY operando = (ExpresionY) visit(ctx.expresionUnaria());
        String operador;
        if (ctx.NEGACION() != null) operador = "!";
        else if (ctx.MENOS() != null) operador = "-";
        else if (ctx.INCREMENTO() != null) operador = "++";
        else operador = "--"; // DECREMENTO
        return new Unaria(operador, operando, true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpUnariaBase(GramaticaY.ExpUnariaBaseContext ctx) {
        return visit(ctx.expresionPostfija());
    }

    // --- Nivel 6 (continuación): ++, -- como POSTFIJO ---
    @Override
    public NodoAST visitExpPostfijaDef(GramaticaY.ExpPostfijaDefContext ctx) {
        ExpresionY base = (ExpresionY) visit(ctx.primaria());
        if (ctx.INCREMENTO() != null) return new Unaria("++", base, false, linea(ctx), columna(ctx));
        if (ctx.DECREMENTO() != null) return new Unaria("--", base, false, linea(ctx), columna(ctx));
        return base;
    }

    // --- Nivel 7: primaria (13 alternativas) ---

    @Override
    public NodoAST visitPrimariaCampo(GramaticaY.PrimariaCampoContext ctx) {
        ExpresionY objeto = (ExpresionY) visit(ctx.primaria());
        return new AccesoCampo(objeto, ctx.ID().getText(), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaIndice(GramaticaY.PrimariaIndiceContext ctx) {
        ExpresionY arreglo = (ExpresionY) visit(ctx.primaria());
        ExpresionY indice = construirExpresion(ctx.expresion());
        return new Indice(arreglo, indice, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaLlamada(GramaticaY.PrimariaLlamadaContext ctx) {
        ExpresionY objetivo = (ExpresionY) visit(ctx.primaria());
        List<ExpresionY> argumentos = new ArrayList<>();
        if (ctx.argumentos() != null) {
            var lista = (GramaticaY.ArgumentosDefContext) ctx.argumentos(); // única alternativa
            for (GramaticaY.ExpresionContext e : lista.expresion()) argumentos.add(construirExpresion(e));
        }
        return new Llamada(objetivo, argumentos, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaLeer(GramaticaY.PrimariaLeerContext ctx) {
        return new Leer(linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaParentesis(GramaticaY.PrimariaParentesisContext ctx) {
        // Los paréntesis solo agrupan; no necesitan su propio nodo en el AST: el
        // valor semántico es exactamente el de la expresión interior.
        return construirExpresion(ctx.expresion());
    }

    @Override
    public NodoAST visitPrimariaListaLiteral(GramaticaY.PrimariaListaLiteralContext ctx) {
        List<ExpresionY> elementos = new ArrayList<>();
        for (GramaticaY.ExpresionContext e : ctx.expresion()) elementos.add(construirExpresion(e));
        return new ListaLiteral(elementos, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaEntero(GramaticaY.PrimariaEnteroContext ctx) {
        long valor = LiteralUtil.aEntero(ctx.getText());
        return new Literal(valor, CategoriaLiteral.ENTERO, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaFlotante(GramaticaY.PrimariaFlotanteContext ctx) {
        double valor = LiteralUtil.aFlotante(ctx.getText());
        return new Literal(valor, CategoriaLiteral.FLOTANTE, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaCaracter(GramaticaY.PrimariaCaracterContext ctx) {
        char valor = LiteralUtil.aCaracter(ctx.getText());
        return new Literal(valor, CategoriaLiteral.CARACTER, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaCadena(GramaticaY.PrimariaCadenaContext ctx) {
        String valor = LiteralUtil.textoSinComillasNiEscapes(ctx.getText());
        return new Literal(valor, CategoriaLiteral.CADENA, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaVerdadero(GramaticaY.PrimariaVerdaderoContext ctx) {
        return new Literal(Boolean.TRUE, CategoriaLiteral.BOOLEANO, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaFalso(GramaticaY.PrimariaFalsoContext ctx) {
        return new Literal(Boolean.FALSE, CategoriaLiteral.BOOLEANO, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaIdentificador(GramaticaY.PrimariaIdentificadorContext ctx) {
        return new Identificador(ctx.getText(), linea(ctx), columna(ctx));
    }

    // LITERAL suelto (regla 'literal', SOLO se usa dentro de "caso <literal>:")

    /**
     * Es una regla distinta de 'primaria' (aunque también produce nuestro mismo
     * nodo Literal) porque en la gramática 'literal' es mucho más
     * restringida (solo entero/caracter/cadena, para los "caso" de un elegir).
     * @param ctx the parse tree
     * @return
     */
    @Override
    public NodoAST visitLitEntero(GramaticaY.LitEnteroContext ctx) {
        long valor = LiteralUtil.aEntero(ctx.getText());
        return new Literal(valor, CategoriaLiteral.ENTERO, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitLitCaracter(GramaticaY.LitCaracterContext ctx) {
        char valor = LiteralUtil.aCaracter(ctx.getText());
        return new Literal(valor, CategoriaLiteral.CARACTER, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitLitCadena(GramaticaY.LitCadenaContext ctx) {
        String valor = LiteralUtil.textoSinComillasNiEscapes(ctx.getText());
        return new Literal(valor, CategoriaLiteral.CADENA, linea(ctx), columna(ctx));
    }
}
