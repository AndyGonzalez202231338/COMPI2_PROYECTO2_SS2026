package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
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

            StringBuilder claveEspecifica = new StringBuilder(ac.getCampo()).append("#").append(argumentos.size());
            for (Tipo t : tiposArgs) claveEspecifica.append("#").append(t.nombre());
            Simbolo m = tc.getDefinicion().buscarMiembro(claveEspecifica.toString());

            if (m == null) {
                m = tc.getDefinicion().buscarMiembro(ac.getCampo() + "#" + argumentos.size());
            }

            if (m == null || m.getCategoria() != CategoriaSimbolo.METODO) {
                errores.reportar(linea, columna,
                        "La clase '" + tc.nombre() + "' no tiene método '" + ac.getCampo() + "'");
                return TipoPrimitivo.DESCONOCIDO;
            }
            this.simboloResuelto = m;
            this.nombreClaseObjetivo = tc.getDefinicion().getNombre();
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
        // Caso Identificador: sin receptor, nada que evaluar.
        // Evaluar todos los argumentos, guardando sus lugares.
        List<String> lugaresArgs = new ArrayList<>();
        for (ExpresionPigLatin a : argumentos) {
            ResultadoC3D v = a.generarC3D(generador);
            lugaresArgs.add(v.getLugar());
        }

        // Bloque de params: receptor (si aplica) + args.
        if (receptor != null) {
            generador.emitirParam(receptor);
        }
        for (String lugar : lugaresArgs) {
            generador.emitirParam(lugar);
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
            if (esVoid) {
                generador.emitirCall(id.getNombre(), argumentos.size(), null);
                return ResultadoC3D.vacio();
            }
            String t = generador.nuevoTemporal();
            generador.emitirCall(id.getNombre(), argumentos.size(), t);
            return ResultadoC3D.temporal(t, tipoRetorno);
        }

        // Caso "obj.m(args)": receptor ya evaluado, +1 al número de argumentos.
        // Caso "obj.m(args)": receptor ya evaluado, +1 al número de argumentos.
        if (objetivo instanceof AccesoCampo ac) {
            String clase = (nombreClaseObjetivo != null) ? nombreClaseObjetivo : "?";
            // Tipos FORMALES del símbolo resuelto (para mangling coherente con Z).
            List<com.proyecto1.semantico.tipos.Tipo> tiposFormales = new java.util.ArrayList<>();
            if (simboloResuelto != null) {
                for (com.proyecto1.semantico.tabla.Simbolo p : simboloResuelto.getParametros()) {
                    tiposFormales.add(p.getTipo() != null
                            ? p.getTipo()
                            : com.proyecto1.semantico.tipos.TipoPrimitivo.DESCONOCIDO);
                }
            }
            String etiqueta = GeneradorC3D.etiquetaMetodo(clase, ac.getCampo(), tiposFormales);
            if (esVoid) {
                generador.emitirCall(etiqueta, argumentos.size() + 1, null);
                return ResultadoC3D.vacio();
            }
            String t = generador.nuevoTemporal();
            generador.emitirCall(etiqueta, argumentos.size() + 1, t);
            return ResultadoC3D.temporal(t, tipoRetorno);
        }

        throw new UnsupportedOperationException(
                "Llamada con objetivo " + objetivo.getClass().getSimpleName() + ": no soportado en C3D");
    }
}