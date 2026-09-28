package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** Un identificador usado como expresión (#primariaIdentificador): variable, o base de "obj.campo" / "arr[i]" / "f(...)". */
public final class Identificador extends NodoPigLatin implements ExpresionPigLatin {

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
            errores.reportar(linea, columna, "Identificador no declarado: '" + nombre + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        return s.getTipo();
    }

    /**
     * Emite: NADA (leer una variable no necesita cuádruplas).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        Tipo tipo = TipoPrimitivo.DESCONOCIDO;
        Ambito ambito = generador.getAmbito();
        if (ambito != null) {
            Simbolo s = ambito.resolver(nombre);
            if (s != null && s.getTipo() != null) {
                tipo = s.getTipo();
            }
        }
        return ResultadoC3D.valor(nombre, tipo);
    }
}