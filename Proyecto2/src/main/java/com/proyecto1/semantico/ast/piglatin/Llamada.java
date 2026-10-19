package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.*;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.List;

/**
 * primaria LPAREN argumentos? RPAREN (#primariaLlamada): llamada a función
 * o método. Cubre dos casos:
 */
public final class Llamada extends NodoPigLatin implements ExpresionPigLatin {

    private final ExpresionPigLatin objetivo;
    private final List<ExpresionPigLatin> argumentos;

    /**
     * Símbolo de la función/método resuelto por verificar(). Se usa en generarC3D
     * para el tipo de retorno, sin volver a navegar el ámbito. Null si verificar()
     * no corrió o falló.
     */
    private Simbolo simboloResuelto;
    // Clase del tipo declarado del receptor (para dispatch dinamico).
    private Simbolo claseReceptor;
    // true si el metodo puede estar sobreescrito por alguna subclase.
    private boolean despachoDinamico;

    /**
     * Clase del objeto en el caso "obj.m(args)". Null en el caso "f(args)" (o si
     * verificar() no corrió). Cacheado en verificar() a partir de TipoClase.
     */
    private String nombreClaseObjetivo;

    public Llamada(ExpresionPigLatin objetivo, List<ExpresionPigLatin> argumentos, int linea, int columna) {
        super(linea, columna);
        this.objetivo = objetivo;
        this.argumentos = argumentos;
    }

    public ExpresionPigLatin getObjetivo() { return objetivo; }
    public List<ExpresionPigLatin> getArgumentos() { return argumentos; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // Caso 1: función suelta "f(args)".
        if (objetivo instanceof Identificador id) {
            Simbolo f = ambito.resolver(id.getNombre());
            if (f == null || (f.getCategoria() != CategoriaSimbolo.FUNCION
                    && f.getCategoria() != CategoriaSimbolo.METODO)) {
                errores.reportar(linea, columna, "Función no declarada: '" + id.getNombre() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloResuelto = f;
            return verificarArgumentosYRetorno(f, ambito, errores);
        }

        // Caso 2: método sobre objeto "obj.m(args)".
        if (objetivo instanceof AccesoCampo ac) {
            Tipo tObj = ac.getObjeto().verificar(ambito, errores);
            if (!(tObj instanceof TipoClase tc)) {
                if (!tObj.esDesconocido())
                    errores.reportar(linea, columna,
                            "No se puede llamar método sobre " + tObj.nombre());
                return TipoPrimitivo.DESCONOCIDO;
            }

            // Los métodos de una CLASE ya no se guardan bajo su nombre simple (ver
            // AnalizadorSemanticoZ.registrarMiembros y AmbitoContenedor.declararMiembroConClave):
            // se sobrecargan por firma, con clave "nombre#aridad#Tipo1#Tipo2..." y una clave
            // genérica "nombre#aridad" como respaldo. Este es el mismo esquema (y el mismo orden
            // de intentos) que ya usa Llamada(Z) para resolver "obj.metodo(args)" -- antes de este
            // cambio, este método seguía buscando por nombre simple, que ya no existe para nada
            // que no sea un ATRIBUTO (esos sí conservan su clave simple).
            List<Tipo> tiposArgs = new ArrayList<>();
            for (ExpresionPigLatin a : argumentos) tiposArgs.add(a.verificar(ambito, errores));

            // Fase 2: misma resolucion que Llamada(Z), con herencia y subtipos.
            Simbolo m = ResolucionMiembros.resolverMetodo(tc.getDefinicion(), ac.getCampo(), tiposArgs);

            if (m == null || m.getCategoria() != CategoriaSimbolo.METODO) {
                errores.reportar(linea, columna,
                        "La clase '" + tc.nombre() + "' no tiene método '" + ac.getCampo() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloResuelto = m;
            this.claseReceptor = tc.getDefinicion();
            this.despachoDinamico = m.getCategoria() == CategoriaSimbolo.METODO && m.getModificador() != ModificadorAcceso.PRIVATE;
            // La etiqueta C3D es la de la clase que declara el metodo: un metodo heredado
            // vive en la clase padre (Persona_saludar aunque el objeto sea un Profesor).
            this.nombreClaseObjetivo = (m.getClaseDuena() != null)
                    ? m.getClaseDuena().getNombre()
                    : tc.getDefinicion().getNombre();
            // Desde PigLatin no hay clase actual: solo se pueden llamar metodos public o default.
            Acceso.verificar(m, ambito, errores, linea, columna);
            // Los argumentos YA se verificaron arriba (se necesitaban sus tipos para la clave);
            // no repetirlos -- misma razón por la que Llamada(Z) tiene su propio
            // verificarArgumentosYRetorno(Simbolo, List<Tipo>, ManejadorErrores) en vez de
            // volver a llamar a.verificar(...) por cada argumento.
            return verificarArgumentosYRetorno(m, tiposArgs, errores);
        }

        errores.reportar(linea, columna, "Llamada inválida");
        return TipoPrimitivo.DESCONOCIDO;
    }

    private Tipo verificarArgumentosYRetorno(Simbolo f, Ambito ambito, ManejadorErrores errores) {
        List<Tipo> tiposArgs = new ArrayList<>();
        for (ExpresionPigLatin a : argumentos) tiposArgs.add(a.verificar(ambito, errores));
        return verificarArgumentosYRetorno(f, tiposArgs, errores);
    }

    /** Igual que la de arriba pero recibe los tipos YA verificados (caso "obj.m(args)": ya se necesitaban antes, para la clave de sobrecarga). */
    private Tipo verificarArgumentosYRetorno(Simbolo f, List<Tipo> tiposArgs, ManejadorErrores errores) {
        List<Simbolo> params = f.getParametros();
        if (params.size() != tiposArgs.size()) {
            errores.reportar(linea, columna,
                    "Función '" + f.getNombre() + "' espera " + params.size() +
                            " argumentos, recibió " + tiposArgs.size());
            return f.getTipo();
        }
        for (int i = 0; i < tiposArgs.size(); i++) {
            if (!Tipos.esAsignable(params.get(i).getTipo(), tiposArgs.get(i)))
                errores.reportar(argumentos.get(i).getLinea(), argumentos.get(i).getColumna(),
                        "Argumento " + (i+1) + " incompatible: se esperaba " +
                                params.get(i).getTipo().nombre() + ", se recibió " + tiposArgs.get(i).nombre());
        }
        return f.getTipo();
    }

    /**
     * Emite, en este orden:
     *   Determinar el receptor:

     *         Identificador: SIN receptor. No se emite ningún param
     *             antes de los argumentos.
     *         AccesoCampo: la expresión del objeto es el receptor; se
     *             genera su C3D y su lugar pasa a ser el primer param.
     * ResultadoC3D.temporal(t, tipoDelSímboloCacheado) (o DESCONOCIDO
     * si el símbolo no se pudo cachear).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // Receptor: solo si es "obj.m(args)".
        String receptor = null;
        if (objetivo instanceof AccesoCampo ac) {
            ResultadoC3D r = ac.getObjeto().generarC3D(generador);
            receptor = r.getLugar();
        }
        // Caso Identificador: sin receptor, nada que evaluar. Evaluar todos los argumentos, guardando sus lugares.
        List<String> lugaresArgs = new ArrayList<>();
        for (ExpresionPigLatin a : argumentos) {
            ResultadoC3D v = a.generarC3D(generador);
            lugaresArgs.add(v.getLugar());
        }

        // ¿El método devuelve void? Si es así, el call va SIN destino y
        //    devolvemos vacio() (nadie consume el resultado: verificar() ya
        //    rechazó que un void se use en contexto de expresión).
        Tipo tipoRetorno = (simboloResuelto != null && simboloResuelto.getTipo() != null)
                ? simboloResuelto.getTipo()
                : TipoPrimitivo.DESCONOCIDO;
        boolean esVoid = tipoRetorno.esVoid();

        // Emitir la llamada con la etiqueta correcta según el caso.
        if (objetivo instanceof Identificador id) {
            for (String lugar : lugaresArgs) generador.emitirParam(lugar);
            if (esVoid) {
                generador.emitirCall(id.getNombre(), argumentos.size(), null);
                return ResultadoC3D.vacio();
            }
            String t = generador.nuevoTemporal();
            generador.emitirCall(id.getNombre(), argumentos.size(), t);
            return ResultadoC3D.temporal(t, tipoRetorno);
        }

        // Caso "obj.m(args)": receptor ya evaluado, +1 al número de argumentos.
        // Caso "obj.m(args)": receptor ya evaluado, +1 al numero de argumentos.
        if (objetivo instanceof AccesoCampo ac) {
            String clase = (nombreClaseObjetivo != null) ? nombreClaseObjetivo : "?";
            List<com.proyecto1.semantico.tipos.Tipo> tiposFormales = new java.util.ArrayList<>();
            if (simboloResuelto != null) {
                for (com.proyecto1.semantico.tabla.Simbolo p : simboloResuelto.getParametros()) {
                    tiposFormales.add(p.getTipo() != null
                            ? p.getTipo()
                            : com.proyecto1.semantico.tipos.TipoPrimitivo.DESCONOCIDO);
                }
            }
            String etiqueta = GeneradorC3D.etiquetaMetodo(clase, ac.getCampo(), tiposFormales);
            String resultado = esVoid ? null : generador.nuevoTemporal();

            // Dispatch dinamico si alguna subclase sobreescribe el metodo.
            if (despachoDinamico && claseReceptor != null && simboloResuelto != null) {
                java.util.List<GeneradorC3D.EntradaDispatch> entradas =
                        generador.dispatchPara(claseReceptor, simboloResuelto);
                if (GeneradorC3D.necesitaDispatch(entradas)) {
                    emitirDispatch(generador, receptor, lugaresArgs, entradas,
                            etiqueta, resultado, argumentos.size() + 1);
                    return (resultado == null)
                            ? ResultadoC3D.vacio()
                            : ResultadoC3D.temporal(resultado, tipoRetorno);
                }
            }

            // Call directo: nadie sobreescribe o no es virtual.
            generador.emitirParam(receptor);
            for (String lugar : lugaresArgs) generador.emitirParam(lugar);
            generador.emitirCall(etiqueta, argumentos.size() + 1, resultado);
            return (resultado == null)
                    ? ResultadoC3D.vacio()
                    : ResultadoC3D.temporal(resultado, tipoRetorno);
        }

        throw new UnsupportedOperationException(
                "Llamada con objetivo " + objetivo.getClass().getSimpleName() + ": no soportado en C3D");
    }

    // Dispatch dinamico con if-chain (misma logica que Llamada.java de Z).
    private void emitirDispatch(GeneradorC3D generador, String receptor,
                                List<String> lugaresArgs,
                                java.util.List<GeneradorC3D.EntradaDispatch> entradas,
                                String etiquetaPorDefecto, String resultado, int nArgs) {
        String classId = generador.nuevoTemporal();
        generador.emitirCargaCampo(receptor, GeneradorC3D.CAMPO_CLASS_ID, classId);

        java.util.Map<String, String> casoPorFuncion = new java.util.LinkedHashMap<>();
        for (GeneradorC3D.EntradaDispatch e : entradas) {
            if (e.etiqueta().equals(etiquetaPorDefecto)) continue;
            String caso = casoPorFuncion.computeIfAbsent(e.etiqueta(), k -> generador.nuevaEtiqueta());
            String cmp = generador.nuevoTemporal();
            generador.emitirBinaria("==", classId, String.valueOf(e.classId()), cmp);
            generador.emitirIfTrue(cmp, caso);
        }

        String fin = generador.nuevaEtiqueta();

        // Caso por defecto
        generador.emitirParam(receptor);
        for (String lugar : lugaresArgs) generador.emitirParam(lugar);
        generador.emitirCall(etiquetaPorDefecto, nArgs, resultado);
        generador.emitirGoto(fin);

        // Casos alternativos
        for (java.util.Map.Entry<String, String> caso : casoPorFuncion.entrySet()) {
            generador.emitirEtiqueta(caso.getValue());
            generador.emitirParam(receptor);
            for (String lugar : lugaresArgs) generador.emitirParam(lugar);
            generador.emitirCall(caso.getKey(), nArgs, resultado);
            generador.emitirGoto(fin);
        }

        generador.emitirEtiqueta(fin);
    }
}