package com.proyecto1.semantico.tabla;

import com.proyecto1.semantico.tipos.Tipo;

import java.util.ArrayList;
import java.util.List;

public class Simbolo {

    private final String nombre;
    private final CategoriaSimbolo categoria;
    private Tipo tipo;                 // tipo de la variable/campo/atributo, o tipo de RETORNO si es función/método
    private final int linea;
    private final int columna;

    // Solo para FUNCION / METODO / CONSTRUCTOR: sus parámetros, en orden.
    private final List<Simbolo> parametros = new ArrayList<>();
    private final List<Simbolo> miembrosEnOrden = new ArrayList<>();

    /**
     * Solo para ESTRUCTURA / CLASE: sus miembros (campos/atributos/métodos/constructores),
     * indexados por nombre en su propia tabla hash para resolver accesos "objeto.miembro"
     * en O(1) amortizado en vez de recorrer una lista.
     */
    private final TablaHash<String, Simbolo> miembros = new TablaHash<>();

    /**
     * Reservado para la Fase 3 (generación de C3D): posición/offset donde vivirá este símbolo
     */
    private int offset = -1;

    /**
     * Cuántos elementos tiene cada dimensión, si el símbolo es un arreglo declarado con
     * tamaño fijo (Y?: "entero notas[5]"; Z: se calcula en tiempo de ejecución con "new").
     */
    private final List<Integer> tamanosArreglo = new ArrayList<>();

    private boolean inicializado = false; // ¿ya se le asignó un valor al menos una vez?

    /**
     * Fase 2: herencia, encapsulamiento y polimorfismo (solo clases de Zetariano).
     * Para cualquier otro simbolo estos campos quedan en su valor por defecto y no
     * cambian el comportamiento de la Fase 1.
     */

    // Modificador de acceso del miembro o de la clase. PUBLIC por defecto para que
    // los simbolos de Y? y PigLatin (que no tienen modificadores) sigan siendo visibles.
    private ModificadorAcceso modificador = ModificadorAcceso.PUBLIC;

    /**
     * Solo para ATRIBUTO / METODO / CONSTRUCTOR: la clase donde se declaro.
     * Sirve para el encapsulamiento (private -> solo su clase duena) y para saber
     * que etiqueta C3D llamar cuando el metodo es heredado.
     */
    private Simbolo claseDuena;

    /**
     * Solo para CLASE: nombre escrito despues de "extends" (null si no hereda) y el
     * simbolo ya enlazado. Pueden diferir un rato: el nombre se conoce al registrar
     * la clase y el enlace se hace cuando la clase padre ya esta en el ambito global.
     */
    private String nombreClasePadre;
    private Simbolo clasePadre;

    /**
     * Solo para CLASE: metodos y constructores declarados en ESTA clase, en orden.
     * Los miembros se guardan en la tabla hash con varias claves (nombre#aridad#tipos y
     * nombre#aridad), asi que sin estas listas no habria forma limpia de recorrerlos.
     */
    private final List<Simbolo> metodosDeclarados = new ArrayList<>();
    private final List<Simbolo> constructoresDeclarados = new ArrayList<>();

    /**
     * Solo para CLASE: tabla virtual para el despacho dinamico. Se arma a partir de la
     * del padre: un metodo que sobrescribe ocupa el mismo indice que el del padre, uno
     * nuevo se agrega al final. null mientras no se haya construido.
     */
    private List<Simbolo> tablaVirtual;

    // Solo para METODO: indice dentro de la tabla virtual de su clase (-1 si no es virtual: metodos private, que no se pueden sobrescribir).
    private int indiceVirtual = -1;

    public Simbolo(String nombre, CategoriaSimbolo categoria, Tipo tipo, int linea, int columna) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.tipo = tipo;
        this.linea = linea;
        this.columna = columna;
    }

    public String getNombre() {
        return nombre;
    }

    public CategoriaSimbolo getCategoria() {
        return categoria;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }

    public List<Simbolo> getParametros() {
        return parametros;
    }

    public void agregarParametro(Simbolo parametro) {
        this.parametros.add(parametro);
    }

    public TablaHash<String, Simbolo> getMiembros() {
        return miembros;
    }

    public boolean agregarMiembro(Simbolo m) {
        boolean nuevo = miembros.insertar(m.getNombre(), m);
        if (nuevo) miembrosEnOrden.add(m);
        return nuevo;
    }
    // util para la sobrecarga de datos en constructores
    public boolean agregarMiembroConClave(String clave, Simbolo miembro) {
        return miembros.insertar(clave, miembro);
    }

    /**
     * Miembros en el ORDEN en que se declararon (a diferencia de {@code getMiembros()},
     * que devuelve la TablaHash sin orden garantizado). Se usa para generar el C3D de
     * un inicializador de estructura/clase emparejando posicionalmente los valores con
     * los campos, y (Fase 4) para emitir el {@code struct} de C en el orden correcto.
     */
    public List<Simbolo> getMiembrosEnOrden() {
        return miembrosEnOrden;
    }

    // Busca el miembro en esta clase y, si no esta, sube por la cadena de herencia.
    public Simbolo buscarMiembro(String nombre) {
        Simbolo actual = this;
        while (actual != null) {
            Simbolo m = actual.miembros.obtener(nombre);
            if (m != null) return m;
            actual = actual.clasePadre;
        }
        return null;
    }

    // Solo en esta clase, sin subir al padre. Se usa para validar sobrescrituras.
    public Simbolo buscarMiembroLocal(String nombre) {
        return miembros.obtener(nombre);
    }

    // --- Encapsulamiento ---

    public ModificadorAcceso getModificador() {
        return modificador;
    }

    public void setModificador(ModificadorAcceso modificador) {
        this.modificador = (modificador != null) ? modificador : ModificadorAcceso.PUBLIC;
    }

    public Simbolo getClaseDuena() {
        return claseDuena;
    }

    public void setClaseDuena(Simbolo claseDuena) {
        this.claseDuena = claseDuena;
    }

    // --- Herencia ---

    public String getNombreClasePadre() {
        return nombreClasePadre;
    }

    public void setNombreClasePadre(String nombreClasePadre) {
        this.nombreClasePadre = nombreClasePadre;
    }

    public Simbolo getClasePadre() {
        return clasePadre;
    }

    // Enlaza la clase padre. Invalida la tabla virtual por si ya se habia construido.
    public void setClasePadre(Simbolo clasePadre) {
        this.clasePadre = clasePadre;
        this.tablaVirtual = null;
    }

    // true si declaro "extends X" pero X todavia no se pudo enlazar.
    public boolean tieneHerenciaPendiente() {
        return nombreClasePadre != null && clasePadre == null;
    }

    /**
     * true si esta clase es "otra" o hereda de ella (directa o indirectamente).
     * Se compara por nombre y no por referencia: un mismo nombre de clase puede llegar
     * como simbolos distintos (por ejemplo, dos imports de PigLatin que analizan la
     * misma clase en ambitos separados).
     * @param otra
     * @return
     */
    public boolean esSubclaseDe(Simbolo otra) {
        if (otra == null) return false;
        Simbolo actual = this;
        while (actual != null) {
            if (actual.getNombre().equals(otra.getNombre())) return true;
            actual = actual.clasePadre;
        }
        return false;
    }

    /**
     * Atributos para el layout del objeto: primero los del padre (recursivo) y despues
     * los propios. Con este orden un Perro empieza con exactamente los mismos campos y
     * en las mismas posiciones que un Animal, que es lo que permite tratarlo como Animal.
     * Incluye los private del padre: no son accesibles desde la hija pero si ocupan
     * espacio dentro del objeto.
     * @return
     */
    public List<Simbolo> getAtributosConHerencia() {
        List<Simbolo> resultado = new ArrayList<>();
        if (clasePadre != null) resultado.addAll(clasePadre.getAtributosConHerencia());
        for (Simbolo m : miembrosEnOrden) {
            if (m.getCategoria() == CategoriaSimbolo.ATRIBUTO) resultado.add(m);
        }
        return resultado;
    }

    public List<Simbolo> getMetodosDeclarados() {
        return metodosDeclarados;
    }

    public List<Simbolo> getConstructoresDeclarados() {
        return constructoresDeclarados;
    }

    // --- Polimorfismo ---

    /**
     * Tabla virtual de la clase. Se construye la primera vez que se pide:
     *  1) se copia la del padre (mismos indices)
     *  2) cada metodo propio no private que tenga la misma firma que uno heredado reemplaza ese indice (sobrescritura)
     *  3) los metodos nuevos se agregan al final
     * El indice de cada metodo queda guardado en el propio simbolo (indiceVirtual).
     * @return
     */
    public List<Simbolo> getTablaVirtual() {
        if (tablaVirtual != null) return tablaVirtual;

        List<Simbolo> tabla = new ArrayList<>();
        if (clasePadre != null) tabla.addAll(clasePadre.getTablaVirtual());

        for (Simbolo m : metodosDeclarados) {
            if (m.getModificador() == ModificadorAcceso.PRIVATE) {
                m.indiceVirtual = -1;
                continue;
            }
            String firma = m.firma();
            int indice = -1;
            for (int i = 0; i < tabla.size(); i++) {
                if (tabla.get(i).firma().equals(firma)) {
                    indice = i;
                    break;
                }
            }
            if (indice >= 0) {
                tabla.set(indice, m);
            } else {
                tabla.add(m);
                indice = tabla.size() - 1;
            }
            m.indiceVirtual = indice;
        }
        tablaVirtual = tabla;
        return tablaVirtual;
    }

    public int getIndiceVirtual() {
        return indiceVirtual;
    }

    // Firma de un metodo/constructor: nombre#aridad#Tipo1#Tipo2...
    // Es la misma clave especifica con la que AnalizadorSemanticoZ registra el miembro.
    public String firma() {
        StringBuilder sb = new StringBuilder(nombre).append("#").append(parametros.size());
        for (Simbolo p : parametros) {
            sb.append("#").append(p.getTipo() == null ? "?" : p.getTipo().nombre());
        }
        return sb.toString();
    }

    // Firma legible para mensajes de error: hablar(int, String)
    public String firmaLegible() {
        StringBuilder sb = new StringBuilder(nombre).append("(");
        for (int i = 0; i < parametros.size(); i++) {
            if (i > 0) sb.append(", ");
            Simbolo p = parametros.get(i);
            sb.append(p.getTipo() == null ? "?" : p.getTipo().nombre());
        }
        return sb.append(")").toString();
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public List<Integer> getTamanosArreglo() {
        return tamanosArreglo;
    }

    public boolean esArregloDeTamanoFijo() {
        return !tamanosArreglo.isEmpty();
    }

    public boolean isInicializado() {
        return inicializado;
    }

    public void marcarInicializado() {
        this.inicializado = true;
    }

    /** Descripción corta usada en mensajes de error: "función 'suma'", "estructura 'Persona'". */
    public String descripcionCorta() {
        String categoriaTexto = switch (categoria) {
            case VARIABLE -> "variable";
            case PARAMETRO -> "parámetro";
            case FUNCION -> "función";
            case ESTRUCTURA -> "estructura";
            case CAMPO -> "campo";
            case CLASE -> "clase";
            case ATRIBUTO -> "atributo";
            case METODO -> "método";
            case CONSTRUCTOR -> "constructor";
        };
        return categoriaTexto + " '" + nombre + "'";
    }

    @Override
    public String toString() {
        return descripcionCorta() + " : " + (tipo == null ? "?" : tipo.nombre());
    }
}