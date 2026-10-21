package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.List;

/** primaria LPAREN argumentos? RPAREN (#primariaLlamada): llamada a función. */
public final class Llamada extends NodoY implements ExpresionY {

    private final ExpresionY objetivo; // normalmente un Identificador con el nombre de la función
    private final List<ExpresionY> argumentos;

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
        if (!(objetivo instanceof Identificador id)) {
            errores.reportar(linea, columna, "Llamada inválida");
            return TipoPrimitivo.DESCONOCIDO;
        }
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
            return f.getTipo(); // sigue devolviendo el tipo de retorno
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

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        if (!(objetivo instanceof Identificador id)) {
            // verificar() ya reportó "Llamada inválida"; aquí solo se protege de invocarlo
            // sin análisis previo.
            throw new UnsupportedOperationException(
                    "Llamada a " + objetivo.getClass().getSimpleName() + ": pendiente en C3D");
        }

        //    PRIMERO se evaluan TODOS los argumentos, en orden (el orden importa:
        //    en C3D los efectos laterales -- llamadas anidadas, leer(), x++ -- deben
        //    ejecutarse en el orden del codigo fuente), SIN emitir "param" todavia.
        //    Si se emitiera "param" justo tras evaluar cada argumento, un argumento
        //    que es EL MISMO una llamada (ej. "sumar(sumar(1,2), sumar(3,4))") haria
        //    que el "param" de la llamada EXTERNA quedara pendiente justo antes de
        //    evaluar el SIGUIENTE argumento, y los "param" de esa llamada INTERNA se
        //    mezclarian con el pendiente, haciendo que el backend agrupe argumentos
        //    de DOS llamadas distintas como si fueran de una sola.
        java.util.List<String> lugaresArgs = new java.util.ArrayList<>();
        for (ExpresionY a : argumentos) {
            ResultadoC3D va = a.generarC3D(generador);
            lugaresArgs.add(va.getLugar());
        }

        //    SOLO AHORA, con todos los argumentos ya evaluados (y cualquier llamada
        //    anidada ya completamente resuelta), se emiten los "param" de ESTA
        //    llamada, seguidos de su propio "call".
        for (String lugar : lugaresArgs) {
            generador.emitirParam(lugar);
        }

        //    La llamada en sí. El nombre real de la función lo lleva el identificador;
        //    nArgs se guarda por validación cruzada con los param emitidos arriba.
        String t = generador.nuevoTemporal();
        generador.emitirCall(id.getNombre(), argumentos.size(), t);

        //    Tipo de retorno: se resuelve del ámbito del generador (puede ser null si
        //    el generador se creó sin ámbito, en cuyo caso se cae a DESCONOCIDO).
        Tipo tipoRetorno = TipoPrimitivo.DESCONOCIDO;
        Ambito amb = generador.getAmbito();
        if (amb != null) {
            Simbolo f = amb.resolver(id.getNombre());
            if (f != null && f.getTipo() != null) {
                tipoRetorno = f.getTipo();
            }
        }

        //    La llamada SÍ produce un valor (el retorno). El padre decide qué hacer con él:
        //    guardarlo en una variable, imprimirlo, usarlo en una binaria, o descartarlo
        //    (ExpresionStmt ya cubre ese último caso ignorando el ResultadoC3D).
        return ResultadoC3D.temporal(t, tipoRetorno);
    }
}
