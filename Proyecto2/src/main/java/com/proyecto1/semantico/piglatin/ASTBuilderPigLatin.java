package com.proyecto1.semantico.piglatin;

import com.proyecto1.GramaticaPigLatin;
import com.proyecto1.GramaticaPigLatinBaseVisitor;

import com.proyecto1.semantico.LiteralUtil;
import com.proyecto1.semantico.ast.NodoAST;
import com.proyecto1.semantico.ast.piglatin.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class ASTBuilderPigLatin extends GramaticaPigLatinBaseVisitor<NodoAST> {

    /** Punto de entrada: {@code new ASTBuilderPigLatin().construir(parser.programa())}. */
    public Programa construir(GramaticaPigLatin.ProgramaContext arbol) {
        return (Programa) visit(arbol);
    }

    // POSICIÓN: todo nodo del AST propio necesita línea/columna de origen.
    private int linea(ParserRuleContext ctx) { return ctx.getStart().getLine(); }
    private int columna(ParserRuleContext ctx) { return ctx.getStart().getCharPositionInLine(); }

    /**
     * programa : importaciones? seccionVariables? funcionPrincipal EOF ;
     * La sección de variables no tiene su propio nodo (no aporta nada por sí
     * sola): sus declaraciones se guardan directamente en {@link Programa}. Se
     * recorre {@code seccionVariables.getChild(i)} en orden real porque las dos
     * variantes (variable / arreglo) pueden intercalarse.
     */
    @Override
    public NodoAST visitPrograma(GramaticaPigLatin.ProgramaContext ctx) {
        List<Importacion> importaciones = new ArrayList<>();
        if (ctx.importaciones() != null) {
            // La regla 'importaciones' tiene alt label (#importacionesDef), así que
            // el accessor devuelve la clase base ImportacionesContext, que NO tiene
            // el accessor .importacion(). Hay que castear a la subclase concreta.
            GramaticaPigLatin.ImportacionesDefContext imp =
                    (GramaticaPigLatin.ImportacionesDefContext) ctx.importaciones();
            for (GramaticaPigLatin.ImportacionContext i : imp.importacion()) {
                importaciones.add((Importacion) visit(i));
            }
        }

        List<InstruccionPigLatin> variables = new ArrayList<>();
        if (ctx.seccionVariables() != null) {
            GramaticaPigLatin.SeccionVariablesContext sec = ctx.seccionVariables();
            for (int i = 0; i < sec.getChildCount(); i++) {
                ParseTree child = sec.getChild(i);
                if (child instanceof GramaticaPigLatin.DeclaracionVariableContext
                        || child instanceof GramaticaPigLatin.DeclaracionArregloContext) {
                    variables.add((InstruccionPigLatin) visit(child));
                }
            }
        }

        FuncionPrincipal principal = (FuncionPrincipal) visit(ctx.funcionPrincipal());
        return new Programa(importaciones, variables, principal, linea(ctx), columna(ctx));
    }
    /**
     * importacion : IMPORT ID (PUNTO ID)*   #importacionDef ;
     * Se guarda como lista de segmentos en orden de aparición (el primero es el
     * módulo base, los siguientes vienen tras cada PUNTO).
     */
    @Override
    public NodoAST visitImportacionDef(GramaticaPigLatin.ImportacionDefContext ctx) {
        List<String> segmentos = new ArrayList<>();
        for (TerminalNode id : ctx.ID()) segmentos.add(id.getText());
        return new Importacion(segmentos, linea(ctx), columna(ctx));
    }

    /**
     * funcionPrincipal
     *     : MAIOR DOSMAYOR sentencia* FIN_PRINCIPAL PUNTOYCOMA
     *     #funcionPrincipalDef
     *     ;
     * El cuerpo son las sentencias que van entre la cabecera y "FINIS;".
     */
    @Override
    public NodoAST visitFuncionPrincipalDef(GramaticaPigLatin.FuncionPrincipalDefContext ctx) {
        List<InstruccionPigLatin> cuerpo = new ArrayList<>();
        for (GramaticaPigLatin.SentenciaContext s : ctx.sentencia()) {
            cuerpo.add((InstruccionPigLatin) visit(s));
        }
        return new FuncionPrincipal(cuerpo, linea(ctx), columna(ctx));
    }

    // BLOQUE

    /**
     * bloque : LLAVEIZQ sentencia* LLAVEDER  #bloqueDef ;
     * En PigLatin Bloque implementa InstruccionPigLatin (a diferencia de Y): un
     * bloque anidado suelto es una sentencia válida por sí misma.
     */
    @Override
    public NodoAST visitBloqueDef(GramaticaPigLatin.BloqueDefContext ctx) {
        List<InstruccionPigLatin> instrucciones = new ArrayList<>();
        for (GramaticaPigLatin.SentenciaContext s : ctx.sentencia()) {
            instrucciones.add((InstruccionPigLatin) visit(s));
        }
        return new Bloque(instrucciones, linea(ctx), columna(ctx));
    }

    // SENTENCIA

    @Override public NodoAST visitStmtBloque(GramaticaPigLatin.StmtBloqueContext ctx) {
        return visit(ctx.bloque());
    }
    @Override public NodoAST visitStmtSi(GramaticaPigLatin.StmtSiContext ctx) {
        return visit(ctx.sentenciaSi());
    }
    @Override public NodoAST visitStmtDum(GramaticaPigLatin.StmtDumContext ctx) {
        return visit(ctx.sentenciaDum());
    }
    @Override public NodoAST visitStmtFacere(GramaticaPigLatin.StmtFacereContext ctx) {
        return visit(ctx.sentenciaFacere());
    }
    @Override public NodoAST visitStmtPer(GramaticaPigLatin.StmtPerContext ctx) {
        return visit(ctx.sentenciaPer());
    }
    @Override public NodoAST visitStmtInterrumpe(GramaticaPigLatin.StmtInterrumpeContext ctx) {
        return visit(ctx.sentenciaInterrumpe());
    }
    @Override public NodoAST visitStmtPerge(GramaticaPigLatin.StmtPergeContext ctx) {
        return visit(ctx.sentenciaPerge());
    }
    @Override public NodoAST visitStmtDeclaracionVariable(GramaticaPigLatin.StmtDeclaracionVariableContext ctx) {
        return visit(ctx.declaracionVariable());
    }
    @Override public NodoAST visitStmtDeclaracionArreglo(GramaticaPigLatin.StmtDeclaracionArregloContext ctx) {
        return visit(ctx.declaracionArreglo());
    }
    @Override public NodoAST visitStmtImprimir(GramaticaPigLatin.StmtImprimirContext ctx) {
        return visit(ctx.sentenciaImprimir());
    }
    @Override public NodoAST visitStmtLeer(GramaticaPigLatin.StmtLeerContext ctx) {
        return visit(ctx.sentenciaLeer());
    }
    @Override public NodoAST visitStmtExpresion(GramaticaPigLatin.StmtExpresionContext ctx) {
        return visit(ctx.expresionSentencia());
    }
    @Override public NodoAST visitStmtVacia(GramaticaPigLatin.StmtVaciaContext ctx) {
        return visit(ctx.sentenciaVacia());
    }

    // SENTENCIAS ESPECÍFICAS

    /**
     * sentenciaSi
     *     : SI LPAREN expresion RPAREN bloque
     *       (ALITER LPAREN expresion RPAREN bloque)*
     *       (ALITER bloque)?
     *       FINIS PUNTOYCOMA
     *     ;
     *
     * Como "aliter con condición" y "aliter final sin condición" usan EL MISMO
     * token ALITER, se deduce cuál es el "contrario" contando:
     *   - condiciones.size() == bloques.size()      -> no hay "contrario".
     *   - condiciones.size() == bloques.size() - 1  -> el ÚLTIMO bloque es "contrario".
     * Los accessors devuelven cada uno su lista en ORDEN de aparición, así que la
     * correspondencia por índice es directa.
     */
    @Override
    public NodoAST visitSentenciaSiDef(GramaticaPigLatin.SentenciaSiDefContext ctx) {
        List<GramaticaPigLatin.ExpresionContext> condiciones = ctx.expresion();
        List<GramaticaPigLatin.BloqueContext>    bloques    = ctx.bloque();

        int numRamasConCond = condiciones.size();
        List<RamaSi> ramas = new ArrayList<>(numRamasConCond);
        for (int i = 0; i < numRamasConCond; i++) {
            ExpresionPigLatin cond = construirExpresion(condiciones.get(i));
            Bloque cuerpo = (Bloque) visit(bloques.get(i));
            ramas.add(new RamaSi(cond, cuerpo));
        }

        Bloque contrario = null;
        if (bloques.size() > numRamasConCond) {
            contrario = (Bloque) visit(bloques.get(bloques.size() - 1));
        }
        return new Si(ramas, contrario, linea(ctx), columna(ctx));
    }

    /**
     * sentenciaDum : DUM LPAREN expresion RPAREN bloque FINIS PUNTOYCOMA
     *   #sentenciaDumDef ;
     */
    @Override
    public NodoAST visitSentenciaDumDef(GramaticaPigLatin.SentenciaDumDefContext ctx) {
        ExpresionPigLatin cond = construirExpresion(ctx.expresion());
        Bloque cuerpo = (Bloque) visit(ctx.bloque());
        return new Dum(cond, cuerpo, linea(ctx), columna(ctx));
    }

    /**
     * sentenciaFacere : FACERE bloque DUM LPAREN expresion RPAREN PUNTOYCOMA
     *   #sentenciaFacereDef ;
     * Nota: NO lleva "finis;" — solo el ';' final tras la condición.
     */
    @Override
    public NodoAST visitSentenciaFacereDef(GramaticaPigLatin.SentenciaFacereDefContext ctx) {
        Bloque cuerpo = (Bloque) visit(ctx.bloque());
        ExpresionPigLatin cond = construirExpresion(ctx.expresion());
        return new Facere(cuerpo, cond, linea(ctx), columna(ctx));
    }

    /**
     * sentenciaPer
     *     : PER LPAREN inicializacionFor PUNTOYCOMA expresion? PUNTOYCOMA
     *           actualizacionFor? RPAREN bloque
     *     ;
     * "init" viene de {@code inicializacionFor} (declaración sin ';' o lista de
     * expresiones); "act" siempre es lista de expresiones. Ambos se guardan como
     * InstruccionPigLatin genérico (igual que en el {@code Per} de Y).
     */
    @Override
    public NodoAST visitSentenciaPerDef(GramaticaPigLatin.SentenciaPerDefContext ctx) {
        InstruccionPigLatin init = (InstruccionPigLatin) visit(ctx.inicializacionFor());
        ExpresionPigLatin   cond = (ctx.expresion() == null) ? null : construirExpresion(ctx.expresion());
        InstruccionPigLatin act  = (ctx.actualizacionFor() == null)
                ? null
                : (InstruccionPigLatin) visit(ctx.actualizacionFor());
        Bloque cuerpo = (Bloque) visit(ctx.bloque());
        return new Per(init, cond, act, cuerpo, linea(ctx), columna(ctx));
    }

    /** inicializacionFor : declaracionVariableSinPuntoYComa  #initForDeclaracion ; */
    @Override
    public NodoAST visitInitForDeclaracion(GramaticaPigLatin.InitForDeclaracionContext ctx) {
        return visit(ctx.declaracionVariableSinPuntoYComa());
    }

    /** inicializacionFor : listaExpresiones  #initForExpresiones ; */
    @Override
    public NodoAST visitInitForExpresiones(GramaticaPigLatin.InitForExpresionesContext ctx) {
        return visit(ctx.listaExpresiones());
    }

    /** actualizacionFor : listaExpresiones  #actualizacionForDef ; */
    @Override
    public NodoAST visitActualizacionForDef(GramaticaPigLatin.ActualizacionForDefContext ctx) {
        return visit(ctx.listaExpresiones());
    }

    /**
     * listaExpresiones : expresion (COMA expresion)*  #listaExpresionesDef ;
     * Se modela como nodo (ListaExpresiones implementa InstruccionPigLatin) porque
     * cuelga directamente de un Per, nunca de otra expresión.
     */
    @Override
    public NodoAST visitListaExpresionesDef(GramaticaPigLatin.ListaExpresionesDefContext ctx) {
        List<ExpresionPigLatin> expresiones = new ArrayList<>();
        for (GramaticaPigLatin.ExpresionContext e : ctx.expresion()) expresiones.add(construirExpresion(e));
        return new ListaExpresiones(expresiones, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitSentenciaInterrumpeDef(GramaticaPigLatin.SentenciaInterrumpeDefContext ctx) {
        return new Interrumpe(linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitSentenciaPergeDef(GramaticaPigLatin.SentenciaPergeDefContext ctx) {
        return new Perge(linea(ctx), columna(ctx));
    }

    /** sentenciaImprimir : DOSMAYOR expresion (DOSMAYOR expresion)* PUNTOYCOMA */
    @Override
    public NodoAST visitSentenciaImprimirDef(GramaticaPigLatin.SentenciaImprimirDefContext ctx) {
        List<ExpresionPigLatin> args = new ArrayList<>();
        for (GramaticaPigLatin.ExpresionContext e : ctx.expresion()) args.add(construirExpresion(e));
        return new Imprimir(args, linea(ctx), columna(ctx));
    }

    /**
     * sentenciaLeer : ID? DOSMENOR  #sentenciaLeerDef ;
     * El ID es opcional: null cuando se lee y se descarta el valor.
     */
    @Override
    public NodoAST visitSentenciaLeerDef(GramaticaPigLatin.SentenciaLeerDefContext ctx) {
        String variable = (ctx.ID() == null) ? null : ctx.ID().getText();
        return new Leer(variable, linea(ctx), columna(ctx));
    }

    /** expresionSentencia : expresion PUNTOYCOMA  #expresionSentenciaDef ; */
    @Override
    public NodoAST visitExpresionSentenciaDef(GramaticaPigLatin.ExpresionSentenciaDefContext ctx) {
        return new ExpresionStmt(construirExpresion(ctx.expresion()), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitSentenciaVaciaDef(GramaticaPigLatin.SentenciaVaciaDefContext ctx) {
        return new SentenciaVacia(linea(ctx), columna(ctx));
    }

    // DECLARACIONES DE VARIABLE Y ARREGLO

    /** declaracionVariable : declaracionVariableSinPuntoYComa PUNTOYCOMA  #declaracionVariableDef ; */
    @Override
    public NodoAST visitDeclaracionVariableDef(GramaticaPigLatin.DeclaracionVariableDefContext ctx) {
        return visit(ctx.declaracionVariableSinPuntoYComa());
    }

    /**
     * declaracionVariableSinPuntoYComa tiene 3 alternativas ya etiquetadas; las
     * tres devuelven el MISMO tipo (DeclaracionVariable) usando FÁBRICAS distintas
     * que garantizan que no se pueda construir, p. ej., una ESTRUCTURA sin su
     * inicializador.
     *
     * OJO: en PigLatin el inicializador de CON_TIPO viene SIN '=' antes de la
     * expresión ("esto x : numerus 5" es válido). Por eso basta chequear si
     * {@code ctx.expresion() == null}.
     */
    @Override
    public NodoAST visitDeclaracionVarConTipo(GramaticaPigLatin.DeclaracionVarConTipoContext ctx) {
        NodoTipoRef tipo = construirTipoRef(ctx.tipo());
        ExpresionPigLatin init = (ctx.expresion() == null) ? null : construirExpresion(ctx.expresion());
        return DeclaracionVariable.conTipo(ctx.ID().getText(), tipo, init, linea(ctx), columna(ctx));
    }

    /**
     * ESTO ID DOSPUNTOS ID inicializadorArreglo
     * El PRIMER ID es el nombre de la variable; el SEGUNDO es el nombre del tipo
     * (estructura importada). ctx.ID() los da en orden.
     */
    @Override
    public NodoAST visitDeclaracionVarEstructura(GramaticaPigLatin.DeclaracionVarEstructuraContext ctx) {
        List<TerminalNode> ids = ctx.ID();
        InicializadorArreglo ini = (InicializadorArreglo) visit(ctx.inicializadorArreglo());
        return DeclaracionVariable.estructura(ids.get(0).getText(), ids.get(1).getText(), ini,
                linea(ctx), columna(ctx));
    }

    /** ESTO ID DOSPUNTOS expresion  (sin tipo explícito; se infiere del valor). */
    @Override
    public NodoAST visitDeclaracionVarSoloValor(GramaticaPigLatin.DeclaracionVarSoloValorContext ctx) {
        ExpresionPigLatin init = construirExpresion(ctx.expresion());
        return DeclaracionVariable.soloValor(ctx.ID().getText(), init, linea(ctx), columna(ctx));
    }

    /**
     * declaracionArreglo
     *     : SERIES ID CORIZQ ENTERO_LIT CORDER DOSPUNTOS tipo
     *       inicializadorArreglo? PUNTOYCOMA
     *     ;
     * Tamaño parseado a int (no texto crudo); inicializador opcional; sin '='.
     */
    @Override
    public NodoAST visitDeclaracionArregloDef(GramaticaPigLatin.DeclaracionArregloDefContext ctx) {
        int tamano = Integer.parseInt(ctx.ENTERO_LIT().getText());
        NodoTipoRef tipo = construirTipoRef(ctx.tipo());
        InicializadorArreglo ini = (ctx.inicializadorArreglo() == null)
                ? null
                : (InicializadorArreglo) visit(ctx.inicializadorArreglo());
        return new DeclaracionArreglo(ctx.ID().getText(), tamano, tipo, ini,
                linea(ctx), columna(ctx));
    }

    /**
     * inicializadorArreglo
     *     : LLAVEIZQ expresion (COMA expresion)* LLAVEDER
     *     #inicializadorArregloDef
     *     ;
     * OJO: NO es opcional el primer elemento (la gramática exige ≥1 expresión).
     * El mismo nodo se reutiliza como expresión (alt #primariaListaLiteral).
     */
    @Override
    public NodoAST visitInicializadorArregloDef(GramaticaPigLatin.InicializadorArregloDefContext ctx) {
        List<ExpresionPigLatin> elementos = new ArrayList<>();
        for (GramaticaPigLatin.ExpresionContext e : ctx.expresion()) elementos.add(construirExpresion(e));
        return new InicializadorArreglo(elementos, linea(ctx), columna(ctx));
    }

    // TIPOS (regla 'tipo': 6 alternativas -> 6 overrides)

    /**
     * Como 'tipo' tiene 6 alternativas etiquetadas, se usa {@code visit(ctx)}
     * para despachar automáticamente al método correcto. Cada override devuelve
     * un NodoTipoRef con el nombre ya resuelto.
     */
    private NodoTipoRef construirTipoRef(GramaticaPigLatin.TipoContext ctx) {
        return (NodoTipoRef) visit(ctx);
    }

    @Override public NodoAST visitTipoNumerus(GramaticaPigLatin.TipoNumerusContext ctx) {
        return new NodoTipoRef("numerus", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoDecimalis(GramaticaPigLatin.TipoDecimalisContext ctx) {
        return new NodoTipoRef("decimalis", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoTextum(GramaticaPigLatin.TipoTextumContext ctx) {
        return new NodoTipoRef("textum", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoLittera(GramaticaPigLatin.TipoLitteraContext ctx) {
        return new NodoTipoRef("littera", true, linea(ctx), columna(ctx));
    }

    @Override public NodoAST visitTipoBool(GramaticaPigLatin.TipoBoolContext ctx) {
        return new NodoTipoRef("falsus", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoFalsus(GramaticaPigLatin.TipoFalsusContext ctx) {
        return new NodoTipoRef("falsus", true, linea(ctx), columna(ctx));
    }
    @Override public NodoAST visitTipoImportado(GramaticaPigLatin.TipoImportadoContext ctx) {
        // Un ID de clase/estructura importada; la validación contra la tabla de
        return new NodoTipoRef(ctx.ID().getText(), false, linea(ctx), columna(ctx));
    }



    private List<ExpresionPigLatin> construirListaArgumentos(GramaticaPigLatin.ListaArgumentosContext ctx) {
        List<ExpresionPigLatin> r = new ArrayList<>();
        if (ctx == null) return r;
        GramaticaPigLatin.ListaArgumentosDefContext def = (GramaticaPigLatin.ListaArgumentosDefContext) ctx;
        for (GramaticaPigLatin.ExpresionContext e : def.expresion()) r.add(construirExpresion(e));
        return r;
    }

    // EXPRESIONES

    /**
     * expresion : expresionAsignacion ; (regla SIN label) — se delega al nivel
     * de asignación, que sí tiene label.
     */
    private ExpresionPigLatin construirExpresion(GramaticaPigLatin.ExpresionContext ctx) {
        return (ExpresionPigLatin) visit(ctx.expresionAsignacion());
    }

    // --- Nivel 1: asignación (asociativa a la derecha) ---
    // expresionAsignacion
    //   : expresionCondicional (operadorAsignacion expresionAsignacion)?  #expresionAsignacionDef ;
    // Si NO hay operador, se devuelve el LHS tal cual; si lo hay, se envuelve en
    // Asignacion (que es una ExpresionPigLatin, no una instrucción).
    @Override
    public NodoAST visitExpresionAsignacionDef(GramaticaPigLatin.ExpresionAsignacionDefContext ctx) {
        ExpresionPigLatin izquierdo = (ExpresionPigLatin) visit(ctx.expresionCondicional());
        if (ctx.operadorAsignacion() == null) return izquierdo;

        String operador = ctx.operadorAsignacion().getText(); // "=", "+=", "-=", "*=", "/=", "%="
        ExpresionPigLatin derecho = (ExpresionPigLatin) visit(ctx.expresionAsignacion());
        return new Asignacion(izquierdo, operador, derecho, linea(ctx), columna(ctx));
    }

    // --- Nivel 2: ternario (asociativo a la derecha) ---
    // expresionCondicional
    //   : expresionOr (INTERROGACION expresion DOSPUNTOS expresionCondicional)?
    //   #expresionCondicionalDef ;
    @Override
    public NodoAST visitExpresionCondicionalDef(GramaticaPigLatin.ExpresionCondicionalDefContext ctx) {
        ExpresionPigLatin cond = (ExpresionPigLatin) visit(ctx.expresionOr());
        if (ctx.INTERROGACION() == null) return cond;
        ExpresionPigLatin siVerdadero = construirExpresion(ctx.expresion());
        ExpresionPigLatin siFalso = (ExpresionPigLatin) visit(ctx.expresionCondicional());
        return new Ternaria(cond, siVerdadero, siFalso, linea(ctx), columna(ctx));
    }

    // --- Nivel 3: || (un solo operador -> plegado directo) ---
    @Override
    public NodoAST visitExpresionOrDef(GramaticaPigLatin.ExpresionOrDefContext ctx) {
        List<GramaticaPigLatin.ExpresionAndContext> operandos = ctx.expresionAnd();
        ExpresionPigLatin resultado = (ExpresionPigLatin) visit(operandos.get(0));
        for (int i = 1; i < operandos.size(); i++) {
            ExpresionPigLatin der = (ExpresionPigLatin) visit(operandos.get(i));
            resultado = new Binaria("||", resultado, der, linea(ctx), columna(ctx));
        }
        return resultado;
    }

    // --- Nivel 4: && (un solo operador -> plegado directo) ---
    @Override
    public NodoAST visitExpresionAndDef(GramaticaPigLatin.ExpresionAndDefContext ctx) {
        List<GramaticaPigLatin.ExpresionIgualdadContext> operandos = ctx.expresionIgualdad();
        ExpresionPigLatin resultado = (ExpresionPigLatin) visit(operandos.get(0));
        for (int i = 1; i < operandos.size(); i++) {
            ExpresionPigLatin der = (ExpresionPigLatin) visit(operandos.get(i));
            resultado = new Binaria("&&", resultado, der, linea(ctx), columna(ctx));
        }
        return resultado;
    }

    // --- Nivel 5: ==, != (DOS operadores mezclados -> recorrer hijos) ---
    // Los accessors ctx.IGUALIGUAL() y ctx.DISTINTO() devuelven listas separadas
    // que NO dicen cuál salió primero. Por eso se recorre ctx.getChild(i) en su
    // orden real: cuando el hijo es el token ==/!=, se consume el siguiente
    // operando de la lista "operandos".
    @Override
    public NodoAST visitExpresionIgualdadDef(GramaticaPigLatin.ExpresionIgualdadDefContext ctx) {
        List<GramaticaPigLatin.ExpresionRelacionalContext> operandos = ctx.expresionRelacional();
        ExpresionPigLatin resultado = (ExpresionPigLatin) visit(operandos.get(0));
        int sigOperando = 1;
        for (int i = 0; i < ctx.getChildCount() && sigOperando < operandos.size(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode t)) continue;
            int tipo = t.getSymbol().getType();
            String op;
            if      (tipo == GramaticaPigLatin.IGUALIGUAL) op = "==";
            else if (tipo == GramaticaPigLatin.DISTINTO)   op = "!=";
            else continue;
            ExpresionPigLatin der = (ExpresionPigLatin) visit(operandos.get(sigOperando++));
            resultado = new Binaria(op, resultado, der,
                    t.getSymbol().getLine(), t.getSymbol().getCharPositionInLine());
        }
        return resultado;
    }

    // --- Nivel 6: <, >, <=, >= (CUATRO operadores mezclados -> recorrer hijos) ---
    @Override
    public NodoAST visitExpresionRelacionalDef(GramaticaPigLatin.ExpresionRelacionalDefContext ctx) {
        List<GramaticaPigLatin.ExpresionAditivaContext> operandos = ctx.expresionAditiva();
        ExpresionPigLatin resultado = (ExpresionPigLatin) visit(operandos.get(0));
        int sigOperando = 1;
        for (int i = 0; i < ctx.getChildCount() && sigOperando < operandos.size(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode t)) continue;
            int tipo = t.getSymbol().getType();
            String op;
            if      (tipo == GramaticaPigLatin.MENORQUE)   op = "<";
            else if (tipo == GramaticaPigLatin.MAYORQUE)   op = ">";
            else if (tipo == GramaticaPigLatin.MENORIGUAL) op = "<=";
            else if (tipo == GramaticaPigLatin.MAYORIGUAL) op = ">=";
            else continue;
            ExpresionPigLatin der = (ExpresionPigLatin) visit(operandos.get(sigOperando++));
            resultado = new Binaria(op, resultado, der,
                    t.getSymbol().getLine(), t.getSymbol().getCharPositionInLine());
        }
        return resultado;
    }

    // --- Nivel 7: +, - (DOS operadores mezclados -> recorrer hijos) ---
    @Override
    public NodoAST visitExpresionAditivaDef(GramaticaPigLatin.ExpresionAditivaDefContext ctx) {
        List<GramaticaPigLatin.ExpresionMultiplicativaContext> operandos = ctx.expresionMultiplicativa();
        ExpresionPigLatin resultado = (ExpresionPigLatin) visit(operandos.get(0));
        int sigOperando = 1;
        for (int i = 0; i < ctx.getChildCount() && sigOperando < operandos.size(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode t)) continue;
            int tipo = t.getSymbol().getType();
            String op;
            if      (tipo == GramaticaPigLatin.MAS)   op = "+";
            else if (tipo == GramaticaPigLatin.MENOS) op = "-";
            else continue;
            ExpresionPigLatin der = (ExpresionPigLatin) visit(operandos.get(sigOperando++));
            resultado = new Binaria(op, resultado, der,
                    t.getSymbol().getLine(), t.getSymbol().getCharPositionInLine());
        }
        return resultado;
    }

    // --- Nivel 8: *, /, % (TRES operadores mezclados -> recorrer hijos) ---
    @Override
    public NodoAST visitExpresionMultiplicativaDef(GramaticaPigLatin.ExpresionMultiplicativaDefContext ctx) {
        List<GramaticaPigLatin.ExpresionUnariaContext> operandos = ctx.expresionUnaria();
        ExpresionPigLatin resultado = (ExpresionPigLatin) visit(operandos.get(0));
        int sigOperando = 1;
        for (int i = 0; i < ctx.getChildCount() && sigOperando < operandos.size(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode t)) continue;
            int tipo = t.getSymbol().getType();
            String op;
            if      (tipo == GramaticaPigLatin.MULT)   op = "*";
            else if (tipo == GramaticaPigLatin.DIV)    op = "/";
            else if (tipo == GramaticaPigLatin.MODULO) op = "%";
            else continue;
            ExpresionPigLatin der = (ExpresionPigLatin) visit(operandos.get(sigOperando++));
            resultado = new Binaria(op, resultado, der,
                    t.getSymbol().getLine(), t.getSymbol().getCharPositionInLine());
        }
        return resultado;
    }

    // --- Nivel 9: unarias PREFIJAS (!, -, ++, --) ---
    @Override
    public NodoAST visitExpUnariaNegacion(GramaticaPigLatin.ExpUnariaNegacionContext ctx) {
        return new Unaria("!", (ExpresionPigLatin) visit(ctx.expresionUnaria()),
                true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpUnariaMenos(GramaticaPigLatin.ExpUnariaMenosContext ctx) {
        return new Unaria("-", (ExpresionPigLatin) visit(ctx.expresionUnaria()),
                true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpUnariaIncPrefijo(GramaticaPigLatin.ExpUnariaIncPrefijoContext ctx) {
        return new Unaria("++", (ExpresionPigLatin) visit(ctx.expresionUnaria()),
                true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpUnariaDecPrefijo(GramaticaPigLatin.ExpUnariaDecPrefijoContext ctx) {
        return new Unaria("--", (ExpresionPigLatin) visit(ctx.expresionUnaria()),
                true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitExpUnariaBase(GramaticaPigLatin.ExpUnariaBaseContext ctx) {
        // No es unaria: la base pasa el testigo a expresionPostfija.
        return visit(ctx.expresionPostfija());
    }

    // --- Nivel 10: postfijo ++/-- (opcional, uno solo) ---
    @Override
    public NodoAST visitExpresionPostfijaDef(GramaticaPigLatin.ExpresionPostfijaDefContext ctx) {
        ExpresionPigLatin base = (ExpresionPigLatin) visit(ctx.primaria());
        if (ctx.INCREMENTO() != null) return new Unaria("++", base, false, linea(ctx), columna(ctx));
        if (ctx.DECREMENTO() != null) return new Unaria("--", base, false, linea(ctx), columna(ctx));
        return base;
    }

    // --- Nivel 11: primaria (14 alternativas) ---

    @Override
    public NodoAST visitPrimariaCampo(GramaticaPigLatin.PrimariaCampoContext ctx) {
        ExpresionPigLatin objeto = (ExpresionPigLatin) visit(ctx.primaria());
        return new AccesoCampo(objeto, ctx.nombreMiembro().getText(), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaLlamada(GramaticaPigLatin.PrimariaLlamadaContext ctx) {
        ExpresionPigLatin objetivo = (ExpresionPigLatin) visit(ctx.primaria());
        List<ExpresionPigLatin> args = construirListaArgumentos(ctx.listaArgumentos());
        return new Llamada(objetivo, args, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaIndice(GramaticaPigLatin.PrimariaIndiceContext ctx) {
        ExpresionPigLatin arreglo = (ExpresionPigLatin) visit(ctx.primaria());
        ExpresionPigLatin indice = construirExpresion(ctx.expresion());
        return new Indice(arreglo, indice, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaNuevoObjeto(GramaticaPigLatin.PrimariaNuevoObjetoContext ctx) {
        List<ExpresionPigLatin> args = construirListaArgumentos(ctx.listaArgumentos());
        return new NuevoObjeto(ctx.ID().getText(), args, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimariaParentesis(GramaticaPigLatin.PrimariaParentesisContext ctx) {
        // Los paréntesis solo agrupan: no necesitan nodo propio.
        return construirExpresion(ctx.expresion());
    }

    @Override
    public NodoAST visitPrimariaListaLiteral(GramaticaPigLatin.PrimariaListaLiteralContext ctx) {
        // Un inicializador de arreglo usado como expresión.
        return visit(ctx.inicializadorArreglo());
    }

    @Override
    public NodoAST visitPrimariaEntero(GramaticaPigLatin.PrimariaEnteroContext ctx) {
        long valor = LiteralUtil.aEntero(ctx.getText());
        return new Literal(valor, CategoriaLiteral.ENTERO, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitPrimariaFlotante(GramaticaPigLatin.PrimariaFlotanteContext ctx) {
        double valor = LiteralUtil.aFlotante(ctx.getText());
        return new Literal(valor, CategoriaLiteral.FLOTANTE, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitPrimariaCaracter(GramaticaPigLatin.PrimariaCaracterContext ctx) {
        char valor = LiteralUtil.aCaracter(ctx.getText());
        return new Literal(valor, CategoriaLiteral.CARACTER, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitPrimariaCadena(GramaticaPigLatin.PrimariaCadenaContext ctx) {
        String valor = LiteralUtil.textoSinComillasNiEscapes(ctx.getText());
        return new Literal(valor, CategoriaLiteral.CADENA, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitPrimariaVerum(GramaticaPigLatin.PrimariaVerumContext ctx) {
        return new Literal(Boolean.TRUE, CategoriaLiteral.BOOLEANO, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitPrimariaFalsus(GramaticaPigLatin.PrimariaFalsusContext ctx) {
        return new Literal(Boolean.FALSE, CategoriaLiteral.BOOLEANO, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitPrimariaNull(GramaticaPigLatin.PrimariaNullContext ctx) {
        // Específico de PigLatin/Z: "null" mapea a CategoriaLiteral.NULO con valor null.
        return new Literal(null, CategoriaLiteral.NULO, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitPrimariaIdentificador(GramaticaPigLatin.PrimariaIdentificadorContext ctx) {
        return new Identificador(ctx.getText(), linea(ctx), columna(ctx));
    }
}