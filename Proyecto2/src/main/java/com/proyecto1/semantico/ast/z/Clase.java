package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.tabla.ModificadorAcceso;
import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;

import java.util.ArrayList;
import java.util.List;

/**
 * claseDef (#claseDef): "clase Nombre { miembros* }".
 *
 * <p>Desde el punto de vista del C3D, una clase no genera código propio: es un
 * contenedor de constructores y métodos. Aquí se recorre cada uno y se le pasa lo
 * que necesita (atributos al constructor, nombre de la clase al método) — mismo
 * rol que juega Programa en Y con las funciones.
 */
public final class Clase extends NodoZ /* o la base que ya uses */ {

    private final String nombre;
    private final List<Atributo> atributos;
    private final List<Constructor> constructores;
    private final List<Metodo> metodos;

    /**
     * Fase 2: herencia y encapsulamiento.
     * clasePadre es el ID que sigue a "extends"; null si la clase no hereda.
     * modificador es el de la propia clase (public class / class -> DEFAULT).
     */
    private final String clasePadre;
    private final ModificadorAcceso modificador;

    // Constructor de la Fase 1: clase public sin herencia.
    public Clase(String nombre, List<Atributo> atributos,
                 List<Constructor> constructores, List<Metodo> metodos,
                 int linea, int columna) {
        this(nombre, ModificadorAcceso.PUBLIC, null, atributos, constructores, metodos,
                linea, columna);
    }

    public Clase(String nombre, ModificadorAcceso modificador, String clasePadre,
                 List<Atributo> atributos, List<Constructor> constructores,
                 List<Metodo> metodos, int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
        this.modificador = (modificador != null) ? modificador : ModificadorAcceso.DEFAULT;
        this.clasePadre = clasePadre;
        this.atributos = atributos;
        this.metodos = metodos;

        /**
         * Constructor por defecto implicito (como en Java): si la clase no declara
         * ninguno se agrega uno public sin parametros y con cuerpo vacio. Hace falta
         * por la herencia: toda clase hija llama al constructor de su padre (explicito
         * con super(...) o implicito sin argumentos), y ese constructor es el que
         * ejecuta los inicializadores de los atributos del padre.
         */
        List<Constructor> lista = new ArrayList<>(constructores);
        if (lista.isEmpty()) {
            lista.add(Constructor.implicito(nombre, linea, columna));
        }
        this.constructores = lista;
    }

    public String getNombre() { return nombre; }
    public ModificadorAcceso getModificador() { return modificador; }
    public String getClasePadre() { return clasePadre; }
    public boolean tienePadre() { return clasePadre != null; }
    public List<Atributo> getAtributos() { return atributos; }
    public List<Constructor> getConstructores() { return constructores; }
    public List<Metodo> getMetodos() { return metodos; }

    /**
     * Emite, en este orden: todos los constructores, luego todos los métodos. Cada
     * constructor recibe la lista de atributos de la clase para inyectar los field
     * initializers; cada método recibe el nombre de la clase para su mangling.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        generador.entrarClase(nombre);
        for (Constructor c : constructores) {
            c.generarC3D(generador, atributos);
        }
        for (Metodo m : metodos) {
            m.generarC3D(generador, nombre);
        }
        generador.salirClase();
        return ResultadoC3D.vacio();
    }
}