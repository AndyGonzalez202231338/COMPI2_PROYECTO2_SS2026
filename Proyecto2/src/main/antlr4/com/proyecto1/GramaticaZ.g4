parser grammar GramaticaZ;

options {
    tokenVocab = LenguajeLexer;
}

compilationUnit
    : accessModifier?  CLASS nombre=ID (EXTENDS padre=ID)? LLAVEIZQ classBody* LLAVEDER EOF                   #compilationUnitDef
    ;

accessModifier
    : PUBLIC                                                             #accessPublic
    | PRIVATE                                                            #accessPrivate
    | PROTECTED                                                          #accessProtected
    ;

overrideAnnotation
    : ARROBA ID                                                          #overrideAnnotationDef
    ;

/** Solo campos, constructores y métodos (nada de código suelto);
 "no se permiten variables globales fuera de la clase" queda
 garantizado estructuralmente: no hay otra forma de meter una
 sentencia fuera de compilationUnit/classBody.
**/
classBody
    : accessModifier? fieldDeclaration                                  #classBodyField
    | accessModifier? constructorDeclaration                            #classBodyConstructor
    // @Override solo aplica a metodos, nunca a campos ni constructores.
    | overrideAnnotation? accessModifier? methodDeclaration             #classBodyMethod
    ;

fieldDeclaration
    : declaracion PUNTOYCOMA                                             #fieldDeclarationDef
    ;

constructorDeclaration
    : ID LPAREN formalParameters? RPAREN block                    #constructorDeclarationDef
    ;

methodDeclaration
    : (tipo | VOID) nombreMiembro LPAREN formalParameters? RPAREN block  #methodDeclarationDef
    ;

/** Nombre de miembro: ademas de ID acepta palabras reservadas que son exclusivas
de Y o PigLatin. El lexer es compartido por los tres lenguajes, asi que sin
esto un metodo de Z no podria llamarse "imprimir". **/
nombreMiembro
    : ID
    | IMPRIMIR | LEER | SINO | CONTRARIO | ELEGIR | CASO | SIEMPRE
    | PARA | MIENTRAS | HACER | CONTINUAR | ROMPER
    | ESTO | SERIES | NUMERUS | DECIMALIS | TEXTUM | LITTERA
    | FALSUS | VERUM | NOVUS | ALITER | FINIS | DUM | FACERE
    | PER | PERGE | INTERRUMPE
    ;

formalParameters
    : formalParameter (COMA formalParameter)*                           #formalParametersDef
    ;

formalParameter
    : tipo ID                                                            #formalParameterDef
    ;

/**
  TIPOS
  "tipo" = tipoBase + cero o más pares "[]" (arreglo, incluye multidimensional: "int[][]").
**/
tipo
    : tipoBase (CORIZQ CORDER)*                                          #tipoDef
    ;

tipoBase
    : INT                                                                 #tipoInt
    | DOUBLE                                                              #tipoDouble
    | CHAR                                                                #tipoChar
    | BOOLEAN                                                             #tipoBoolean
    | STRING                                                              #tipoString
    | ID                                                                  #tipoClase
    ;

//  BLOQUES Y SENTENCIAS
block
    : LLAVEIZQ statement* LLAVEDER                                       #blockDef
    ;

statement
    : block                                                              #stmtBlock
    | ifStatement                                                        #stmtIf
    | switchStatement                                                    #stmtSwitch
    | forStatement                                                       #stmtFor
    | whileStatement                                                     #stmtWhile
    | doWhileStatement                                                   #stmtDoWhile
    | returnStatement                                                    #stmtReturn
    | breakStatement                                                     #stmtBreak
    | continueStatement                                                  #stmtContinue
    | declarationStatement                                               #stmtDeclaracion
    | expressionStatement                                                #stmtExpresion
    | PUNTOYCOMA                                                         #stmtVacia
    ;

ifStatement
    : IF LPAREN expression RPAREN statement (ELSE statement)?            #ifStatementDef
    ;

switchStatement
    : SWITCH LPAREN expression RPAREN LLAVEIZQ switchCase* defaultCase? LLAVEDER   #switchStatementDef
    ;

// "break" es opcional (fall-through si se omite) -- por eso el '?'
switchCase
    : CASE expression DOSPUNTOS statement* breakStatement?               #switchCaseDef
    ;

defaultCase
    : DEFAULT DOSPUNTOS statement* breakStatement?                       #defaultCaseDef
    ;

forStatement
    : FOR LPAREN forInit? PUNTOYCOMA expression? PUNTOYCOMA forUpdate? RPAREN statement   #forStatementDef
    ;

forInit
    : declaracion                                                        #forInitDeclaracion
    | expressionList                                                     #forInitExpresiones
    ;

forUpdate
    : expressionList                                                     #forUpdateDef
    ;

expressionList
    : expression (COMA expression)*                                      #expressionListDef
    ;

whileStatement
    : WHILE LPAREN expression RPAREN statement                           #whileStatementDef
    ;

doWhileStatement
    : DO statement WHILE LPAREN expression RPAREN PUNTOYCOMA             #doWhileStatementDef
    ;

returnStatement
    : RETURN expression? PUNTOYCOMA                                      #returnStatementDef
    ;

breakStatement
    : BREAK PUNTOYCOMA                                                   #breakStatementDef
    ;

continueStatement
    : CONTINUE PUNTOYCOMA                                                #continueStatementDef
    ;

expressionStatement
    : expression PUNTOYCOMA                                              #expressionStatementDef
    ;

declarationStatement
    : declaracion PUNTOYCOMA                                             #declarationStatementDef
    ;

declaracion
    : tipo ID (ASIGNAR expression)?                                      #declaracionDef
    ;

expression
    : assignmentExpression
    ;

// Asociativa a la derecha (a = b = 5), por eso se recursiona sobre sí misma en la parte derecha en vez de repetirse con '*'.
assignmentExpression
    : conditionalExpression (assignmentOperator assignmentExpression)?    #assignmentExpressionDef
    ;

assignmentOperator
    : ASIGNAR | MAS_ASIGNA | MENOS_ASIGNA | MULT_ASIGNA | DIV_ASIGNA | MOD_ASIGNA   #assignmentOperatorDef
    ;

// Ternario, también asociativo a la derecha
conditionalExpression
    : logicalOrExpression (INTERROGACION expression DOSPUNTOS conditionalExpression)?   #conditionalExpressionDef
    ;

logicalOrExpression
    : logicalAndExpression (O_LOGICO logicalAndExpression)*              #logicalOrExpressionDef
    ;

logicalAndExpression
    : equalityExpression (Y_LOGICO equalityExpression)*                  #logicalAndExpressionDef
    ;

equalityExpression
    : relationalExpression ((IGUALIGUAL | DISTINTO) relationalExpression)*   #equalityExpressionDef
    ;

relationalExpression
    : additiveExpression ((MENORQUE | MAYORQUE | MENORIGUAL | MAYORIGUAL) additiveExpression)*   #relationalExpressionDef
    ;

additiveExpression
    : multiplicativeExpression ((MAS | MENOS) multiplicativeExpression)*   #additiveExpressionDef
    ;

multiplicativeExpression
    : unaryExpression ((MULT | DIV | MODULO) unaryExpression)*              #multiplicativeExpressionDef
    ;

unaryExpression
    : NEGACION unaryExpression                                           #unaryNegacionDef
    | MENOS unaryExpression                                              #unaryMenosDef
    | INCREMENTO unaryExpression                                         #unaryIncrementoPrefijoDef
    | DECREMENTO unaryExpression                                         #unaryDecrementoPrefijoDef
    | postfixExpression                                                  #unaryBaseDef
    ;

postfixExpression
    : primaryExpression (INCREMENTO | DECREMENTO)?                       #postfixExpressionDef
    ;

// Recursiva a la izquierda para poder encadenar: obj1.obj2.metodo(),
// arreglo[i][j], f().g(), etc.
primaryExpression
    : primaryExpression PUNTO nombreMiembro                              #primarioCampo
    | primaryExpression LPAREN argumentList? RPAREN                      #primarioLlamada
    | primaryExpression CORIZQ expression CORDER                         #primarioIndice
    | NEW ID LPAREN argumentList? RPAREN                                 #primarioInstanciaClase
    | NEW tipoBase (CORIZQ expression CORDER)+                           #primarioArregloConTamano
    | NEW tipoBase (CORIZQ CORDER)+ LLAVEIZQ initializerList? LLAVEDER   #primarioArregloConInicializador
    | LLAVEIZQ initializerList? LLAVEDER                                 #primarioListaLiteral
    | PRINTLN LPAREN expression RPAREN                                   #primarioPrintln
    | PRINT LPAREN expression RPAREN                                     #primarioPrint
    | READLN LPAREN RPAREN                                               #primarioReadln
    | LPAREN expression RPAREN                                           #primarioParentesis
    | ENTERO_LIT                                                         #primarioEntero
    | FLOTANTE_LIT                                                       #primarioFlotante
    | CARACTER_LIT                                                       #primarioCaracter
    | CADENA_LIT                                                         #primarioCadena
    | TRUE                                                               #primarioTrue
    | FALSE                                                              #primarioFalse
    | NULL                                                               #primarioNull
    | THIS                                                               #primarioThis
    | SUPER                                                              #primarioSuper
    | ID                                                                 #primarioIdentificador
    ;

argumentList
    : expression (COMA expression)*                                      #argumentListDef
    ;

initializerList
    : expression (COMA expression)*                                      #initializerListDef
    ;