package com.proyecto1.semantico.z;

import com.proyecto1.GramaticaZ;
import com.proyecto1.GramaticaZBaseVisitor;
import com.proyecto1.semantico.LiteralUtil;
import com.proyecto1.semantico.ast.NodoAST;
import com.proyecto1.semantico.ast.z.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class ASTBuilderZ extends GramaticaZBaseVisitor<NodoAST> {

    /** Punto de entrada: {@code new ASTBuilderZ().construir(parser.compilationUnit())}. */
    public Clase construir(GramaticaZ.CompilationUnitContext arbol) {
        return (Clase) visit(arbol);
    }

    // POSICIÓN: todo nodo del AST propio necesita línea/columna de origen.
    private int linea(ParserRuleContext ctx) { return ctx.getStart().getLine(); }
    private int columna(ParserRuleContext ctx) { return ctx.getStart().getCharPositionInLine(); }

    // COMPILATION UNIT / CLASS BODY

    /**
     * compilationUnit
     *     : PUBLIC CLASS ID LLAVEIZQ classBody* LLAVEDER EOF   #compilationUnitDef
     *     ;
     *
     * classBody tiene 3 alternativas ya etiquetadas (field/constructor/method), así
     * que se despacha por {@code instanceof} en vez de por {@code visit(ctx)}: aquí
     * hay que acumular en TRES listas distintas (atributos/constructores/métodos),
     * no en un único resultado.
     */
    @Override
    public NodoAST visitCompilationUnitDef(GramaticaZ.CompilationUnitDefContext ctx) {
        List<Atributo>    atributos     = new ArrayList<>();
        List<Constructor> constructores = new ArrayList<>();
        List<Metodo>      metodos       = new ArrayList<>();

        for (GramaticaZ.ClassBodyContext body : ctx.classBody()) {
            if (body instanceof GramaticaZ.ClassBodyFieldContext cf) {
                atributos.add(construirAtributo(
                        (GramaticaZ.FieldDeclarationDefContext) cf.fieldDeclaration()));
            } else if (body instanceof GramaticaZ.ClassBodyConstructorContext cc) {
                constructores.add(construirConstructor(
                        (GramaticaZ.ConstructorDeclarationDefContext) cc.constructorDeclaration()));
            } else if (body instanceof GramaticaZ.ClassBodyMethodContext cm) {
                metodos.add(construirMetodo(
                        (GramaticaZ.MethodDeclarationDefContext) cm.methodDeclaration()));
            }
        }

        return new Clase(ctx.ID().getText(), atributos, constructores, metodos,
                linea(ctx), columna(ctx));
    }

    /**
     * fieldDeclaration : declaracion PUNTOYCOMA   #fieldDeclarationDef ;
     * Comparte la regla "declaracion" con declarationStatement, pero aquí el nodo
     * que se construye es un Atributo (miembro de clase), no una instrucción.
     */
    private Atributo construirAtributo(GramaticaZ.FieldDeclarationDefContext ctx) {
        GramaticaZ.DeclaracionDefContext decl =
                (GramaticaZ.DeclaracionDefContext) ctx.declaracion(); // única alternativa
        NodoTipoRef tipo = construirTipoRef((GramaticaZ.TipoDefContext) decl.tipo());
        ExpresionZ inicializador = (decl.expression() == null)
                ? null
                : construirExpresion(decl.expression());
        return new Atributo(tipo, decl.ID().getText(), inicializador, linea(ctx), columna(ctx));
    }

    /**
     * constructorDeclaration
     *     : PUBLIC ID LPAREN formalParameters? RPAREN block   #constructorDeclarationDef
     *     ;
     */
    private Constructor construirConstructor(GramaticaZ.ConstructorDeclarationDefContext ctx) {
        List<Parametro> parametros = construirParametros(ctx.formalParameters());
        Bloque cuerpo = construirBloque((GramaticaZ.BlockDefContext) ctx.block()); // única alt.
        return new Constructor(ctx.ID().getText(), parametros, cuerpo, linea(ctx), columna(ctx));
    }

    /**
     * methodDeclaration
     *     : PUBLIC (tipo | VOID) ID LPAREN formalParameters? RPAREN block   #methodDeclarationDef
     *     ;
     * Si la alternativa elegida fue VOID, {@code ctx.tipo() == null}: se guarda
     * {@code tipoRetorno = null} (mismo criterio que Metodo.esVoid()).
     */
    private Metodo construirMetodo(GramaticaZ.MethodDeclarationDefContext ctx) {
        NodoTipoRef tipoRetorno = (ctx.tipo() == null)
                ? null
                : construirTipoRef((GramaticaZ.TipoDefContext) ctx.tipo());
        List<Parametro> parametros = construirParametros(ctx.formalParameters());
        Bloque cuerpo = construirBloque((GramaticaZ.BlockDefContext) ctx.block());
        return new Metodo(ctx.ID().getText(), parametros, tipoRetorno, cuerpo, linea(ctx), columna(ctx));
    }

    /**
     * formalParameters : formalParameter (COMA formalParameter)*  #formalParametersDef ;
     * Devuelve lista vacía si el contexto es null (no había paréntesis con params).
     */
    private List<Parametro> construirParametros(GramaticaZ.FormalParametersContext ctx) {
        List<Parametro> resultado = new ArrayList<>();
        if (ctx == null) return resultado;
        GramaticaZ.FormalParametersDefContext def =
                (GramaticaZ.FormalParametersDefContext) ctx; // única alternativa
        for (GramaticaZ.FormalParameterContext p : def.formalParameter()) {
            GramaticaZ.FormalParameterDefContext pdef = (GramaticaZ.FormalParameterDefContext) p;
            NodoTipoRef tipo = construirTipoRef((GramaticaZ.TipoDefContext) pdef.tipo());
            resultado.add(new Parametro(tipo, pdef.ID().getText(), linea(pdef), columna(pdef)));
        }
        return resultado;
    }

    // TIPOS
    /**
     * tipo : tipoBase (CORIZQ CORDER)*  #tipoDef ;
     * dimensiones = cuántos pares "[]" hubo (0=escalar, 1="tipo[]", 2="tipo[][]", ...).
     * El nombreBase sale de tipoBase (helper aparte, ver nota de clase: NodoTipoRef
     * de Z no es NodoAST y por eso no se puede usar visit()).
     */
    private NodoTipoRef construirTipoRef(GramaticaZ.TipoDefContext ctx) {
        String base = nombreTipoBase(ctx.tipoBase());
        boolean primitivo = esTipoBasePrimitivo(ctx.tipoBase());
        int dimensiones = ctx.CORIZQ().size();
        return new NodoTipoRef(base, primitivo, dimensiones, linea(ctx), columna(ctx));
    }

    /** tipoBase tiene 6 alternativas etiquetadas; devuelve el nombre textual del tipo. */
    private String nombreTipoBase(GramaticaZ.TipoBaseContext ctx) {
        if (ctx instanceof GramaticaZ.TipoIntContext)     return "int";
        if (ctx instanceof GramaticaZ.TipoDoubleContext)  return "double";
        if (ctx instanceof GramaticaZ.TipoCharContext)    return "char";
        if (ctx instanceof GramaticaZ.TipoBooleanContext) return "boolean";
        if (ctx instanceof GramaticaZ.TipoStringContext)  return "String";
        if (ctx instanceof GramaticaZ.TipoClaseContext c) return c.ID().getText();
        throw new IllegalStateException("tipoBase no reconocido: " + ctx.getClass().getSimpleName());
    }

    /** Primitivo = todo menos un ID de clase. */
    private boolean esTipoBasePrimitivo(GramaticaZ.TipoBaseContext ctx) {
        return !(ctx instanceof GramaticaZ.TipoClaseContext);
    }

    // BLOQUES

    /**
     * block : LLAVEIZQ statement* LLAVEDER  #blockDef ;
     * A diferencia de Y, en Z las llaves son obligatorias para un "block", así que
     * SIEMPRE se construye un {@link Bloque}. En cambio, muchos cuerpos de
     * sentencias (if/while/for) aceptan cualquier {@code statement}, que puede o no
     * ser un bloque.
     */
    private Bloque construirBloque(GramaticaZ.BlockDefContext ctx) {
        List<InstruccionZ> instrucciones = new ArrayList<>();
        for (GramaticaZ.StatementContext s : ctx.statement()) {
            instrucciones.add((InstruccionZ) visit(s));
        }
        return new Bloque(instrucciones, linea(ctx), columna(ctx));
    }


    // STATEMENT (12 alternativas -> 12 overrides)
    @Override public NodoAST visitStmtBlock(GramaticaZ.StmtBlockContext ctx) {
        // Antes: visit(ctx.block()) -- no hay visitBlockDef, asi que devolvia null y el cuerpo de
        // todo for/while/if escrito con llaves quedaba null (NullPointerException al verificar).
        return construirBloque((GramaticaZ.BlockDefContext) ctx.block());
    }
    @Override public NodoAST visitStmtIf(GramaticaZ.StmtIfContext ctx) {
        return visit(ctx.ifStatement());
    }
    @Override public NodoAST visitStmtSwitch(GramaticaZ.StmtSwitchContext ctx) {
        return visit(ctx.switchStatement());
    }
    @Override public NodoAST visitStmtFor(GramaticaZ.StmtForContext ctx) {
        return visit(ctx.forStatement());
    }
    @Override public NodoAST visitStmtWhile(GramaticaZ.StmtWhileContext ctx) {
        return visit(ctx.whileStatement());
    }
    @Override public NodoAST visitStmtDoWhile(GramaticaZ.StmtDoWhileContext ctx) {
        return visit(ctx.doWhileStatement());
    }
    @Override public NodoAST visitStmtReturn(GramaticaZ.StmtReturnContext ctx) {
        return visit(ctx.returnStatement());
    }
    @Override public NodoAST visitStmtBreak(GramaticaZ.StmtBreakContext ctx) {
        return visit(ctx.breakStatement());
    }
    @Override public NodoAST visitStmtContinue(GramaticaZ.StmtContinueContext ctx) {
        return visit(ctx.continueStatement());
    }
    @Override public NodoAST visitStmtDeclaracion(GramaticaZ.StmtDeclaracionContext ctx) {
        return visit(ctx.declarationStatement());
    }
    @Override public NodoAST visitStmtExpresion(GramaticaZ.StmtExpresionContext ctx) {
        return visit(ctx.expressionStatement());
    }
    @Override public NodoAST visitStmtVacia(GramaticaZ.StmtVaciaContext ctx) {
        // ";" suelto: en Z sí existe, en Y no.
        return new SentenciaVacia(linea(ctx), columna(ctx));
    }

    // SENTENCIAS ESPECÍFICAS
    /**
     * ifStatement : IF LPAREN expression RPAREN statement (ELSE statement)?  #ifStatementDef ;
     * "entonces"/"contrario" son InstruccionZ (no Bloque) porque en Z las llaves son
     * opcionales para una sola sentencia. Un "else if" es sintácticamente "ELSE
     * statement" donde ese statement ES otro Si: el encadenado sale gratis.
     */
    @Override
    public NodoAST visitIfStatementDef(GramaticaZ.IfStatementDefContext ctx) {
        ExpresionZ cond = construirExpresion(ctx.expression());
        List<GramaticaZ.StatementContext> stmts = ctx.statement();
        InstruccionZ entonces = (InstruccionZ) visit(stmts.get(0));
        InstruccionZ contrario = (stmts.size() > 1) ? (InstruccionZ) visit(stmts.get(1)) : null;
        return new Si(cond, entonces, contrario, linea(ctx), columna(ctx));
    }

    /**
     * switchStatement
     *     : SWITCH LPAREN expression RPAREN LLAVEIZQ switchCase* defaultCase? LLAVEDER
     *     ;
     */
    @Override
    public NodoAST visitSwitchStatementDef(GramaticaZ.SwitchStatementDefContext ctx) {
        ExpresionZ control = construirExpresion(ctx.expression());
        List<CasoElegir> casos = new ArrayList<>();
        for (GramaticaZ.SwitchCaseContext c : ctx.switchCase()) {
            casos.add(construirCasoElegir((GramaticaZ.SwitchCaseDefContext) c));
        }
        CasoDefecto porDefecto = (ctx.defaultCase() == null)
                ? null
                : construirCasoDefecto((GramaticaZ.DefaultCaseDefContext) ctx.defaultCase());
        return new Elegir(control, casos, porDefecto, linea(ctx), columna(ctx));
    }

    /**
     * switchCase : CASE expression DOSPUNTOS statement* breakStatement?  #switchCaseDef ;
     *
     * NOTA sobre el "break" opcional: ANTLR es greedy con {@code statement*}, y
     * {@code breakStatement} también es un {@code statement} válido (via stmtBreak).
     * En la práctica el "break;" del final suele quedar DENTRO de
     * {@code ctx.statement()}, no en {@code ctx.breakStatement()}. Para que el AST
     * refleje la intención (instrucciones del caso + bandera tieneRomper), se
     * detecta el caso y se saca el Romper del final de la lista.
     */
    private CasoElegir construirCasoElegir(GramaticaZ.SwitchCaseDefContext ctx) {
        ExpresionZ valor = construirExpresion(ctx.expression());

        List<InstruccionZ> instrucciones = new ArrayList<>();
        for (GramaticaZ.StatementContext s : ctx.statement()) {
            instrucciones.add((InstruccionZ) visit(s));
        }
        boolean tieneRomper = extraerRomperFinal(ctx.breakStatement(), instrucciones);
        return new CasoElegir(valor, instrucciones, tieneRomper);
    }

    /** Misma historia que switchCase, sin "expression" delante. */
    private CasoDefecto construirCasoDefecto(GramaticaZ.DefaultCaseDefContext ctx) {
        List<InstruccionZ> instrucciones = new ArrayList<>();
        for (GramaticaZ.StatementContext s : ctx.statement()) {
            instrucciones.add((InstruccionZ) visit(s));
        }
        boolean tieneRomper = extraerRomperFinal(ctx.breakStatement(), instrucciones);
        return new CasoDefecto(instrucciones, tieneRomper);
    }

    /**
     * Devuelve true si el caso termina con "break;", quitándolo de la lista de
     * instrucciones si quedó atrapado ahí por el greedy del parser.
     */
    private boolean extraerRomperFinal(GramaticaZ.BreakStatementContext breakOpt,
                                       List<InstruccionZ> instrucciones) {
        if (breakOpt != null) return true;
        if (!instrucciones.isEmpty()
                && instrucciones.get(instrucciones.size() - 1) instanceof Romper) {
            instrucciones.remove(instrucciones.size() - 1);
            return true;
        }
        return false;
    }

    /**
     * forStatement
     *     : FOR LPAREN forInit? PUNTOYCOMA expression? PUNTOYCOMA forUpdate? RPAREN statement
     *     ;
     *
     * El "init" puede ser UNA declaración O una lista de expresiones; el "act" es
     * siempre una lista de expresiones. Para el nodo Para, exactamente uno de
     * {@code inicializacionDeclaracion} / {@code inicializacionExpresiones} queda
     * no-nulo cuando hubo init; y {@code actualizacion} queda null si se omitió.
     */
    @Override
    public NodoAST visitForStatementDef(GramaticaZ.ForStatementDefContext ctx) {
        DeclaracionVariable initDecl = null;
        List<ExpresionZ>    initExprs = null;
        if (ctx.forInit() != null) {
            if (ctx.forInit() instanceof GramaticaZ.ForInitDeclaracionContext fd) {
                initDecl = construirDeclaracionVariable(
                        (GramaticaZ.DeclaracionDefContext) fd.declaracion());
            } else if (ctx.forInit() instanceof GramaticaZ.ForInitExpresionesContext fe) {
                initExprs = construirExpressionList(
                        (GramaticaZ.ExpressionListDefContext) fe.expressionList());
            }
        }

        ExpresionZ cond = (ctx.expression() == null) ? null : construirExpresion(ctx.expression());

        List<ExpresionZ> act = null;
        if (ctx.forUpdate() != null) {
            GramaticaZ.ForUpdateDefContext upd = (GramaticaZ.ForUpdateDefContext) ctx.forUpdate();
            act = construirExpressionList((GramaticaZ.ExpressionListDefContext) upd.expressionList());
        }

        InstruccionZ cuerpo = (InstruccionZ) visit(ctx.statement());
        return new Para(initDecl, initExprs, cond, act, cuerpo, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitWhileStatementDef(GramaticaZ.WhileStatementDefContext ctx) {
        ExpresionZ cond = construirExpresion(ctx.expression());
        InstruccionZ cuerpo = (InstruccionZ) visit(ctx.statement());
        return new Mientras(cond, cuerpo, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitDoWhileStatementDef(GramaticaZ.DoWhileStatementDefContext ctx) {
        InstruccionZ cuerpo = (InstruccionZ) visit(ctx.statement());
        ExpresionZ cond = construirExpresion(ctx.expression());
        return new HacerMientras(cuerpo, cond, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitReturnStatementDef(GramaticaZ.ReturnStatementDefContext ctx) {
        ExpresionZ valor = (ctx.expression() == null) ? null : construirExpresion(ctx.expression());
        return new Retorno(valor, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitBreakStatementDef(GramaticaZ.BreakStatementDefContext ctx) {
        return new Romper(linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitContinueStatementDef(GramaticaZ.ContinueStatementDefContext ctx) {
        return new Continuar(linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitExpressionStatementDef(GramaticaZ.ExpressionStatementDefContext ctx) {
        return new ExpresionStmt(construirExpresion(ctx.expression()), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitDeclarationStatementDef(GramaticaZ.DeclarationStatementDefContext ctx) {
        return construirDeclaracionVariable((GramaticaZ.DeclaracionDefContext) ctx.declaracion());
    }

    /**
     * declaracion : tipo ID (ASIGNAR expression)?  #declaracionDef ;
     * Se usa tanto como instrucción suelta como en el "init" de un for.
     */
    private DeclaracionVariable construirDeclaracionVariable(GramaticaZ.DeclaracionDefContext ctx) {
        NodoTipoRef tipo = construirTipoRef((GramaticaZ.TipoDefContext) ctx.tipo());
        ExpresionZ inicializador = (ctx.expression() == null)
                ? null
                : construirExpresion(ctx.expression());
        return new DeclaracionVariable(tipo, ctx.ID().getText(), inicializador,
                linea(ctx), columna(ctx));
    }

    // LISTAS AUXILIARES (expressionList / argumentList / initializerList)
    // Las tres tienen la MISMA forma: "expression (COMA expression)*". Por eso se
    // factorizan en helpers que devuelven List<ExpresionZ>.
    private List<ExpresionZ> construirExpressionList(GramaticaZ.ExpressionListDefContext ctx) {
        List<ExpresionZ> lista = new ArrayList<>();
        for (GramaticaZ.ExpressionContext e : ctx.expression()) lista.add(construirExpresion(e));
        return lista;
    }

    /** Igual que expressionList, pero el contexto puede ser null ("argumentList?"). */
    private List<ExpresionZ> construirArgumentList(GramaticaZ.ArgumentListContext ctx) {
        List<ExpresionZ> lista = new ArrayList<>();
        if (ctx == null) return lista;
        GramaticaZ.ArgumentListDefContext def = (GramaticaZ.ArgumentListDefContext) ctx;
        for (GramaticaZ.ExpressionContext e : def.expression()) lista.add(construirExpresion(e));
        return lista;
    }

    /** Igual que expressionList, pero el contexto puede ser null ("initializerList?"). */
    private List<ExpresionZ> construirInitializerList(GramaticaZ.InitializerListContext ctx) {
        List<ExpresionZ> lista = new ArrayList<>();
        if (ctx == null) return lista;
        GramaticaZ.InitializerListDefContext def = (GramaticaZ.InitializerListDefContext) ctx;
        for (GramaticaZ.ExpressionContext e : def.expression()) lista.add(construirExpresion(e));
        return lista;
    }

    // EXPRESIONES
    /**
     * {@code expression : assignmentExpression ;} — regla SIN etiqueta: se delega
     * directamente al siguiente nivel (assignmentExpression), que sí tiene label.
     */
    private ExpresionZ construirExpresion(GramaticaZ.ExpressionContext ctx) {
        return (ExpresionZ) visit(ctx.assignmentExpression());
    }

    // --- Nivel 1: asignación, asociativa a la derecha ("a = b = 5") ---
    // Si NO hay assignmentOperator, la expresión es simplemente la
    // conditionalExpression (pasa de largo). Si lo hay, se construye Asignacion.
    @Override
    public NodoAST visitAssignmentExpressionDef(GramaticaZ.AssignmentExpressionDefContext ctx) {
        ExpresionZ izquierdo = (ExpresionZ) visit(ctx.conditionalExpression());
        if (ctx.assignmentOperator() == null) return izquierdo;

        String operador = ctx.assignmentOperator().getText(); // "=", "+=", "-=", "*=", "/=", "%="
        ExpresionZ derecho = (ExpresionZ) visit(ctx.assignmentExpression());
        return new Asignacion(izquierdo, operador, derecho, linea(ctx), columna(ctx));
    }

    // --- Nivel 2: ternario, asociativo a la derecha ---
    // Si no hay '?', pasa de largo el logicalOrExpression.
    @Override
    public NodoAST visitConditionalExpressionDef(GramaticaZ.ConditionalExpressionDefContext ctx) {
        ExpresionZ cond = (ExpresionZ) visit(ctx.logicalOrExpression());
        if (ctx.INTERROGACION() == null) return cond;
        ExpresionZ siVerdadero = construirExpresion(ctx.expression());
        ExpresionZ siFalso = (ExpresionZ) visit(ctx.conditionalExpression());
        return new Ternario(cond, siVerdadero, siFalso, linea(ctx), columna(ctx));
    }

    // --- Nivel 3: || (un solo operador -> plegado directo) ---
    @Override
    public NodoAST visitLogicalOrExpressionDef(GramaticaZ.LogicalOrExpressionDefContext ctx) {
        List<GramaticaZ.LogicalAndExpressionContext> operandos = ctx.logicalAndExpression();
        ExpresionZ resultado = (ExpresionZ) visit(operandos.get(0));
        for (int i = 1; i < operandos.size(); i++) {
            ExpresionZ der = (ExpresionZ) visit(operandos.get(i));
            resultado = new Binaria("||", resultado, der, linea(ctx), columna(ctx));
        }
        return resultado;
    }

    // --- Nivel 4: && (un solo operador -> plegado directo) ---
    @Override
    public NodoAST visitLogicalAndExpressionDef(GramaticaZ.LogicalAndExpressionDefContext ctx) {
        List<GramaticaZ.EqualityExpressionContext> operandos = ctx.equalityExpression();
        ExpresionZ resultado = (ExpresionZ) visit(operandos.get(0));
        for (int i = 1; i < operandos.size(); i++) {
            ExpresionZ der = (ExpresionZ) visit(operandos.get(i));
            resultado = new Binaria("&&", resultado, der, linea(ctx), columna(ctx));
        }
        return resultado;
    }

    // --- Nivel 5: ==, != (DOS operadores mezclados -> recorrer hijos) ---
    // En este nivel la gramática es "relationalExpression ((IGUALIGUAL | DISTINTO)
    // relationalExpression)*". Los accessors ctx.IGUALIGUAL() y ctx.DISTINTO()
    // devuelven listas separadas, y por sí solas NO dicen cuál salió primero. Por
    // eso se recorre ctx.getChild(i) en su orden real: cuando el hijo es el token
    // ==/!=, se consume el siguiente operando de la lista "operandos" (ya sabemos
    // que empieza en 1) y se arma el Binaria correspondiente.
    @Override
    public NodoAST visitEqualityExpressionDef(GramaticaZ.EqualityExpressionDefContext ctx) {
        List<GramaticaZ.RelationalExpressionContext> operandos = ctx.relationalExpression();
        ExpresionZ resultado = (ExpresionZ) visit(operandos.get(0));
        int sigOperando = 1;
        for (int i = 0; i < ctx.getChildCount() && sigOperando < operandos.size(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode t)) continue;
            int tipo = t.getSymbol().getType();
            String op;
            if (tipo == GramaticaZ.IGUALIGUAL)   op = "==";
            else if (tipo == GramaticaZ.DISTINTO) op = "!=";
            else continue; // otro token cualquiera
            ExpresionZ der = (ExpresionZ) visit(operandos.get(sigOperando++));
            resultado = new Binaria(op, resultado, der,
                    t.getSymbol().getLine(), t.getSymbol().getCharPositionInLine());
        }
        return resultado;
    }

    // --- Nivel 6: <, >, <=, >= (CUATRO operadores mezclados -> recorrer hijos) ---
    @Override
    public NodoAST visitRelationalExpressionDef(GramaticaZ.RelationalExpressionDefContext ctx) {
        List<GramaticaZ.AdditiveExpressionContext> operandos = ctx.additiveExpression();
        ExpresionZ resultado = (ExpresionZ) visit(operandos.get(0));
        int sigOperando = 1;
        for (int i = 0; i < ctx.getChildCount() && sigOperando < operandos.size(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode t)) continue;
            int tipo = t.getSymbol().getType();
            String op;
            if      (tipo == GramaticaZ.MENORQUE)   op = "<";
            else if (tipo == GramaticaZ.MAYORQUE)   op = ">";
            else if (tipo == GramaticaZ.MENORIGUAL) op = "<=";
            else if (tipo == GramaticaZ.MAYORIGUAL) op = ">=";
            else continue;
            ExpresionZ der = (ExpresionZ) visit(operandos.get(sigOperando++));
            resultado = new Binaria(op, resultado, der,
                    t.getSymbol().getLine(), t.getSymbol().getCharPositionInLine());
        }
        return resultado;
    }

    // --- Nivel 7: +, - (DOS operadores mezclados -> recorrer hijos) ---
    @Override
    public NodoAST visitAdditiveExpressionDef(GramaticaZ.AdditiveExpressionDefContext ctx) {
        List<GramaticaZ.MultiplicativeExpressionContext> operandos = ctx.multiplicativeExpression();
        ExpresionZ resultado = (ExpresionZ) visit(operandos.get(0));
        int sigOperando = 1;
        for (int i = 0; i < ctx.getChildCount() && sigOperando < operandos.size(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode t)) continue;
            int tipo = t.getSymbol().getType();
            String op;
            if      (tipo == GramaticaZ.MAS)   op = "+";
            else if (tipo == GramaticaZ.MENOS) op = "-";
            else continue;
            ExpresionZ der = (ExpresionZ) visit(operandos.get(sigOperando++));
            resultado = new Binaria(op, resultado, der,
                    t.getSymbol().getLine(), t.getSymbol().getCharPositionInLine());
        }
        return resultado;
    }

    // --- Nivel 8: *, /, % (TRES operadores mezclados -> recorrer hijos) ---
    @Override
    public NodoAST visitMultiplicativeExpressionDef(GramaticaZ.MultiplicativeExpressionDefContext ctx) {
        List<GramaticaZ.UnaryExpressionContext> operandos = ctx.unaryExpression();
        ExpresionZ resultado = (ExpresionZ) visit(operandos.get(0));
        int sigOperando = 1;
        for (int i = 0; i < ctx.getChildCount() && sigOperando < operandos.size(); i++) {
            if (!(ctx.getChild(i) instanceof TerminalNode t)) continue;
            int tipo = t.getSymbol().getType();
            String op;
            if      (tipo == GramaticaZ.MULT)   op = "*";
            else if (tipo == GramaticaZ.DIV)    op = "/";
            else if (tipo == GramaticaZ.MODULO) op = "%";
            else continue;
            ExpresionZ der = (ExpresionZ) visit(operandos.get(sigOperando++));
            resultado = new Binaria(op, resultado, der,
                    t.getSymbol().getLine(), t.getSymbol().getCharPositionInLine());
        }
        return resultado;
    }

    // --- Nivel 9: unarias PREFIJAS (!, -, ++, --) ---
    // Cuatro alternativas -> cuatro overrides. Todas recursivas sobre unaryExpression.
    @Override
    public NodoAST visitUnaryNegacionDef(GramaticaZ.UnaryNegacionDefContext ctx) {
        return new Unaria("!", (ExpresionZ) visit(ctx.unaryExpression()),
                true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitUnaryMenosDef(GramaticaZ.UnaryMenosDefContext ctx) {
        return new Unaria("-", (ExpresionZ) visit(ctx.unaryExpression()),
                true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitUnaryIncrementoPrefijoDef(GramaticaZ.UnaryIncrementoPrefijoDefContext ctx) {
        return new Unaria("++", (ExpresionZ) visit(ctx.unaryExpression()),
                true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitUnaryDecrementoPrefijoDef(GramaticaZ.UnaryDecrementoPrefijoDefContext ctx) {
        return new Unaria("--", (ExpresionZ) visit(ctx.unaryExpression()),
                true, linea(ctx), columna(ctx));
    }
    @Override
    public NodoAST visitUnaryBaseDef(GramaticaZ.UnaryBaseDefContext ctx) {
        // No es unaria: es la base, pasa el testigo a postfixExpression.
        return visit(ctx.postfixExpression());
    }

    // --- Nivel 10: postfijo ++/-- (opcional, uno solo) ---
    @Override
    public NodoAST visitPostfixExpressionDef(GramaticaZ.PostfixExpressionDefContext ctx) {
        ExpresionZ base = (ExpresionZ) visit(ctx.primaryExpression());
        if (ctx.INCREMENTO() != null) return new Unaria("++", base, false, linea(ctx), columna(ctx));
        if (ctx.DECREMENTO() != null) return new Unaria("--", base, false, linea(ctx), columna(ctx));
        return base;
    }

    // --- Nivel 11: primaria (19 alternativas) ---

    @Override
    public NodoAST visitPrimarioCampo(GramaticaZ.PrimarioCampoContext ctx) {
        ExpresionZ objeto = (ExpresionZ) visit(ctx.primaryExpression());
        return new AccesoCampo(objeto, ctx.ID().getText(), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioLlamada(GramaticaZ.PrimarioLlamadaContext ctx) {
        ExpresionZ objetivo = (ExpresionZ) visit(ctx.primaryExpression());
        List<ExpresionZ> args = construirArgumentList(ctx.argumentList());
        return new Llamada(objetivo, args, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioIndice(GramaticaZ.PrimarioIndiceContext ctx) {
        ExpresionZ arreglo = (ExpresionZ) visit(ctx.primaryExpression());
        ExpresionZ indice = construirExpresion(ctx.expression());
        return new Indice(arreglo, indice, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioInstanciaClase(GramaticaZ.PrimarioInstanciaClaseContext ctx) {
        List<ExpresionZ> args = construirArgumentList(ctx.argumentList());
        return new NuevoObjeto(ctx.ID().getText(), args, linea(ctx), columna(ctx));
    }

    /**
     * NEW tipoBase (CORIZQ expression CORDER)+   -> new int[n], new int[a][b]
     * tipoElemento = tipo base SIN corchetes (dimensiones = 0); cada expresión de
     * tamaño va en la lista "tamanos" (una por cada par [ ]).
     */
    @Override
    public NodoAST visitPrimarioArregloConTamano(GramaticaZ.PrimarioArregloConTamanoContext ctx) {
        NodoTipoRef tipoElemento = new NodoTipoRef(
                nombreTipoBase(ctx.tipoBase()),
                esTipoBasePrimitivo(ctx.tipoBase()),
                0,
                linea(ctx), columna(ctx));
        List<ExpresionZ> tamanos = new ArrayList<>();
        for (GramaticaZ.ExpressionContext e : ctx.expression()) tamanos.add(construirExpresion(e));
        return new NuevoArregloConTamano(tipoElemento, tamanos, linea(ctx), columna(ctx));
    }

    /**
     * NEW tipoBase (CORIZQ CORDER)+ LLAVEIZQ initializerList? LLAVEDER
     *     -> new int[]{1,2,3}, new int[][]{{1,2},{3,4}}
     * dimensiones = cuántos pares "[]" vacíos hubo; los elementos van en la lista
     * (los anidados salen como ListaLiteral dentro de "elementos", sin regla aparte).
     */
    @Override
    public NodoAST visitPrimarioArregloConInicializador(GramaticaZ.PrimarioArregloConInicializadorContext ctx) {
        NodoTipoRef tipoElemento = new NodoTipoRef(
                nombreTipoBase(ctx.tipoBase()),
                esTipoBasePrimitivo(ctx.tipoBase()),
                0,
                linea(ctx), columna(ctx));
        int dimensiones = ctx.CORIZQ().size();
        List<ExpresionZ> elementos = construirInitializerList(ctx.initializerList());
        return new NuevoArregloConInicializador(tipoElemento, dimensiones, elementos,
                linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioListaLiteral(GramaticaZ.PrimarioListaLiteralContext ctx) {
        // "{1, 2, 3}" — el literal de lista como expresión.
        List<ExpresionZ> elementos = construirInitializerList(ctx.initializerList());
        return new ListaLiteral(elementos, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioPrintln(GramaticaZ.PrimarioPrintlnContext ctx) {
        return new Println(construirExpresion(ctx.expression()), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioPrint(GramaticaZ.PrimarioPrintContext ctx) {
        return new Print(construirExpresion(ctx.expression()), linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioReadln(GramaticaZ.PrimarioReadlnContext ctx) {
        return new Readln(linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioParentesis(GramaticaZ.PrimarioParentesisContext ctx) {
        // Los paréntesis solo agrupan: no necesitan nodo propio.
        return construirExpresion(ctx.expression());
    }

    @Override
    public NodoAST visitPrimarioEntero(GramaticaZ.PrimarioEnteroContext ctx) {
        long valor = LiteralUtil.aEntero(ctx.getText());
        return new Literal(valor, CategoriaLiteral.ENTERO, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioFlotante(GramaticaZ.PrimarioFlotanteContext ctx) {
        double valor = LiteralUtil.aFlotante(ctx.getText());
        return new Literal(valor, CategoriaLiteral.FLOTANTE, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioCaracter(GramaticaZ.PrimarioCaracterContext ctx) {
        char valor = LiteralUtil.aCaracter(ctx.getText());
        return new Literal(valor, CategoriaLiteral.CARACTER, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioCadena(GramaticaZ.PrimarioCadenaContext ctx) {
        String valor = LiteralUtil.textoSinComillasNiEscapes(ctx.getText());
        return new Literal(valor, CategoriaLiteral.CADENA, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioTrue(GramaticaZ.PrimarioTrueContext ctx) {
        return new Literal(Boolean.TRUE, CategoriaLiteral.BOOLEANO, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioFalse(GramaticaZ.PrimarioFalseContext ctx) {
        return new Literal(Boolean.FALSE, CategoriaLiteral.BOOLEANO, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioNull(GramaticaZ.PrimarioNullContext ctx) {
        // Específico de Z: el literal "null" mapea a CategoriaLiteral.NULO con valor null.
        return new Literal(null, CategoriaLiteral.NULO, linea(ctx), columna(ctx));
    }

    @Override
    public NodoAST visitPrimarioIdentificador(GramaticaZ.PrimarioIdentificadorContext ctx) {
        return new Identificador(ctx.getText(), linea(ctx), columna(ctx));
    }
}