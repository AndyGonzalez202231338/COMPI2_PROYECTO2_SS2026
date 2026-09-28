package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.List;

/** (#declVarDef) usada como instrucción (#instDeclaracion). */
public final class DeclaracionVariable extends NodoY implements InstruccionY {

    private final NodoTipoRef tipo;
    private final String nombre;
    private final List<Integer> tamanosArreglo; // vacío si no es arreglo
    private final ExpresionY inicializador;      // null si no hay "= expresion"

    public DeclaracionVariable(NodoTipoRef tipo, String nombre, List<Integer> tamanosArreglo,
                               ExpresionY inicializador, int linea, int columna) {
        super(linea, columna);
        this.tipo = tipo;
        this.nombre = nombre;
        this.tamanosArreglo = tamanosArreglo;
        this.inicializador = inicializador;
    }

    public NodoTipoRef getTipo() { return tipo; }
    public String getNombre() { return nombre; }
    public List<Integer> getTamanosArreglo() { return tamanosArreglo; }
    public ExpresionY getInicializador() { return inicializador; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo t = tipo.resolver(ambito, errores);

        // Si es arreglo, envolver en TipoArreglo (un nivel, según Y?)
        if (!tamanosArreglo.isEmpty()) {
            if (tamanosArreglo.size() > 1)
                errores.reportar(linea, columna, "Y? solo admite arreglos de un nivel");
            t = new TipoArreglo(t, tamanosArreglo.get(0));   // <-- longitud
        }

        Simbolo s = new Simbolo(nombre, CategoriaSimbolo.VARIABLE, t, linea, columna);
        if (!tamanosArreglo.isEmpty()) s.getTamanosArreglo().addAll(tamanosArreglo);

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
     * Tres casos, en este orden:
     *   Variable escalar (tamanosArreglo vacío): igual que antes — sin
     *       inicializador no emite nada; con inicializador emite nombre = v.
     *   Arreglo sin inicializador: no emite NADA.  Emitir cuádruplas aquí sería ruido.
     *   Arreglo con inicializador: ListaLiteral: emite
     *       arr[i] = v_i por cada elemento del literal, USANDO EL NOMBRE DEL
     *       ARREGLO como base (no un temporal intermedio).
     * Un inicializador de arreglo que NO sea ListaLiteral (una variable de
     * arreglo, una llamada que devuelve arreglo, etc.) sigue siendo deuda explícita:
     * C no permite asignar arreglos y Y? tampoco lo contempla hoy.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // ---------- Caso arreglo ----------
        if (!tamanosArreglo.isEmpty()) {
            if (inicializador == null) {
                // Nada que emitir: Fase 4 reserva las celdas leyendo la longitud del
                // TipoArreglo del símbolo.
                return ResultadoC3D.vacio();
            }
            if (inicializador instanceof ListaLiteral lit) {
                // Init elemento a elemento sobre el propio nombre del arreglo: t[i] = v_i.
                List<ExpresionY> elems = lit.getElementos();
                for (int i = 0; i < elems.size(); i++) {
                    ResultadoC3D v = elems.get(i).generarC3D(generador);
                    generador.emitirGuardarIndice(nombre, String.valueOf(i), v.getLugar());
                }
                return ResultadoC3D.vacio();
            }
            throw new UnsupportedOperationException(
                    "Inicialización de arreglo con expresión que no es lista literal: pendiente en C3D");
        }

        // ---------- Caso variable escalar (como antes) ----------
        if (inicializador != null) {
            ResultadoC3D v = inicializador.generarC3D(generador);
            generador.emitirAsignacion(v.getLugar(), nombre);
        }
        return ResultadoC3D.vacio();
    }
}