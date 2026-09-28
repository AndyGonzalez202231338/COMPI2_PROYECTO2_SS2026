package com.proyecto1.semantico.ast.z;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;

/**
 * Un (#formalParameterDef): "tipo ID". Más simple que el
 * Parametro de Y?: Z no distingue sintácticamente paso por valor/referencia
 * con marcadores especiales ("[]"/"{}") — el arreglo ya viene incluido en el propio
 * tipo, y cualquier ID de clase es implícitamente por referencia, así que no
 * hace falta una CategoriaParametro aparte.
 */
public final class Parametro extends NodoZ {

    private final NodoTipoRef tipo;
    private final String nombre;

    public Parametro(NodoTipoRef tipo, String nombre, int linea, int columna) {
        super(linea, columna);
        this.tipo = tipo;
        this.nombre = nombre;
    }

    public NodoTipoRef getTipo() {
        return tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public Tipo resolverTipo(Ambito ambito, ManejadorErrores errores) {
        return tipo.resolver(ambito, errores);
    }

}
