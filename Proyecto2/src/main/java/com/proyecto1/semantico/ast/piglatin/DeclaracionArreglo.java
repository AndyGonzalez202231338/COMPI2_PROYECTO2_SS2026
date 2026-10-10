package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

public final class DeclaracionArreglo extends NodoPigLatin implements InstruccionPigLatin {

    private final String nombre;
    private final int tamano;
    private final NodoTipoRef tipo;
    private final InicializadorArreglo inicializador; // null si no hay "= { ... }"

    private Tipo tipoBaseCache;

    public DeclaracionArreglo(String nombre, int tamano, NodoTipoRef tipo, InicializadorArreglo inicializador,
                              int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
        this.tamano = tamano;
        this.tipo = tipo;
        this.inicializador = inicializador;
    }

    public String getNombre() { return nombre; }
    public int getTamano() { return tamano; }
    public NodoTipoRef getTipo() { return tipo; }
    public InicializadorArreglo getInicializador() { return inicializador; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo base = tipo.resolver(ambito, errores);
        this.tipoBaseCache = base;

        Tipo tArr = new TipoArreglo(base);

        Simbolo s = new Simbolo(nombre, CategoriaSimbolo.VARIABLE, tArr, linea, columna);
        s.getTamanosArreglo().add(tamano);
        if (!ambito.declarar(s)) {
            errores.reportar(linea, columna, "Arreglo ya declarado: '" + nombre + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        if (inicializador != null)
            inicializador.verificar(ambito, errores);
        s.marcarInicializado();
        return TipoPrimitivo.VOID;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // Resolver nombre unico.
        String destino = nombre;
        Ambito amb = generador.getAmbito();
        if (amb != null) {
            Simbolo sv = amb.resolver(nombre);
            if (sv != null) destino = sv.getNombreC3D();
        }
        if (inicializador != null) {
            ResultadoC3D v = inicializador.generarC3D(generador);
            generador.emitirAsignacion(v.getLugar(), destino);
            return ResultadoC3D.vacio();
        }

        // Sin inicializador: reservar el bloque.
        String descriptor = descriptorElemento();
        java.util.List<String> tamanos = java.util.List.of(String.valueOf(tamano));
        String t = generador.nuevoTemporal();
        generador.emitirNewArray(descriptor, tamanos, t);
        generador.emitirAsignacion(t, destino);
        return ResultadoC3D.vacio();
    }

    private String descriptorElemento() {
        return tipoAC(tipoBaseCache);
    }

    private static String tipoAC(Tipo t) {
        if (t == null) return "int";
        if (t == TipoPrimitivo.ENTERO)   return "int";
        if (t == TipoPrimitivo.FLOTANTE) return "double";
        if (t == TipoPrimitivo.CARACTER) return "char";
        if (t == TipoPrimitivo.CADENA)   return "char*";
        if (t == TipoPrimitivo.BOOL)     return "int";
        if (t instanceof com.proyecto1.semantico.tipos.TipoClase tc)
            return tc.getDefinicion().getNombre() + "*";
        if (t instanceof com.proyecto1.semantico.tipos.TipoEstructura te)
            return te.getDefinicion().getNombre() + "*";
        if (t instanceof TipoArreglo ta)
            return tipoAC(ta.getBase()) + "*";
        return "int";
    }
}