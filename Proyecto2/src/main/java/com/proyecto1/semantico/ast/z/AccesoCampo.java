package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Acceso;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**  primaryExpression PUNTO ID (#primarioCampo): "objeto.campo". */
public final class AccesoCampo extends NodoZ implements ExpresionZ {

    private final ExpresionZ objeto;
    private final String campo;
    private boolean esLength;

    /** Tipo del campo, cacheado por verificar() para que generarC3D no lo recalcule. */
    private Tipo tipoCampo;

    /** Nombre de la clase DECLARADA del objeto, cacheado para resolver el offset del
     *  campo sin ambiguedad (ver claseObjeto en CuadruplaCampoCarga). */
    private String claseObjeto;

    public AccesoCampo(ExpresionZ objeto, String campo, int linea, int columna) {
        super(linea, columna);
        this.objeto = objeto;
        this.campo = campo;
    }

    public ExpresionZ getObjeto() { return objeto; }
    public String getCampo() { return campo; }
    public Tipo getTipoCampo() { return tipoCampo; }
    public String getClaseObjeto() { return claseObjeto; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tObj = objeto.verificar(ambito, errores);
        if (tObj instanceof TipoArreglo && "length".equals(campo)) {
            this.tipoCampo = TipoPrimitivo.ENTERO;
            this.esLength = true;
            return TipoPrimitivo.ENTERO;
        }
        if (!(tObj instanceof TipoClase tc)) {
            if (!tObj.esDesconocido())
                errores.reportar(linea, columna,
                        "No se puede acceder a '" + campo + "' en tipo " + tObj.nombre());
            return TipoPrimitivo.DESCONOCIDO;
        }
        Simbolo miembro = tc.getDefinicion().buscarMiembro(campo);
        if (miembro == null) {
            errores.reportar(linea, columna,
                    "La clase '" + tc.nombre() + "' no tiene miembro '" + campo + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        if (miembro.getCategoria() != CategoriaSimbolo.ATRIBUTO) {
            errores.reportar(linea, columna, "'" + campo + "' no es un atributo accesible");
            return TipoPrimitivo.DESCONOCIDO;
        }
        // Encapsulamiento: private/protected segun la clase desde la que se accede.
        // Si falla se sigue con el tipo real del campo para no generar errores en cascada.
        Acceso.verificar(miembro, ambito, errores, linea, columna);
        tipoCampo = miembro.getTipo();
        this.claseObjeto = tc.getDefinicion().getNombre();
        return tipoCampo;
    }

    /**
     * Emite: C3D del objeto (queda su referencia en un temporal o variable), luego
     * (=., base, campo, t). Devuelve temporal(t, tipoCampo).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        ResultadoC3D base = objeto.generarC3D(generador);
        String t = generador.nuevoTemporal();
        if (esLength) {
            generador.emitirCargaIndice(base.getLugar(), "-1", t);
            return ResultadoC3D.temporal(t, TipoPrimitivo.ENTERO);
        }
        String tipoCampoStr = (tipoCampo != null) ? tipoCampo.nombre() : null;
        boolean esPuntero = (tipoCampo != null && tipoCampo.esArreglo());
        generador.emitirCargaCampo(base.getLugar(), campo, t, tipoCampoStr, esPuntero, claseObjeto);
        Tipo tipo = (tipoCampo != null) ? tipoCampo : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(t, tipo);
    }
}