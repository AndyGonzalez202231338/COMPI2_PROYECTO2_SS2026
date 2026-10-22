package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.*;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Llamada extends NodoZ implements ExpresionZ {

    private final ExpresionZ objetivo;
    private final List<ExpresionZ> argumentos;
    private Simbolo simboloMetodo;
    private boolean superConstructorAutorizado = false;
    private boolean despachoDinamico = false;
    private Simbolo claseReceptor;

    public Llamada(ExpresionZ objetivo, List<ExpresionZ> argumentos, int linea, int columna) {
        super(linea, columna);
        this.objetivo = objetivo;
        this.argumentos = argumentos;
    }

    public ExpresionZ getObjetivo() { return objetivo; }
    public List<ExpresionZ> getArgumentos() { return argumentos; }
    public Simbolo getSimboloMetodo() { return simboloMetodo; }

    public boolean esSuperConstructor() {
        return objetivo instanceof Super;
    }

    public void autorizarSuperConstructor() {
        this.superConstructorAutorizado = true;
    }

    public boolean esDespachoDinamico() {
        return despachoDinamico;
    }

    public int getIndiceVirtual() {
        return (simboloMetodo == null) ? -1 : simboloMetodo.getIndiceVirtual();
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        if (objetivo instanceof Super sup) {
            return verificarSuperConstructor(sup, ambito, errores);
        }

        if (objetivo instanceof Identificador id) {
            AmbitoClase ambClase = ambito.ambitoClaseMasCercano();
            if (ambClase == null) {
                errores.reportar(linea, columna,
                        "No se puede resolver el método '" + id.getNombre() + "' fuera de una clase");
                return TipoPrimitivo.DESCONOCIDO;
            }

            List<Tipo> tiposArgs = verificarArgumentos(ambito, errores);
            Simbolo clase = ambClase.getSimboloContenedor();
            Simbolo m = ResolucionMiembros.resolverMetodo(clase, id.getNombre(), tiposArgs);

            if (m == null) {
                Simbolo c = clase.buscarMiembroLocal(id.getNombre() + "#" + argumentos.size());
                if (c != null && c.getCategoria() == CategoriaSimbolo.CONSTRUCTOR) m = c;
            }

            if (m == null) {
                errores.reportar(linea, columna, "Método no declarado: '" + id.getNombre() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloMetodo = m;
            this.claseReceptor = clase;
            Acceso.verificar(m, ambito, errores, linea, columna);
            this.despachoDinamico = esVirtual(m);
            return verificarArgumentosYRetorno(m, tiposArgs, errores);
        }

        if (objetivo instanceof AccesoCampo ac) {
            Tipo tObj = ac.getObjeto().verificar(ambito, errores);
            if (!(tObj instanceof TipoClase tc)) {
                if (!tObj.esDesconocido())
                    errores.reportar(linea, columna,
                            "No se puede llamar método sobre " + tObj.nombre());
                return TipoPrimitivo.DESCONOCIDO;
            }

            List<Tipo> tiposArgs = verificarArgumentos(ambito, errores);
            Simbolo m = ResolucionMiembros.resolverMetodo(tc.getDefinicion(), ac.getCampo(), tiposArgs);

            if (m == null) {
                errores.reportar(linea, columna,
                        "La clase '" + tc.nombre() + "' no tiene método '" + ac.getCampo() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloMetodo = m;
            this.claseReceptor = tc.getDefinicion();
            Acceso.verificar(m, ambito, errores, linea, columna);
            this.despachoDinamico = !(ac.getObjeto() instanceof Super) && esVirtual(m);
            return verificarArgumentosYRetorno(m, tiposArgs, errores);
        }

        errores.reportar(linea, columna, "Llamada inválida");
        return TipoPrimitivo.DESCONOCIDO;
    }

    private Tipo verificarSuperConstructor(Super sup, Ambito ambito, ManejadorErrores errores) {
        List<Tipo> tiposArgs = verificarArgumentos(ambito, errores);

        if (!superConstructorAutorizado) {
            errores.reportar(linea, columna,
                    "super(...) solo puede usarse como primera sentencia de un constructor");
            return TipoPrimitivo.VOID;
        }

        Tipo tPadre = sup.verificar(ambito, errores);
        if (!(tPadre instanceof TipoClase tc)) return TipoPrimitivo.VOID;

        Simbolo padre = tc.getDefinicion();
        Simbolo ctor = ResolucionMiembros.resolverConstructor(padre, tiposArgs);
        if (ctor == null) {
            errores.reportar(linea, columna,
                    "La clase padre '" + padre.getNombre() + "' no tiene un constructor con "
                            + argumentos.size() + " argumentos");
            return TipoPrimitivo.VOID;
        }
        this.simboloMetodo = ctor;
        Acceso.verificar(ctor, ambito, errores, linea, columna);
        verificarArgumentosYRetorno(ctor, tiposArgs, errores);
        return TipoPrimitivo.VOID;
    }

    private List<Tipo> verificarArgumentos(Ambito ambito, ManejadorErrores errores) {
        List<Tipo> tipos = new ArrayList<>();
        for (ExpresionZ a : argumentos) tipos.add(a.verificar(ambito, errores));
        return tipos;
    }

    private static boolean esVirtual(Simbolo m) {
        return m.getCategoria() == CategoriaSimbolo.METODO
                && m.getModificador() != ModificadorAcceso.PRIVATE;
    }

    private Tipo verificarArgumentosYRetorno(Simbolo m, List<Tipo> tiposArgs,
                                             ManejadorErrores errores) {
        List<Simbolo> params = m.getParametros();
        if (params.size() != tiposArgs.size()) {
            errores.reportar(linea, columna,
                    "Método '" + m.getNombre() + "' espera " + params.size()
                            + " argumentos, recibió " + tiposArgs.size());
            return m.getTipo();
        }
        for (int i = 0; i < tiposArgs.size(); i++) {
            if (!Tipos.esAsignable(params.get(i).getTipo(), tiposArgs.get(i))) {
                errores.reportar(argumentos.get(i).getLinea(), argumentos.get(i).getColumna(),
                        "Argumento " + (i + 1) + " incompatible: se esperaba "
                                + params.get(i).getTipo().nombre() + ", se recibió "
                                + tiposArgs.get(i).nombre());
            }
        }
        return m.getTipo();
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        String receptor;
        if (objetivo instanceof Identificador || objetivo instanceof Super) {
            receptor = "this";
        } else if (objetivo instanceof AccesoCampo ac) {
            ResultadoC3D r = ac.getObjeto().generarC3D(generador);
            receptor = r.getLugar();
        } else {
            throw new UnsupportedOperationException(
                    "Llamada con objetivo " + objetivo.getClass().getSimpleName()
                            + ": no soportado en C3D");
        }

        List<Simbolo> paramsFormales =
                (simboloMetodo != null) ? simboloMetodo.getParametros()
                        : java.util.Collections.emptyList();

        List<String> lugaresArgs = new ArrayList<>();
        for (int _i = 0; _i < argumentos.size(); _i++) {
            ResultadoC3D v = argumentos.get(_i).generarC3D(generador);
            String lugar = v.getLugar();

            if (_i < paramsFormales.size()) {
                Tipo tipoFormal = paramsFormales.get(_i).getTipo();
                Tipo tipoReal   = v.getTipo();
                if (tipoFormal == TipoPrimitivo.FLOTANTE
                        && tipoReal != null
                        && tipoReal != TipoPrimitivo.FLOTANTE
                        && tipoReal != TipoPrimitivo.DESCONOCIDO) {
                    String conv = generador.nuevoTemporal();
                    generador.emitirConversion(tipoReal.nombre(), "flotante", lugar, conv);
                    lugar = conv;
                }
            }
            lugaresArgs.add(lugar);
        }

        List<Tipo> tiposFormales = new ArrayList<>();
        if (simboloMetodo != null) {
            for (Simbolo p : simboloMetodo.getParametros()) {
                tiposFormales.add(p.getTipo() != null ? p.getTipo() : TipoPrimitivo.DESCONOCIDO);
            }
        }

        String etiqueta;
        if (objetivo instanceof Super) {
            String padre = claseDe(simboloMetodo, "?");
            etiqueta = GeneradorC3D.etiquetaConstructor(padre, tiposFormales);
            emitirParams(generador, receptor, lugaresArgs);
            generador.emitirCall(etiqueta, argumentos.size() + 1, null);
            return ResultadoC3D.vacio();
        }

        String metodo = (objetivo instanceof Identificador id)
                ? id.getNombre()
                : ((AccesoCampo) objetivo).getCampo();
        String respaldo = (generador.getClaseActual() != null) ? generador.getClaseActual() : "?";
        String clase = claseDe(simboloMetodo, respaldo);
        etiqueta = GeneradorC3D.etiquetaMetodo(clase, metodo, tiposFormales);

        boolean esVoid = (simboloMetodo != null
                && simboloMetodo.getTipo() != null
                && simboloMetodo.getTipo().esVoid());
        Tipo tipo = (simboloMetodo != null && simboloMetodo.getTipo() != null)
                ? simboloMetodo.getTipo()
                : TipoPrimitivo.DESCONOCIDO;
        String resultado = esVoid ? null : generador.nuevoTemporal();

        if (despachoDinamico) {
            List<GeneradorC3D.EntradaDispatch> entradas =
                    generador.dispatchPara(claseReceptor, simboloMetodo);
            if (GeneradorC3D.necesitaDispatch(entradas)) {
                emitirDispatch(generador, receptor, lugaresArgs, entradas, etiqueta, resultado);
                return (resultado == null) ? ResultadoC3D.vacio() : ResultadoC3D.temporal(resultado, tipo);
            }
        }

        emitirParams(generador, receptor, lugaresArgs);
        String tipoRet = (simboloMetodo != null && simboloMetodo.getTipo() != null)
                ? simboloMetodo.getTipo().nombre() : null;
        generador.emitirCall(etiqueta, argumentos.size() + 1, resultado, tipoRet);
        return (resultado == null) ? ResultadoC3D.vacio() : ResultadoC3D.temporal(resultado, tipo);
    }

    private static void emitirParams(GeneradorC3D generador, String receptor, List<String> lugaresArgs) {
        generador.emitirParam(receptor);
        for (String lugar : lugaresArgs) {
            generador.emitirParam(lugar);
        }
    }

    private void emitirDispatch(GeneradorC3D generador, String receptor, List<String> lugaresArgs,
                                List<GeneradorC3D.EntradaDispatch> entradas,
                                String etiquetaPorDefecto, String resultado) {
        int nArgs = argumentos.size() + 1;

        String classId = generador.nuevoTemporal();
        generador.emitirCargaCampo(receptor, GeneradorC3D.CAMPO_CLASS_ID, classId);

        Map<String, String> casoPorFuncion = new LinkedHashMap<>();
        for (GeneradorC3D.EntradaDispatch e : entradas) {
            if (e.etiqueta().equals(etiquetaPorDefecto)) continue;
            String caso = casoPorFuncion.computeIfAbsent(e.etiqueta(), k -> generador.nuevaEtiqueta());

            String cmp = generador.nuevoTemporal();
            generador.emitirBinaria("==", classId, String.valueOf(e.classId()), cmp);
            generador.emitirIfTrue(cmp, caso);
        }

        String fin = generador.nuevaEtiqueta();

        String tipoRet = (simboloMetodo != null && simboloMetodo.getTipo() != null)
                ? simboloMetodo.getTipo().nombre() : null;

        emitirParams(generador, receptor, lugaresArgs);
        generador.emitirCall(etiquetaPorDefecto, nArgs, resultado, tipoRet);
        generador.emitirGoto(fin);

        for (Map.Entry<String, String> caso : casoPorFuncion.entrySet()) {
            generador.emitirEtiqueta(caso.getValue());
            emitirParams(generador, receptor, lugaresArgs);
            generador.emitirCall(caso.getKey(), nArgs, resultado, tipoRet);
            generador.emitirGoto(fin);
        }

        generador.emitirEtiqueta(fin);
    }

    private static String claseDe(Simbolo miembro, String respaldo) {
        if (miembro != null && miembro.getClaseDuena() != null) {
            return miembro.getClaseDuena().getNombre();
        }
        return respaldo;
    }
}