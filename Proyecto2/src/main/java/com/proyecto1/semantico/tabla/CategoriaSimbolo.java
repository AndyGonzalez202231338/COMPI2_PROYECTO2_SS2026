package com.proyecto1.semantico.tabla;

/**
 * Categoría de un Simbolo dentro de la tabla de símbolos. Sirve tanto para dar
 * mensajes de error más claros ("ya existe una función llamada X", no solo "ya existe X")
 * como para validaciones que dependen de qué tipo de símbolo es (por ejemplo, solo se
 * puede "llamar" a FUNCION/METODO/CONSTRUCTOR, solo se puede acceder con '.' a
 * CAMPO/ATRIBUTO/METODO, etc.).
 */
public enum CategoriaSimbolo {
    VARIABLE,       // variable local (Y?: declaracionVariable; Z: declaracionStatement)
    PARAMETRO,      // parámetro de función/método/constructor
    FUNCION,        // función de Y? (definida en %funciones)
    ESTRUCTURA,     // estructura de Y? (definida en %estructuras)
    CAMPO,          // campo dentro de una estructura de Y?
    CLASE,          // clase de Zetariano
    ATRIBUTO,       // atributo (campo) de una clase de Zetariano
    METODO,         // método de una clase de Zetariano
    CONSTRUCTOR     // constructor de una clase de Zetariano
}