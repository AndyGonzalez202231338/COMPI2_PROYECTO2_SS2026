package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.ResolucionMiembros;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.List;

public final class Llamada extends NodoY implements ExpresionY {

    private final ExpresionY objetivo;
    private final List<ExpresionY> argumentos;

    // Cacheado en verificar() para saber la etiqueta exacta al generar C3D.
    private Simbolo simboloMetodo;

    public Llamada(ExpresionY objetivo, List<ExpresionY> argumentos, int linea, int columna) {
        super(linea, columna);
        this.objetivo = objetivo;
        this.argumentos = argumentos;
    }

    public ExpresionY getObjetivo() {
        return objetivo;
    }

    public List<ExpresionY> getArgumentos() {
        return argumentos;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // CASO 1: Funciones normales de Y?
        if (objetivo instanceof Identificador id) {
            Simbolo f = ambito.resolver(id.getNombre());
            if (f == null || (f.getCategoria() != CategoriaSimbolo.FUNCION
                    && f.getCategoria() != CategoriaSimbolo.METODO)) {
                errores.reportar(linea, columna, "Función no declarada: '" + id.getNombre() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            List<Simbolo> params = f.getParametros();
            if (params.size() != argumentos.size()) {
                errores.reportar(linea, columna,
                        "Función '" + f.getNombre() + "' espera " + params.size() +
                                " argumentos, recibió " + argumentos.size());
                return f.getTipo();
            }
            for (int i = 0; i < argumentos.size(); i++) {
                Tipo ta = argumentos.get(i).verificar(ambito, errores);
                if (!Tipos.esAsignable(params.get(i).getTipo(), ta))
                    errores.reportar(argumentos.get(i).getLinea(), argumentos.get(i).getColumna(),
                            "Argumento " + (i+1) + " incompatible: se esperaba " +
                                    params.get(i).getTipo().nombre() + ", se recibió " + ta.nombre());
            }
            return f.getTipo();
        }

        // CASO 2: Llamadas a métodos de objetos (p.getNombre())
        if (objetivo instanceof AccesoCampo ac) {
            Tipo tObj = ac.getObjeto().verificar(ambito, errores);
            if (tObj instanceof TipoClase tc) {
                List<Tipo> tiposArgs = new ArrayList<>();
                for (ExpresionY a : argumentos) tiposArgs.add(a.verificar(ambito, errores));

                Simbolo m = ResolucionMiembros.resolverMetodo(tc.getDefinicion(), ac.getCampo(), tiposArgs);
                if (m == null) {
                    errores.reportar(linea, columna, "La clase '" + tc.nombre() + "' no tiene método '" + ac.getCampo() + "'");
                    return TipoPrimitivo.DESCONOCIDO;
                }
                this.simboloMetodo = m;

                List<Simbolo> params = m.getParametros();
                if (params.size() != argumentos.size()) {
                    errores.reportar(linea, columna, "El método '" + ac.getCampo() + "' espera " + params.size() + " argumentos.");
                } else {
                    for (int i = 0; i < argumentos.size(); i++) {
                        Tipo ta = tiposArgs.get(i);
                        if (!Tipos.esAsignable(params.get(i).getTipo(), ta))
                            errores.reportar(argumentos.get(i).getLinea(), argumentos.get(i).getColumna(),
                                    "Argumento " + (i+1) + " incompatible.");
                    }
                }
                return m.getTipo();
            } else {
                errores.reportar(linea, columna, "Llamada inválida");
                return TipoPrimitivo.DESCONOCIDO;
            }
        }

        errores.reportar(linea, columna, "Llamada inválida");
        return TipoPrimitivo.DESCONOCIDO;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // CASO 1: Llamada a función libre
        if (objetivo instanceof Identificador id) {
            List<String> lugaresArgs = new ArrayList<>();
            for (ExpresionY a : argumentos) {
                lugaresArgs.add(a.generarC3D(generador).getLugar());
            }
            for (String lugar : lugaresArgs) {
                generador.emitirParam(lugar);
            }
            String t = generador.nuevoTemporal();
            Tipo tipoRetorno = TipoPrimitivo.DESCONOCIDO;
            Ambito amb = generador.getAmbito();
            if (amb != null) {
                Simbolo f = amb.resolver(id.getNombre());
                if (f != null && f.getTipo() != null) tipoRetorno = f.getTipo();
            }
            generador.emitirCall(id.getNombre(), argumentos.size(), t, tipoRetorno.nombre());
            return ResultadoC3D.temporal(t, tipoRetorno);
        }

        // CASO 2: Llamada a método (p.getNombre())
        if (objetivo instanceof AccesoCampo ac) {
            ResultadoC3D base = ac.getObjeto().generarC3D(generador);
            List<String> lugaresArgs = new ArrayList<>();
            List<Tipo> tiposRealesArgs = new ArrayList<>();
            for (ExpresionY a : argumentos) {
                ResultadoC3D v = a.generarC3D(generador);
                lugaresArgs.add(v.getLugar());
                tiposRealesArgs.add(v.getTipo());
            }

            generador.emitirParam(base.getLugar());
            for (String lugar : lugaresArgs) {
                generador.emitirParam(lugar);
            }

            // Construir tiposFormales con fallback a los tipos reales de los
            // argumentos, por si el simbolo tiene tipos rotos (clase Z no
            // resuelta al momento de copiar la tabla virtual).
            List<Tipo> tiposFormales = new ArrayList<>();
            if (simboloMetodo != null) {
                List<Simbolo> params = simboloMetodo.getParametros();
                for (int i = 0; i < params.size(); i++) {
                    Tipo tp = params.get(i).getTipo();
                    String n = (tp != null) ? tp.nombre() : null;
                    boolean roto = (n == null || n.isBlank() || n.replaceAll("[^A-Za-z0-9]", "").isEmpty());
                    if (roto && i < tiposRealesArgs.size() && tiposRealesArgs.get(i) != null) {
                        tp = tiposRealesArgs.get(i);
                    }
                    tiposFormales.add(tp != null ? tp : TipoPrimitivo.DESCONOCIDO);
                }
            }

            String clase = (simboloMetodo != null && simboloMetodo.getClaseDuena() != null)
                    ? simboloMetodo.getClaseDuena().getNombre() : "?";
            String metodo = ac.getCampo();
            String etiqueta = GeneradorC3D.etiquetaMetodo(clase, metodo, tiposFormales);

            String t = generador.nuevoTemporal();
            Tipo tipoRetorno = (simboloMetodo != null && simboloMetodo.getTipo() != null)
                    ? simboloMetodo.getTipo() : TipoPrimitivo.DESCONOCIDO;

            generador.emitirCall(etiqueta, argumentos.size() + 1, t, tipoRetorno.nombre());
            return ResultadoC3D.temporal(t, tipoRetorno);
        }

        throw new UnsupportedOperationException("Llamada inválida");
    }
}