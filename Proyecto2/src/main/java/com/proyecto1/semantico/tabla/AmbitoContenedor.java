package com.proyecto1.semantico.tabla;

/**
 * Base común de AmbitoEstructura (Y?) y AmbitoClase (Zetariano): un
 * ámbito que representa "por dentro" de una estructura o clase mientras se procesan
 * sus campos/atributos/métodos. Guarda una referencia al {@link Simbolo} que representa
 * a la propia estructura/clase (ya registrado antes en el AmbitoGlobal), para poder
 * ir agregándole miembros (simbolo.agregarMiembro(...)) a medida que se visitan.
 *
 * Este ámbito casi no se usa para "resolver" nombres en el sentido normal (los campos
 * de una estructura no son visibles como variables sueltas dentro de sus propios
 * métodos sin usar "objeto.campo" o, en Zetariano, "this.atributo" implícito) — su rol
 * principal es servir de contenedor temporal mientras se declaran los miembros y, en
 * Zetariano, permitir que un método resuelva sus propios atributos por nombre simple
 * (igual que Java permite "return edad;" dentro de un método de instancia).
 */
public abstract class AmbitoContenedor extends Ambito {

    protected final Simbolo simboloContenedor;

    protected AmbitoContenedor(Ambito padre, Simbolo simboloContenedor) {
        super(padre);
        this.simboloContenedor = simboloContenedor;
    }

    public Simbolo getSimboloContenedor() { return simboloContenedor; }

    /**
     * A diferencia de declarar() normal, aquí un miembro se registra TANTO en la tabla
     * hash de miembros del símbolo contenedor (para resolver "objeto.miembro" desde
     * afuera) COMO en la tabla de símbolos de este ámbito (para poder resolverlo por
     * nombre simple desde dentro de un método/constructor, al estilo "this" implícito).
     */
    public boolean declararMiembro(Simbolo miembro) {
        if (simbolos.contiene(miembro.getNombre())) return false;
        simbolos.insertar(miembro.getNombre(), miembro);
        simboloContenedor.agregarMiembro(miembro);
        return true;
    }

    //util para diferenciar parametros en constructores y no confundirlos
    public boolean declararMiembroConClave(String clave, Simbolo miembro) {
        if (simbolos.contiene(clave)) return false;
        simbolos.insertar(clave, miembro);
        simboloContenedor.agregarMiembroConClave(clave, miembro);
        return true;
    }
}