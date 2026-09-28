package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.NodoAST;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Base de TODOS los nodos del AST de Y?. Guarda línea/columna (para poder reportar
 * errores exactamente donde ocurren) y provee una implementación de
 * verificar(Ambito, ManejadorErrores) que, POR AHORA, es un placeholder:
 * no valida nada y siempre devuelve TipoPrimitivo#DESCONOCIDO.
 *
 * El alcance de esta entrega es SOLO el
 * visitor que construye el AST (recorre el árbol que entrega ANTLR y arma estos
 * nodos); las reglas semánticas reales se agregan en la siguiente parte,
 * sobreescribiendo verificar() en cada subclase concreta que lo necesite. Quedan
 * pendientes explícitamente:
 *   Variables declaradas antes de usarse.
 *   Funciones con retorno correcto.
 *   Arreglos con índices enteros y dimensiones correctas.
 *   Estructuras anidadas y objetos.
 * Al dejar la implementación por defecto AQUÍ (en la clase base) y no repetida en
 * cada subclase, cuando llegue el momento de implementar las reglas de verdad basta
 * con sobreescribir el método en la subclase que corresponda — nada de lo que ya
 * existe en el AST necesita cambiar de forma.
 */
public abstract class NodoY implements NodoAST {

    protected final int linea;
    protected final int columna;

    protected NodoY(int linea, int columna) {
        this.linea = linea;
        this.columna = columna;
    }

    @Override
    public int getLinea() { return linea; }

    @Override
    public int getColumna() { return columna; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        return TipoPrimitivo.DESCONOCIDO; // pendiente a propósito, ver Javadoc de la clase
    }
}
