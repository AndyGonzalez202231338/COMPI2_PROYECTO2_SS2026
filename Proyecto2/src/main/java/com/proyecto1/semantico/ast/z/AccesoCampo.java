package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/**  primaryExpression PUNTO ID (#primarioCampo): "objeto.campo". */
public final class AccesoCampo extends NodoZ implements ExpresionZ {

    private final ExpresionZ objeto;
    private final String campo;

    /** Tipo del campo, cacheado por verificar() para que generarC3D no lo recalcule. */
    private Tipo tipoCampo;

    public AccesoCampo(ExpresionZ objeto, String campo, int linea, int columna) {
        super(linea, columna);
        this.objeto = objeto;
        this.campo = campo;
    }

    public ExpresionZ getObjeto() { return objeto; }
    public String getCampo() { return campo; }
    public Tipo getTipoCampo() { return tipoCampo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tObj = objeto.verificar(ambito, errores);
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
        tipoCampo = miembro.getTipo();
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
        generador.emitirCargaCampo(base.getLugar(), campo, t);
        Tipo tipo = (tipoCampo != null) ? tipoCampo : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(t, tipo);
    }
}