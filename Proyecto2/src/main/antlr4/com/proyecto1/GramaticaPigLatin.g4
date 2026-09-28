parser grammar GramaticaPigLatin;

options {
    tokenVocab = LenguajeLexer;
}

programa
    : importaciones? seccionVariables? funcionPrincipal EOF
    ;

importaciones
    : importacion+                                                       #importacionesDef
    ;

importacion
    : IMPORT ID (PUNTO ID)*                                     #importacionDef
    ;

seccionVariables
    : VARIABILES MAYORQUE (declaracionVariable | declaracionArreglo)*
    ;

funcionPrincipal
    : MAIOR MAYORQUE sentencia* FIN_PRINCIPAL PUNTOYCOMA                  #funcionPrincipalDef
    ;

sentencia
    : bloque                                                              #stmtBloque
    | sentenciaSi                                                         #stmtSi
    | sentenciaDum                                                        #stmtDum
    | sentenciaFacere                                                     #stmtFacere
    | sentenciaPer                                                        #stmtPer
    | sentenciaInterrumpe                                                 #stmtInterrumpe
    | sentenciaPerge                                                      #stmtPerge
    | declaracionVariable                                                 #stmtDeclaracionVariable
    | declaracionArreglo                                                  #stmtDeclaracionArreglo
    | sentenciaImprimir                                                   #stmtImprimir
    | sentenciaLeer                                                       #stmtLeer
    | expresionSentencia                                                  #stmtExpresion
    | sentenciaVacia                                                      #stmtVacia
    ;

bloque
    : LLAVEIZQ sentencia* LLAVEDER                                        #bloqueDef
    ;

// si (cond) bloque (aliter (cond) bloque)* (aliter bloque)? finis ;
sentenciaSi
    : SI LPAREN expresion RPAREN bloque
      (ALITER LPAREN expresion RPAREN bloque)*
      (ALITER bloque)?
      FINIS PUNTOYCOMA                                                    #sentenciaSiDef
    ;

// dum (cond) bloque finis ;
sentenciaDum
    : DUM LPAREN expresion RPAREN bloque FINIS PUNTOYCOMA                 #sentenciaDumDef
    ;

// facere bloque dum (cond) ;  -- sin 'finis;', solo el ';' final
sentenciaFacere
    : FACERE bloque DUM LPAREN expresion RPAREN PUNTOYCOMA                #sentenciaFacereDef
    ;

// per (init ; cond? ; actualizacion?) bloque -- SIN 'finis;' (a diferencia de si/dum)
sentenciaPer
    : PER LPAREN inicializacionFor PUNTOYCOMA expresion? PUNTOYCOMA
          actualizacionFor? RPAREN bloque                                 #sentenciaPerDef
    ;

// declaración de variable SIN ';' (el ';' del for ya actúa como separador)
// o una lista de asignaciones/expresiones separadas por coma.
inicializacionFor
    : declaracionVariableSinPuntoYComa                                    #initForDeclaracion
    | listaExpresiones                                                    #initForExpresiones
    ;

actualizacionFor
    : listaExpresiones                                                    #actualizacionForDef
    ;

listaExpresiones
    : expresion (COMA expresion)*                                        #listaExpresionesDef
    ;

sentenciaInterrumpe
    : INTERRUMPE PUNTOYCOMA                                               #sentenciaInterrumpeDef
    ;

sentenciaPerge
    : PERGE PUNTOYCOMA                                                    #sentenciaPergeDef
    ;

// >> expr (>> expr)* ;
sentenciaImprimir
    : DOSMAYOR expresion (DOSMAYOR expresion)* PUNTOYCOMA                 #sentenciaImprimirDef
    ;

// (ID)? << -- SIN ';' (ni siquiera después de la variable)
sentenciaLeer
    : ID? DOSMENOR                                                        #sentenciaLeerDef
    ;

// asignación / llamada a función / llamada a método encadenada,
expresionSentencia
    : expresion PUNTOYCOMA                                                #expresionSentenciaDef
    ;

sentenciaVacia
    : PUNTOYCOMA                                                          #sentenciaVaciaDef
    ;

//  DECLARACIONES DE VARIABLES Y ARREGLOS
declaracionVariable
    : declaracionVariableSinPuntoYComa PUNTOYCOMA                         #declaracionVariableDef
    ;

// esto ID : tipo (= expresion)?   -- reutilizada por el 'per' (sin ';')
declaracionVariableSinPuntoYComa
    : ESTO ID DOSPUNTOS tipo expresion?                                   #declaracionVarConTipo
    | ESTO ID DOSPUNTOS ID inicializadorArreglo                           #declaracionVarEstructura
    | ESTO ID DOSPUNTOS expresion                                         #declaracionVarSoloValor
    ;

// series ID [ tamaño ] : tipo (= { expr, ... })?
declaracionArreglo
    : SERIES ID CORIZQ ENTERO_LIT CORDER DOSPUNTOS tipo
      inicializadorArreglo? PUNTOYCOMA                                    #declaracionArregloDef
    ;

inicializadorArreglo
    : LLAVEIZQ expresion (COMA expresion)* LLAVEDER                       #inicializadorArregloDef
    ;

tipo
    : NUMERUS                                                             #tipoNumerus
    | DECIMALIS                                                           #tipoDecimalis
    | TEXTUM                                                              #tipoTextum
    | LITTERA                                                             #tipoLittera
    | FALSUS                                                              #tipoFalsus
    | ID                                                                  #tipoImportado
    ;

expresion
    : expresionAsignacion
    ;

// Asociativa a la derecha (a = b = 5)
expresionAsignacion
    : expresionCondicional (operadorAsignacion expresionAsignacion)?      #expresionAsignacionDef
    ;

operadorAsignacion
    : ASIGNAR | MAS_ASIGNA | MENOS_ASIGNA | MULT_ASIGNA | DIV_ASIGNA | MOD_ASIGNA   #operadorAsignacionDef
    ;

// Ternario -- el documento lo marca como opcional/no confirmado;
// se incluye igual (fácil de quitar si no se pide).
expresionCondicional
    : expresionOr (INTERROGACION expresion DOSPUNTOS expresionCondicional)?   #expresionCondicionalDef
    ;

expresionOr
    : expresionAnd (O_LOGICO expresionAnd)*                               #expresionOrDef
    ;

expresionAnd
    : expresionIgualdad (Y_LOGICO expresionIgualdad)*                     #expresionAndDef
    ;

expresionIgualdad
    : expresionRelacional ((IGUALIGUAL | DISTINTO) expresionRelacional)*  #expresionIgualdadDef
    ;

expresionRelacional
    : expresionAditiva ((MENORQUE | MAYORQUE | MENORIGUAL | MAYORIGUAL) expresionAditiva)*   #expresionRelacionalDef
    ;

expresionAditiva
    : expresionMultiplicativa ((MAS | MENOS) expresionMultiplicativa)*    #expresionAditivaDef
    ;

expresionMultiplicativa
    : expresionUnaria ((MULT | DIV | MODULO) expresionUnaria)*               #expresionMultiplicativaDef
    ;

// Ver desviación 3: se agregó '++'/'--' prefijo (recursivo), el
// documento solo daba '!'/'-' de forma no repetible.
expresionUnaria
    : NEGACION expresionUnaria                                            #expUnariaNegacion
    | MENOS expresionUnaria                                               #expUnariaMenos
    | INCREMENTO expresionUnaria                                          #expUnariaIncPrefijo
    | DECREMENTO expresionUnaria                                          #expUnariaDecPrefijo
    | expresionPostfija                                                   #expUnariaBase
    ;

expresionPostfija
    : primaria (INCREMENTO | DECREMENTO)?                                 #expresionPostfijaDef
    ;

// Recursiva a la izquierda para encadenar: obj.campo, obj[i],
// obj.metodo(args), obj1.obj2.metodo() -- ver desviación 2.
primaria
    : primaria PUNTO ID                                                   #primariaCampo
    | primaria LPAREN listaArgumentos? RPAREN                             #primariaLlamada
    | primaria CORIZQ expresion CORDER                                    #primariaIndice
    | NOVUS ID LPAREN listaArgumentos? RPAREN                             #primariaNuevoObjeto
    | LPAREN expresion RPAREN                                             #primariaParentesis
    | inicializadorArreglo                                                #primariaListaLiteral
    | ENTERO_LIT                                                          #primariaEntero
    | FLOTANTE_LIT                                                        #primariaFlotante
    | CARACTER_LIT                                                        #primariaCaracter
    | CADENA_LIT                                                          #primariaCadena
    | VERUM                                                               #primariaVerum
    | FALSUS                                                              #primariaFalsus
    | NULL                                                                #primariaNull
    | ID                                                                  #primariaIdentificador
    ;

listaArgumentos
    : expresion (COMA expresion)*                                        #listaArgumentosDef
    ;