package com.proyecto1.semantico.ast.piglatin;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
/**
 * Representa la regla tipo de la gramática ({@code #tipoNumerus, #tipoDecimalis,
 * #tipoTextum, #tipoLittera, #tipoFalsus, #tipoImportado). Es una referencia
 * SINTÁCTICA nada más: guarda el nombre tal cual aparece en el código ("numerus",
 * "decimalis", "textum", "littera", "falsus", o el {@code ID} de un tipo importado) y
 * si es primitivo o no; todavía NO se resuelve contra la tabla de símbolos (eso es
 * parte de las validaciones semánticas).
 *
 */
public final class NodoTipoRef extends NodoPigLatin {

    private final String nombre;      // "numerus" | "decimalis" | "textum" | "littera" | "falsus" | <ID importado>
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

    @Override
    public String toString() {
        return nombre;
    }

    public Tipo resolver(Ambito ambito, ManejadorErrores errores) {
        if (esPrimitivo) {
            return switch (nombre) {
                case "numerus"   -> TipoPrimitivo.ENTERO;
                case "decimalis" -> TipoPrimitivo.FLOTANTE;
                case "textum"    -> TipoPrimitivo.CADENA;
                case "littera"   -> TipoPrimitivo.CARACTER;
                case "falsus"    -> TipoPrimitivo.BOOL;
                default          -> TipoPrimitivo.DESCONOCIDO;
            };
        }
        // Tipo importado: puede ser estructura (de .y) o clase (de .z)
        Simbolo s = ambito.resolver(nombre);
        if (s == null) {
            errores.reportar(linea, columna, "Tipo importado desconocido: '" + nombre + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        return switch (s.getCategoria()) {
            case ESTRUCTURA -> new TipoEstructura(s);
            case CLASE      -> new TipoClase(s);
            default -> {
                errores.reportar(linea, columna, "'" + nombre + "' no es un tipo válido");
                yield TipoPrimitivo.DESCONOCIDO;
            }
        };
    }
}
