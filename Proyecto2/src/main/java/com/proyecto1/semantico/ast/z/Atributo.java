package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.AmbitoClase;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * Un fieldDeclaration (#fieldDeclarationDef): "tipo ID (= expresion)? ;".
 * A diferencia de {@code CampoEstructura} de Y?, no necesita una lista de tamaños de
 * arreglo aparte: en Z el arreglo ya viene incluido en tipo
 */
public final class Atributo extends NodoZ {

    private final NodoTipoRef tipo;
    private final String nombre;
    private final ExpresionZ inicializador; // null si no hay "= expresion"

    // Fase 2: modificador de acceso; DEFAULT si no se escribio ninguno.
    private final ModificadorAcceso modificador;

    // Constructor de la Fase 1: atributo public.
    public Atributo(NodoTipoRef tipo, String nombre, ExpresionZ inicializador, int linea, int columna) {
        this(ModificadorAcceso.PUBLIC, tipo, nombre, inicializador, linea, columna);
    }

    public Atributo(ModificadorAcceso modificador, NodoTipoRef tipo, String nombre,
                    ExpresionZ inicializador, int linea, int columna) {
        super(linea, columna);
        this.modificador = (modificador != null) ? modificador : ModificadorAcceso.DEFAULT;
        this.tipo = tipo;
        this.nombre = nombre;
        this.inicializador = inicializador;
    }

    public ModificadorAcceso getModificador() {
        return modificador;
    }

    public NodoTipoRef getTipo() {
        return tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public ExpresionZ getInicializador() {
        return inicializador;
    }

    public void verificar(AmbitoClase amb, ManejadorErrores errores) {
        Tipo t = tipo.resolver(amb, errores);
        // AnalizadorSemanticoZ ya registró este atributo en su primera pasada. Si el símbolo que
        // hay en el ámbito es ESTE mismo (misma posición), no hay nada que declarar: intentarlo de
        // nuevo lo marcaba como "duplicado" de sí mismo en TODA clase con atributos. Un duplicado
        // real (otro atributo con el mismo nombre, en otra posición) sí se sigue reportando.
        Simbolo existente = amb.resolverLocal(nombre);
        boolean yaRegistradoEnPrimeraPasada = existente != null
                && existente.getCategoria() == CategoriaSimbolo.ATRIBUTO
                && existente.getLinea() == linea && existente.getColumna() == columna;
        if (!yaRegistradoEnPrimeraPasada) {
            Simbolo s = new Simbolo(nombre, CategoriaSimbolo.ATRIBUTO, t, linea, columna);
            if (!amb.declararMiembro(s))
                errores.reportar(linea, columna, "Atributo duplicado: '" + nombre + "'");
        }

        if (inicializador != null) {
            Tipo tInit = inicializador.verificar(amb, errores);
            if (!Tipos.esAsignable(t, tInit))
                errores.reportar(linea, columna,
                        "Inicialización incompatible: " + tInit.nombre() + " -> " + t.nombre());
        }
    }
}