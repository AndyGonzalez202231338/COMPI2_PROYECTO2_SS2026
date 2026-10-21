package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoArreglo;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.List;

/** (#declVarDef) usada como instrucción (#instDeclaracion). */
public final class DeclaracionVariable extends NodoY implements InstruccionY {

    private final NodoTipoRef tipo;
    private final String nombre;
    private final List<Integer> tamanosArreglo; // vacío si no es arreglo
    private final ExpresionY inicializador;      // null si no hay "= expresion"
    private Tipo tipoCacheado;

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
        this.tipoCacheado = t;

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
            if (inicializador instanceof Leer lectura) lectura.setTipoEsperado(t);

            if (t instanceof TipoEstructura te && inicializador instanceof ListaLiteral lista) {
                // CAMBIO CLAVE AQUI: Usar getMiembrosEnOrden() en lugar de getMiembros().valores()
                List<Simbolo> campos = te.getDefinicion().getMiembrosEnOrden();
                List<ExpresionY> valores = lista.getElementos();

                if (campos.size() != valores.size()) {
                    errores.reportar(linea, columna,
                            "La estructura '" + te.nombre() + "' espera " + campos.size() +
                                    " valores, se recibieron " + valores.size());
                } else {
                    for (int i = 0; i < valores.size(); i++) {
                        Tipo tVal = valores.get(i).verificar(ambito, errores);
                        Tipo tCampo = campos.get(i).getTipo();
                        if (!Tipos.esAsignable(tCampo, tVal)) {
                            errores.reportar(valores.get(i).getLinea(), valores.get(i).getColumna(),
                                    "Campo " + (i + 1) + " ('" + campos.get(i).getNombre() +
                                            "') espera " + tCampo.nombre() + ", se recibió " + tVal.nombre());
                        }
                    }
                }
            }
            // CASO NORMAL: primitivos, arreglos, variables, etc.
            else {
                Tipo tInit = inicializador.verificar(ambito, errores);
                if (!Tipos.esAsignable(t, tInit))
                    errores.reportar(linea, columna,
                            "Inicialización incompatible: " + tInit.nombre() + " → " + t.nombre());
            }
            s.marcarInicializado();
        }

        if(!tamanosArreglo.isEmpty() && inicializador == null){
            s.marcarInicializado();
        }
        return TipoPrimitivo.VOID;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // ---------- Caso arreglo ----------
        if (!tamanosArreglo.isEmpty()) {
            String dest = nombre;
            Ambito amb = generador.getAmbito();
            if (amb != null) {
                Simbolo sv = amb.resolver(nombre);
                if (sv != null) dest = sv.getNombreC3D();
            }

            int tamano = tamanosArreglo.get(0);
            String descriptorTipo = "entero"; // fallback
            if (tipoCacheado instanceof TipoArreglo ta) {
                descriptorTipo = ta.getBase().nombre();
            } else if (tipoCacheado != null) {
                descriptorTipo = tipoCacheado.nombre();
            }
            generador.emitirNewArray(descriptorTipo, String.valueOf(tamano), dest);

            if (inicializador instanceof ListaLiteral lit) {
                List<ExpresionY> elems = lit.getElementos();
                for (int i = 0; i < elems.size(); i++) {
                    ResultadoC3D v = elems.get(i).generarC3D(generador);
                    generador.emitirGuardarIndice(dest, String.valueOf(i), v.getLugar());
                }
            }
            return ResultadoC3D.vacio();
        }

        // ---------- Caso variable escalar ----------
        String dest = nombre;
        Ambito amb = generador.getAmbito();
        if (amb != null) {
            Simbolo sv = amb.resolver(nombre);
            if (sv != null) dest = sv.getNombreC3D();
        }

        // --- CORRECCIÓN: Si es estructura, reservar en el HEAP ---
        if (tipoCacheado instanceof TipoEstructura te) {
            // 1. Reservar memoria para la estructura en el heap (malloc)
            generador.emitirNew(te.nombre(), dest);

            // 2. Si hay inicializador, asignar campo por campo usando emitirGuardarCampo
            if (inicializador instanceof ListaLiteral lista) {
                List<Simbolo> campos = te.getDefinicion().getMiembrosEnOrden();
                List<ExpresionY> valores = lista.getElementos();

                for (int i = 0; i < valores.size() && i < campos.size(); i++) {
                    ResultadoC3D v = valores.get(i).generarC3D(generador);
                    // Usar emitirGuardarCampo para que genere la instrucción correcta
                    generador.emitirGuardarCampo(dest, campos.get(i).getNombre(), v.getLugar());
                }
            }
            return ResultadoC3D.vacio();
        }

        // --- CASO NORMAL: primitivos ---
        if (inicializador != null) {
            ResultadoC3D v = inicializador.generarC3D(generador);
            generador.emitirAsignacion(v.getLugar(), dest);
        }
        return ResultadoC3D.vacio();
    }
}