package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

/**
 * La regla compartida {@code declaracion} (#declaracionDef): "tipo ID (= expresion)?".
 * Se usa tanto como instrucción suelta (#declarationStatement -> #stmtDeclaracion)
 * como dentro del "init" de un {@code for} (#forInitDeclaracion, ver {@link Para}).
 */
public final class DeclaracionVariable extends NodoZ implements InstruccionZ {

    private final NodoTipoRef tipo;
    private final String nombre;
    private final ExpresionZ inicializador; // null si no hay "= expresion"

    public DeclaracionVariable(NodoTipoRef tipo, String nombre, ExpresionZ inicializador, int linea, int columna) {
        super(linea, columna);
        this.tipo = tipo;
        this.nombre = nombre;
        this.inicializador = inicializador;
    }

    public NodoTipoRef getTipo() { return tipo; }
    public String getNombre()     { return nombre; }
    public ExpresionZ getInicializador() { return inicializador; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo t = tipo.resolver(ambito, errores);
        Simbolo s = new Simbolo(nombre, CategoriaSimbolo.VARIABLE, t, linea, columna);

        if (inicializador instanceof NuevoArregloConTamano nat) {
            boolean todosLiterales = true;
            for (ExpresionZ tam : nat.getTamanos()) {
                if (tam instanceof Literal lit && lit.getCategoria() == CategoriaLiteral.ENTERO) {
                    s.getTamanosArreglo().add(((Long) lit.getValor()).intValue());
                } else {
                    todosLiterales = false;
                    break;
                }
            }
            if (!todosLiterales) s.getTamanosArreglo().clear();  // tamaño dinámico: no verificable
        }

        if (!ambito.declarar(s)) {
            errores.reportar(linea, columna, "Variable ya declarada en este ámbito: '" + nombre + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }
        if (inicializador != null) {
            Tipo tInit = inicializador.verificar(ambito, errores);
            if (!Tipos.esAsignable(t, tInit))
                errores.reportar(linea, columna,
                        "Inicialización incompatible: " + tInit.nombre() + " → " + t.nombre());
            s.marcarInicializado();
        }
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite: NADA si no hay inicializador (la reserva de la variable vive en la
     * tabla de símbolos, que Fase 4 usa para declararla en C). Con inicializador,
     * primero el C3D del inicializador y luego (=, v, -, nombre).
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        if (inicializador != null) {
            ResultadoC3D v = inicializador.generarC3D(generador);
            generador.emitirAsignacion(v.getLugar(), nombre);
        }
        return ResultadoC3D.vacio();
    }
}