package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

public final class NodoTipoRef {

    private final String nombreBase;
    private final boolean esPrimitivo;
    private final int dimensiones;
    private final int linea;
    private final int columna;

    public NodoTipoRef(String nombreBase, boolean esPrimitivo, int dimensiones,
                       int linea, int columna) {
        this.nombreBase = nombreBase;
        this.esPrimitivo = esPrimitivo;
        this.dimensiones = dimensiones;
        this.linea = linea;
        this.columna = columna;
    }

    public String getNombreBase() { return nombreBase; }
    public boolean isEsPrimitivo() { return esPrimitivo; }
    public int getDimensiones() { return dimensiones; }
    public boolean esArreglo() { return dimensiones > 0; }

    public Tipo resolver(Ambito ambito, ManejadorErrores errores) {
        Tipo base;
        if (esPrimitivo) {
            base = switch (nombreBase) {
                case "int"     -> TipoPrimitivo.ENTERO;
                case "double"  -> TipoPrimitivo.FLOTANTE;
                case "char"    -> TipoPrimitivo.CARACTER;
                case "String"  -> TipoPrimitivo.CADENA;
                case "boolean" -> TipoPrimitivo.BOOL;
                default        -> TipoPrimitivo.DESCONOCIDO;
            };
        } else {
            Simbolo s = ambito.ambitoGlobal().resolverLocal(nombreBase);
            if (s == null || s.getCategoria() != CategoriaSimbolo.CLASE) {
                errores.reportar(linea, columna, "Tipo desconocido: '" + nombreBase + "'");
                base = TipoPrimitivo.DESCONOCIDO;
            } else {
                base = new TipoClase(s);
            }
        }
        // Envolver según dimensiones: int[][] → TipoArreglo(TipoArreglo(ENTERO))
        for (int i = 0; i < dimensiones; i++) base = new TipoArreglo(base);
        return base;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(nombreBase);
        for (int i = 0; i < dimensiones; i++) sb.append("[]");
        return sb.toString();
    }
}