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
    private final List<Integer> tamanosArreglo;
    private final ExpresionY inicializador;
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

        if (!tamanosArreglo.isEmpty()) {
            for (int i = tamanosArreglo.size() - 1; i >= 0; i--) {
                t = new TipoArreglo(t, tamanosArreglo.get(i));
            }
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
                validarCamposEstructura(te, lista.getElementos(), ambito, errores, linea, columna);
            } else {
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

    private void validarCamposEstructura(TipoEstructura te, List<ExpresionY> valores, Ambito ambito, ManejadorErrores errores, int linea, int columna) {
        List<Simbolo> campos = te.getDefinicion().getMiembrosEnOrden();

        if (campos.size() != valores.size()) {
            errores.reportar(linea, columna,
                    "La estructura '" + te.nombre() + "' espera " + campos.size() +
                            " valores, se recibieron " + valores.size());
            return;
        }

        for (int i = 0; i < valores.size(); i++) {
            Simbolo campoActual = campos.get(i);
            ExpresionY valorActual = valores.get(i);
            Tipo tCampo = campoActual.getTipo();

            if (tCampo instanceof TipoEstructura subEstructura && valorActual instanceof ListaLiteral subLista) {
                validarCamposEstructura(subEstructura, subLista.getElementos(), ambito, errores,
                        valorActual.getLinea(), valorActual.getColumna());
            } else {
                Tipo tVal = valorActual.verificar(ambito, errores);
                if (!Tipos.esAsignable(tCampo, tVal)) {
                    errores.reportar(valorActual.getLinea(), valorActual.getColumna(),
                            "Campo " + (i + 1) + " ('" + campoActual.getNombre() +
                                    "') espera " + tCampo.nombre() + ", se recibió " + tVal.nombre());
                }
            }
        }
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        if (!tamanosArreglo.isEmpty()) {
            String dest = nombre;
            Ambito amb = generador.getAmbito();
            if (amb != null) {
                Simbolo sv = amb.resolver(nombre);
                if (sv != null) dest = sv.getNombreC3D();
            }

            int totalElementos = 1;
            for (int tam : tamanosArreglo) {
                totalElementos *= tam;
            }

            String descriptorTipo = "entero";
            if (tipoCacheado instanceof TipoArreglo ta) {
                descriptorTipo = ta.baseEscalar().nombre();
            } else if (tipoCacheado != null) {
                descriptorTipo = tipoCacheado.nombre();
            }
            generador.emitirNewArray(descriptorTipo, String.valueOf(totalElementos), dest);

            if (inicializador instanceof ListaLiteral lit) {
                List<ExpresionY> elems = lit.getElementos();
                for (int i = 0; i < elems.size(); i++) {
                    ResultadoC3D v = elems.get(i).generarC3D(generador);
                    generador.emitirGuardarIndice(dest, String.valueOf(i), v.getLugar());
                }
            }
            return ResultadoC3D.vacio();
        }

        String dest = nombre;
        Ambito amb = generador.getAmbito();
        if (amb != null) {
            Simbolo sv = amb.resolver(nombre);
            if (sv != null) dest = sv.getNombreC3D();
        }

        if (tipoCacheado instanceof TipoEstructura te) {
            generador.emitirNew(te.nombre(), dest);

            if (inicializador == null) {
                for (Simbolo campo : te.getDefinicion().getMiembrosEnOrden()) {
                    if (campo.getTipo() instanceof TipoEstructura subTe) {
                        String tSub = generador.nuevoTemporal();
                        generador.emitirNew(subTe.nombre(), tSub);
                        generador.emitirGuardarCampo(dest, campo.getNombre(), tSub);
                    }
                }
            } else if (inicializador instanceof ListaLiteral lista) {
                generarC3DCamposEstructura(dest, te, lista.getElementos(), generador);
            } else {
                ResultadoC3D rhs = inicializador.generarC3D(generador);
                for (Simbolo campo : te.getDefinicion().getMiembrosEnOrden()) {
                    String tCampo = generador.nuevoTemporal();
                    generador.emitirCargaCampo(rhs.getLugar(), campo.getNombre(), tCampo);
                    generador.emitirGuardarCampo(dest, campo.getNombre(), tCampo);
                }
            }
            return ResultadoC3D.vacio();
        }

        if (inicializador != null) {
            ResultadoC3D v = inicializador.generarC3D(generador);
            generador.emitirAsignacion(v.getLugar(), dest);
        }
        return ResultadoC3D.vacio();
    }

    private void generarC3DCamposEstructura(String tObjeto, TipoEstructura te, List<ExpresionY> valores, GeneradorC3D generador) {
        List<Simbolo> campos = te.getDefinicion().getMiembrosEnOrden();

        for (int i = 0; i < valores.size() && i < campos.size(); i++) {
            Simbolo campoActual = campos.get(i);
            ExpresionY valorActual = valores.get(i);

            if (campoActual.getTipo() instanceof TipoEstructura subEstructura && valorActual instanceof ListaLiteral subLista) {
                String tSub = generador.nuevoTemporal();
                generador.emitirNew(subEstructura.nombre(), tSub);
                generarC3DCamposEstructura(tSub, subEstructura, subLista.getElementos(), generador);
                generador.emitirGuardarCampo(tObjeto, campoActual.getNombre(), tSub);
            } else {
                ResultadoC3D v = valorActual.generarC3D(generador);
                generador.emitirGuardarCampo(tObjeto, campoActual.getNombre(), v.getLugar());
            }
        }
    }
}