parser grammar GramaticaY;

options {
    tokenVocab = LenguajeLexer;
}

programa
    : seccionEstructuras? seccionFunciones? EOF                          #programaDef
    ;

seccionEstructuras
    : SEC_ESTRUCTURAS NEWLINE definicionEstructura+                      #seccionEstructurasDef
    ;

seccionFunciones
    : SEC_FUNCIONES NEWLINE definicionFuncion+                           #seccionFuncionesDef
    ;

/** ESTRUCTURAS **/
definicionEstructura
    : ESTRUCTURA ID DOSPUNTOS NEWLINE INDENT campoEstructura+ DEDENT      #estructuraDef
    ;

campoEstructura
    : tipo ID (CORIZQ ENTERO_LIT CORDER)* NEWLINE                        #campoDef
    ;

/** FUNCIONES **/
// flecha tipo cuando retorna algo la funcion
definicionFuncion
    : DEFINIR ID LPAREN listaParametros? RPAREN (FLECHA tipo)? bloque     #funcionDef
    ;

listaParametros
    : parametro (COMA parametro)*                                       #parametrosDef
    ;

/**
Primitivo (por valor):    tipo ID              -> "entero miEntero"
Arreglo   (por referencia): [] tipo ID         -> "[] entero miArray"
Estructura(por referencia): {} NombreTipo ID   -> "{} MiEstructura miEstructura"
**/
parametro
    : CORIZQ CORDER tipo ID                                              #parametroArreglo
    | LLAVEIZQ LLAVEDER ID ID                                            #parametroEstructura
    | tipo ID                                                            #parametroPrimitivo
    ;

/** TIPOS **/
tipo
    : ENTERO                                                             #tipoEntero
    | FLOTANTE                                                           #tipoFlotante
    | CARACTER                                                           #tipoCaracter
    | CADENA                                                             #tipoCadena
    | BOOL                                                               #tipoBool
    | ID                                                                 #tipoEstructura
    ;

/**
  BLOQUES
  Hay DOS formas de bloque, según lo que muestra el chunk 5:
   - "bloque"       : lleva ':' antes del salto de línea.
                      Se usa en %estructuras, %funciones, para,
                      elegir/caso/siempre y en el "hacer:" del
                      do-while.
   - "bloqueSimple" : SIN ':' -- se usa después de "entonces"
                      (si/sino) y después de "hacer" cuando
                      "hacer" introduce el cuerpo de un mientras
                      (mismo token HACER, dos posiciones distintas).
**/
bloque
    : DOSPUNTOS NEWLINE INDENT instruccion+ DEDENT                       #bloqueDef
    ;

bloqueSimple
    : NEWLINE INDENT instruccion+ DEDENT                                 #bloqueSimpleDef
    ;

/** INSTRUCCIONES **/
instruccion
    : declaracionVariable NEWLINE                                        #instDeclaracion
    | asignacion NEWLINE                                                 #instAsignacion
    | RETORNAR expresion? NEWLINE                                        #instRetorno
    | instruccionSi                                                      #instSi
    | instruccionElegir                                                  #instElegir
    | instruccionPara                                                    #instPara
    | instruccionMientras                                                #instMientras
    | instruccionHacerMientras                                           #instHacerMientras
    | IMPRIMIR LPAREN expresion (COMA expresion)* RPAREN NEWLINE         #instImprimir
    | CONTINUAR NEWLINE                                                  #instContinuar
    | ROMPER NEWLINE                                                     #instRomper
    | expresion NEWLINE                                                  #instExpresion
    ;

// "tipo ID", "entero miEntero" o "entero miEntero = 5"
declaracionVariable
    : tipo ID (CORIZQ ENTERO_LIT CORDER)* (ASIGNAR expresion)?           #declVarDef
    ;

operadorAsignacion
    : ASIGNAR | MAS_ASIGNA | MENOS_ASIGNA | MULT_ASIGNA | DIV_ASIGNA      #operadorAsignacionDef
    ;

asignacion
    : ID (PUNTO ID | CORIZQ expresion CORDER)* operadorAsignacion expresion #asigDef
    ;

argumentos
    : expresion (COMA expresion)*                                       #argumentosDef
    ;

// ----- Condicionales -----
// usan bloque simple debido a que NO tiene dos puntos antes de iniciar el bloque
instruccionSi
    : SI LPAREN expresion RPAREN ENTONCES bloqueSimple
      (SINO LPAREN expresion RPAREN ENTONCES bloqueSimple)*
      (CONTRARIO bloqueSimple)?                                          #condicionSiDef
    ;

// usan bloque debido a que SI tiene dos puntos antes de iniciar el bloque
instruccionElegir
    : ELEGIR LPAREN expresion RPAREN DOSPUNTOS NEWLINE INDENT
        casoElegir+
        (SIEMPRE bloque)?
      DEDENT                                                             #condicionElegirDef
    ;

casoElegir
    : CASO literal bloque                                                #casoDef
    ;

literal
    : ENTERO_LIT                                                         #litEntero
    | CARACTER_LIT                                                       #litCaracter
    | CADENA_LIT                                                         #litCadena
    ;

// ----- Ciclos -----
// para(entero i = 0; i < 10; i++):
instruccionPara
    : PARA LPAREN (declaracionVariable | asignacion)? PUNTOYCOMA
             expresion? PUNTOYCOMA
             (asignacion | expresion)? RPAREN bloque                     #cicloParaDef
    ;

// mientras(contador < 5) hacer
// usan bloque simple debido a que NO tiene dos puntos antes de iniciar el bloque
instruccionMientras
    : MIENTRAS LPAREN expresion RPAREN HACER bloqueSimple                #cicloMientrasDef
    ;

/**
hacer:
mientras(intentos < 10)

usan bloque debido a que SI tiene dos puntos antes de iniciar el bloque
**/
instruccionHacerMientras
    : HACER bloque MIENTRAS LPAREN expresion RPAREN NEWLINE              #cicloHacerMientrasDef
    ;

// ----- EXPRESIONES -----
// Punto de entrada; nivel 1 de precedencia (más bajo): ||
expresion
    : expresionOr
    ;

expresionOr
    : expresionOr O_LOGICO expresionAnd                                  #expOrDef
    | expresionAnd                                                       #expOrBase
    ;

// Nivel 2: &&
expresionAnd
    : expresionAnd Y_LOGICO expresionRelacional                          #expAndDef
    | expresionRelacional                                                #expAndBase
    ;

// Nivel 3: ==, !=, <, >, <=, >=  (no encadenable, de ahí el "?")
expresionRelacional
    : expresionAditiva ((IGUALIGUAL | DISTINTO | MENORQUE | MAYORQUE
                          | MENORIGUAL | MAYORIGUAL) expresionAditiva)?   #expRelacionalDef
    ;

// Nivel 4: +, -
expresionAditiva
    : expresionAditiva (MAS | MENOS) expresionMultiplicativa             #expAditivaDef
    | expresionMultiplicativa                                           #expAditivaBase
    ;

// Nivel 5: *, /, %
expresionMultiplicativa
    : expresionMultiplicativa (MULT | DIV | MODULO) expresionUnaria      #expMultiplicativaDef
    | expresionUnaria                                                    #expMultiplicativaBase
    ;

// Nivel 6: !, -, ++, -- como PREFIJO (right-recursivo)
expresionUnaria
    : (NEGACION | MENOS | INCREMENTO | DECREMENTO) expresionUnaria       #expUnariaPrefijaDef
    | expresionPostfija                                                  #expUnariaBase
    ;

// Nivel 6 (continuación): ++, -- como POSTFIJO, uno solo (no "a++++")
expresionPostfija
    : primaria (INCREMENTO | DECREMENTO)?                                #expPostfijaDef
    ;

// Nivel 7 (más alto): (), [], . , llamada a función, literales y "leer()"
primaria
    : primaria PUNTO ID                                                  #primariaCampo
    | primaria CORIZQ expresion CORDER                                   #primariaIndice
    | primaria LPAREN argumentos? RPAREN                                 #primariaLlamada
    | LEER LPAREN RPAREN                                                 #primariaLeer
    | LPAREN expresion RPAREN                                            #primariaParentesis
    | LLAVEIZQ (expresion (COMA expresion)*)? LLAVEDER                   #primariaListaLiteral
    | ENTERO_LIT                                                         #primariaEntero
    | FLOTANTE_LIT                                                       #primariaFlotante
    | CARACTER_LIT                                                       #primariaCaracter
    | CADENA_LIT                                                         #primariaCadena
    | VERDADERO                                                          #primariaVerdadero
    | FALSO                                                              #primariaFalso
    | ID                                                                 #primariaIdentificador
    ;