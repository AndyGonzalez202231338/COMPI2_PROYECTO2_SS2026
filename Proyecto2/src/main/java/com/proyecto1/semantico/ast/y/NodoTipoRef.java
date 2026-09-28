package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Representa la regla tipo de la gramática tipoEntero, tipoFlotante,
 * tipoCaracter, tipoCadena, tipoBool, tipoEstructura). Es una referencia SINTÁCTICA
 * nada más: guarda el nombre tal cual aparece en el código ("entero", "flotante",
 * "Persona", ...) y si es primitivo o no; todavía NO se resuelve contra la tabla de
 * símbolos (comprobar que "Persona" en verdad exista como estructura ya
 * definida antes es parte de las validaciones semánticas ).
 *
 */
public final class NodoTipoRef extends NodoY {

    private final String nombre;      // "entero" | "flotante" | "caracter" | "cadena" | "bool" | <ID de estructura>
    private final boolean esPrimitivo;

    public NodoTipoRef(String nombre, boolean esPrimitivo, int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
        this.esPrimitivo = esPrimitivo;
    }

    public String getNombre() {
        return nombre;
    }
    public boolean isEsPrimitivo() {
        return esPrimitivo;
    }

    public Tipo resolver(Ambito ambito, ManejadorErrores errores) {
        if (esPrimitivo) {
            return switch (nombre) {
                case "entero"   -> TipoPrimitivo.ENTERO;
                case "flotante" -> TipoPrimitivo.FLOTANTE;
                case "caracter" -> TipoPrimitivo.CARACTER;
                case "cadena"   -> TipoPrimitivo.CADENA;
                case "bool"     -> TipoPrimitivo.BOOL;
                default -> TipoPrimitivo.DESCONOCIDO;
            };
        }
        // Es un nombre de estructura: buscar en el ámbito global
        Simbolo s = ambito.ambitoGlobal().resolverLocal(nombre);
        if (s == null || s.getCategoria() != CategoriaSimbolo.ESTRUCTURA) {
            errores.reportar(linea, columna, "Tipo desconocido: '" + nombre + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        return new TipoEstructura(s);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
