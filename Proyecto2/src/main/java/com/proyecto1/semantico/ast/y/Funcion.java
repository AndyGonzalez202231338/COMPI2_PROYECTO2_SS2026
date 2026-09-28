package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoFuncion;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.ArrayList;
import java.util.List;

public final class Funcion extends NodoY {

    private final String nombre;
    private final List<Parametro> parametros;
    private final NodoTipoRef tipoRetorno;
    private final Bloque cuerpo;
    private List<Tipo> tiposParamsCache;
    private Tipo tipoRetornoCache;

    public Funcion(String nombre, List<Parametro> parametros, NodoTipoRef tipoRetorno,
                   Bloque cuerpo, int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
        this.parametros = parametros;
        this.tipoRetorno = tipoRetorno;
        this.cuerpo = cuerpo;
    }

    public String getNombre() { return nombre; }
    public List<Parametro> getParametros() { return parametros; }
    public NodoTipoRef getTipoRetorno() { return tipoRetorno; }
    public boolean esVoid() { return tipoRetorno == null; }
    public Bloque getCuerpo() { return cuerpo; }

    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Simbolo simbolo = ambito.resolverLocal(nombre);
        AmbitoFuncion amb = new AmbitoFuncion(ambito, simbolo);

        tiposParamsCache = new ArrayList<>();
        for (Parametro p : parametros) {
            Tipo t = p.resolverTipo(amb, errores);
            tiposParamsCache.add(t);
            Simbolo sp = new Simbolo(p.getNombre(), CategoriaSimbolo.PARAMETRO, t,
                    p.getLinea(), p.getColumna());
            if (!amb.declarar(sp))
                errores.reportar(p.getLinea(), p.getColumna(),
                        "Parámetro duplicado: '" + p.getNombre() + "'");
            simbolo.agregarParametro(sp);
        }
        tipoRetornoCache = (tipoRetorno == null)
                ? TipoPrimitivo.VOID
                : tipoRetorno.resolver(amb, errores);

        cuerpo.verificar(amb, errores);

        if (!amb.esVoid() && !amb.isTuvoRetorno()) {
            errores.reportar(linea, columna,
                    "La función '" + nombre + "' debe retornar un valor de tipo "
                            + amb.getTipoRetorno().nombre());
        }
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, en este orden: (begin_func, nombre, nParametros, null), todas las
     * cuádruplas del cuerpo (vía Bloque#generarC3D) y (end_func).
     * Devuelve {@code ResultadoC3D.vacio()}.
     *
     * No emite un return implícito al final de funciones void; la Fase 4 puede
     * resolverlo al traducir end_func.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        /*
        definir prueba():
            entero x = 0
            x = 5

        definir prueba() -> entero:
            retornar 123
         */
        //begin_func prueba 0
        //cuadruplas(prueba, 0)
        // Registrar firma ANTES del begin_func.
        List<GeneradorC3D.ParametroFirma> pfs = new ArrayList<>();
        for (int i = 0; i < parametros.size(); i++) {
            Tipo t = (tiposParamsCache != null && i < tiposParamsCache.size())
                    ? tiposParamsCache.get(i) : TipoPrimitivo.DESCONOCIDO;
            pfs.add(new GeneradorC3D.ParametroFirma(parametros.get(i).getNombre(), t));
        }
        Tipo tipoRet = (tipoRetornoCache != null) ? tipoRetornoCache : TipoPrimitivo.VOID;
        generador.registrarFirma(nombre, pfs, tipoRet, false);

        generador.emitirBeginFunc(nombre, parametros.size());
        cuerpo.generarC3D(generador);
        generador.emitirEndFunc();
        return ResultadoC3D.vacio();
    }
}