package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

/** primaria PUNTO ID (#primariaCampo): "objeto.campo". */
public final class AccesoCampo extends NodoY implements ExpresionY {

    private final ExpresionY objeto;
    private final String campo;

    private Tipo tipoCampo;
    private String claseObjeto;

    public AccesoCampo(ExpresionY objeto, String campo, int linea, int columna) {
        super(linea, columna);
        this.objeto = objeto;
        this.campo = campo;
    }

    public ExpresionY getObjeto() { return objeto; }
    public String getCampo() { return campo; }
    public Tipo getTipoCampo() { return tipoCampo; }
    public String getClaseObjeto() { return claseObjeto; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tObj = objeto.verificar(ambito, errores);
        Simbolo def;

        if (tObj instanceof TipoEstructura te) {
            def = te.getDefinicion();
        } else if (tObj instanceof TipoClase tc) {
            def = tc.getDefinicion();
        } else {
            if (!tObj.esDesconocido())
                errores.reportar(linea, columna,
                        "No se puede acceder a '" + campo + "' en tipo " + tObj.nombre());
            return TipoPrimitivo.DESCONOCIDO;
        }

        Simbolo campoSim = def.buscarMiembro(campo);
        if (campoSim == null) {
            errores.reportar(linea, columna,
                    "'" + def.getNombre() + "' no tiene miembro '" + campo + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        tipoCampo = campoSim.getTipo();
        this.claseObjeto = def.getNombre();
        return tipoCampo;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        ResultadoC3D base = objeto.generarC3D(generador);
        String t = generador.nuevoTemporal();
        String tipoCampoStr = (tipoCampo != null) ? tipoCampo.nombre() : null;

        boolean esPuntero = (tipoCampo != null && tipoCampo.esArreglo());

        generador.emitirCargaCampo(base.getLugar(), campo, t, tipoCampoStr, esPuntero, claseObjeto);
        Tipo tipo = (tipoCampo != null) ? tipoCampo : TipoPrimitivo.DESCONOCIDO;
        return ResultadoC3D.temporal(t, tipo);
    }

    public void setTipoCampo(Tipo tipoCampo) {
        this.tipoCampo = tipoCampo;
    }
}