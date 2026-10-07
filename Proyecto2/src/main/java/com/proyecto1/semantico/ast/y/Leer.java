package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** LEER LPAREN RPAREN (#primariaLeer): lectura de entrada estándar. */
public final class Leer extends NodoY implements ExpresionY {
    public Leer(int linea, int columna) {
        super(linea, columna);
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // leer() se asigna a variables; se asume cadena por defecto.
        // Si quieres un comportamiento más fino, devuelve el tipo del contexto de asignación.
        return TipoPrimitivo.DESCONOCIDO;
    }

    /**
     * Emite UNA cuádrupla (read, null, null, t) con un temporal nuevo y devuelve
     * ese temporal con tipo CADENA. El padre (p. ej. una Asignacion) decide luego
     * si lo copia a una variable x = t, lo usa como argumento de imprimir
     * print t) o lo combina en una binaria.
     * Devuelve ResultadoC3D.temporal(t, CADENA).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String t = generador.nuevoTemporal();
        generador.emitirRead(t, nombreTipoEsperado());
        Tipo devuelto = (tipoEsperado != null) ? tipoEsperado : TipoPrimitivo.CADENA;
        return ResultadoC3D.temporal(t, devuelto);
    }

    /** Tipo que el contexto espera de esta lectura (lo fija quien la recibe). */
    private Tipo tipoEsperado;

    public void setTipoEsperado(Tipo t) { this.tipoEsperado = t; }

    private String nombreTipoEsperado() {
        if (tipoEsperado == TipoPrimitivo.ENTERO)   return "entero";
        if (tipoEsperado == TipoPrimitivo.FLOTANTE) return "flotante";
        if (tipoEsperado == TipoPrimitivo.CARACTER) return "caracter";
        if (tipoEsperado == TipoPrimitivo.BOOL)     return "bool";
        return "cadena";
    }
}
