package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** primaria PUNTO ID (#primariaCampo): "objeto.campo". */
public final class AccesoCampo extends NodoY implements ExpresionY {

    private final ExpresionY objeto;
    private final String campo;

    /** Tipo del campo, cacheado por verificar() para que generarC3D no lo recalcule. */
    private Tipo tipoCampo;

    public AccesoCampo(ExpresionY objeto, String campo, int linea, int columna) {
        super(linea, columna);
        this.objeto = objeto;
        this.campo = campo;
    }

    public ExpresionY getObjeto() { return objeto; }
    public String getCampo() { return campo; }
    public Tipo getTipoCampo() { return tipoCampo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tObj = objeto.verificar(ambito, errores);
        if (!(tObj instanceof TipoEstructura te)) {
            if (!tObj.esDesconocido())
                errores.reportar(linea, columna,
                        "No se puede acceder a '" + campo + "' en tipo " + tObj.nombre());
            return TipoPrimitivo.DESCONOCIDO;
        }
        Simbolo campoSim = te.getDefinicion().buscarMiembro(campo);
        if (campoSim == null) {
            errores.reportar(linea, columna,
                    "La estructura '" + te.nombre() + "' no tiene campo '" + campo + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        tipoCampo = campoSim.getTipo();
        return tipoCampo;
    }

    /**
     * Emite: primero el C3D del objeto (queda su "base" en un temporal o nombre de
     * variable), luego (=., base, campo, t) con un temporal nuevo.
     * Devuelve temporal(t, tipoCampo) — si el campo es a su vez una estructura,
     * el temporal es una referencia a estructura (Fase 4 lo declara como tal).
     *
     * El nombre del campo viaja tal cual en la cuádrupla (no como offset): el cálculo
     * base+offset con el layout real lo hará Fase 4, que es la que conoce los sizeof.
     *
     * Encadenamiento "a.b.c": funciona porque el objeto de un AccesoCampo externo puede
     * ser otro AccesoCampo; el interno emite "t0 = a.b" y el externo emite "t1 = t0.c".
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