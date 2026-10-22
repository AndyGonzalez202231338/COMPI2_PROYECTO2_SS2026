package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Acceso;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

public final class Identificador extends NodoZ implements ExpresionZ {

    private final String nombre;

    public Identificador(String nombre, int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
    }

    public String getNombre() { return nombre; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Simbolo s = ambito.resolver(nombre);
        if (s == null) {
            errores.reportar(linea, columna, "Variable no declarada: '" + nombre + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        if (!s.isInicializado() && s.getCategoria() == CategoriaSimbolo.VARIABLE) {
            errores.reportar(linea, columna, "Variable '" + nombre + "' usada sin inicializar");
        }
        if (s.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
            Acceso.verificar(s, ambito, errores, linea, columna);
        }
        return s.getTipo();
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        Ambito ambito = generador.getAmbito();
        Tipo tipo = TipoPrimitivo.DESCONOCIDO;
        CategoriaSimbolo categoria = null;

        Simbolo simb = null;
        if (ambito != null) {
            simb = ambito.resolver(nombre);
            if (simb != null && simb.getTipo() != null) {
                tipo = simb.getTipo();
                categoria = simb.getCategoria();
            }
        }

        if (categoria == CategoriaSimbolo.ATRIBUTO) {
            String t = generador.nuevoTemporal();
            String tipoStr = (tipo != null) ? tipo.nombre() : null;
            boolean esPuntero = (tipo != null && tipo.esArreglo());
            generador.emitirCargaCampo("this", nombre, t, tipoStr, esPuntero);
            return ResultadoC3D.temporal(t, tipo);
        }

        String nombreC3D = (simb != null) ? simb.getNombreC3D() : nombre;
        return ResultadoC3D.valor(nombreC3D, tipo);
    }
}