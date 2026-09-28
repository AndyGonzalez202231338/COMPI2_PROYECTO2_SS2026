package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**
 * Identificador de Z. Se diferencia de Y en un caso clave: si el símbolo resuelto
 * es un ATRIBUTO de la clase actual, no es una variable local, es un acceso
 * implícito a "this.<nombre>". Eso se emite como carga de campo.
 *
 * Motivo: AmbitoContenedor.declararMiembro registra los atributos en la tabla
 * del método/constructor con su nombre plano (por eso "return edad;" resuelve
 * solo). Basta con mirar la categoría del símbolo: ATRIBUTO -> this.<nombre>;
 * VARIABLE/PARAMETRO -> acceso local normal.
 */
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
        // En Z solo las variables locales/parámetros exigen inicialización previa;
        // un atributo puede leerse sin "inicializar" explícitamente (su init lo hace
        // el constructor).
        if (!s.isInicializado() && s.getCategoria() == CategoriaSimbolo.VARIABLE) {
            errores.reportar(linea, columna, "Variable '" + nombre + "' usada sin inicializar");
        }
        return s.getTipo();
    }

    /**
     * Dos casos según la categoría del símbolo:
     *   ATRIBUTO: emite (=., this, nombre, t) y devuelve
     *       temporal(t, tipo). Es un acceso implícito a "this.nombre".
     *   VARIABLE / PARAMETRO / otro: comportamiento idéntico al de Y:
     *       no emite nada y devuelve valor(nombre, tipo).
     * Si el generador no tiene ámbito o el símbolo no se resuelve, cae al caso Y
     * (devuelve valor(nombre, DESCONOCIDO)); es el comportamiento degradado
     * cuando se genera C3D sin análisis semántico previo.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        Ambito ambito = generador.getAmbito();
        Tipo tipo = TipoPrimitivo.DESCONOCIDO;
        CategoriaSimbolo categoria = null;

        if (ambito != null) {
            Simbolo s = ambito.resolver(nombre);
            if (s != null && s.getTipo() != null) {
                tipo = s.getTipo();
                categoria = s.getCategoria();
            }
        }

        if (categoria == CategoriaSimbolo.ATRIBUTO) {
            String t = generador.nuevoTemporal();
            generador.emitirCargaCampo("this", nombre, t);
            return ResultadoC3D.temporal(t, tipo);
        }

        // Variable local o parámetro: igual que en Y.
        return ResultadoC3D.valor(nombre, tipo);
    }
}