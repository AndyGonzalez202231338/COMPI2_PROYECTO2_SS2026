package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** primaria . ID (#primariaCampo): "objeto.campo". */
public final class AccesoCampo extends NodoPigLatin implements ExpresionPigLatin {

    private final ExpresionPigLatin objeto;
    private final String campo;

    /** Tipo del campo, cacheado por verificar() para que generarC3D no lo recalcule. */
    private Tipo tipoCampo;

    public AccesoCampo(ExpresionPigLatin objeto, String campo, int linea, int columna) {
        super(linea, columna);
        this.objeto = objeto;
        this.campo = campo;
    }

    public ExpresionPigLatin getObjeto() { return objeto; }
    public String getCampo() { return campo; }
    public Tipo getTipoCampo() { return tipoCampo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tObj = objeto.verificar(ambito, errores);
        Simbolo def;
        if (tObj instanceof TipoEstructura te) def = te.getDefinicion();
        else if (tObj instanceof TipoClase tc) def = tc.getDefinicion();
        else {
            if (!tObj.esDesconocido())
                errores.reportar(linea, columna, "No se puede acceder a '" + campo + "' en tipo " + tObj.nombre());
            return TipoPrimitivo.DESCONOCIDO;
        }
        Simbolo m = def.buscarMiembro(campo);
        if (m == null) {
            errores.reportar(linea, columna,
                    "'" + def.getNombre() + "' no tiene miembro '" + campo + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        tipoCampo = m.getTipo();
        return tipoCampo;
    }

    /**
     * Emite: primero el C3D del objeto (queda su base en un temporal o nombre de
     * variable), luego UNA cuádrupla (=., base, campo, t) con un temporal
     * nuevo. Devuelve ResultadoC3D.temporal(t, tipoCampo).
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