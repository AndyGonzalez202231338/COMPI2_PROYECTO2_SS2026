package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.*;

import java.util.ArrayList;
import java.util.List;

public final class DeclaracionVariable extends NodoPigLatin implements InstruccionPigLatin {

    private final CategoriaDeclaracionVariable categoria;
    private final String nombre;
    private final NodoTipoRef tipo;                       // solo si categoria == CON_TIPO
    private final ExpresionPigLatin inicializador;         // CON_TIPO (opcional) | SOLO_VALOR (obligatorio)
    private final String nombreTipoEstructura;             // solo si categoria == ESTRUCTURA (el ID tras ':')
    private final InicializadorArreglo inicializadorEstructura; // solo si categoria == ESTRUCTURA (obligatorio)

    /**
     * Tipo resuelto por verificar(). Se cachea aquí porque generarC3D() NO puede
     * depender del ámbito del generador: las variables locales del MAIOR se
     * declaran en un AmbitoBloque interno que ya no está activo cuando se
     * traduce a C3D. Sin este cache, generarC3D no sabría que "direccion1" es
     * Direccion (creería que es un arreglo) y emitiría `new int[2]` en vez de
     * `new Direccion`.
     */
    private Tipo tipoCache;

    private DeclaracionVariable(CategoriaDeclaracionVariable categoria, String nombre, NodoTipoRef tipo,
                                ExpresionPigLatin inicializador, String nombreTipoEstructura,
                                InicializadorArreglo inicializadorEstructura, int linea, int columna) {
        super(linea, columna);
        this.categoria = categoria;
        this.nombre = nombre;
        this.tipo = tipo;
        this.inicializador = inicializador;
        this.nombreTipoEstructura = nombreTipoEstructura;
        this.inicializadorEstructura = inicializadorEstructura;
    }

    /** esto ID : tipo (= expresion)? (#declaracionVarConTipo). inicializador puede ser null. */
    public static DeclaracionVariable conTipo(String nombre, NodoTipoRef tipo, ExpresionPigLatin inicializador,
                                              int linea, int columna) {
        return new DeclaracionVariable(CategoriaDeclaracionVariable.CON_TIPO, nombre, tipo, inicializador,
                null, null, linea, columna);
    }

    /** esto ID : ID inicializadorArreglo (#declaracionVarEstructura). */
    public static DeclaracionVariable estructura(String nombre, String nombreTipoEstructura,
                                                 InicializadorArreglo inicializadorEstructura,
                                                 int linea, int columna) {
        return new DeclaracionVariable(CategoriaDeclaracionVariable.ESTRUCTURA, nombre, null, null,
                nombreTipoEstructura, inicializadorEstructura, linea, columna);
    }

    /** esto ID : expresion (#declaracionVarSoloValor). El tipo se infiere del valor. */
    public static DeclaracionVariable soloValor(String nombre, ExpresionPigLatin inicializador,
                                                int linea, int columna) {
        return new DeclaracionVariable(CategoriaDeclaracionVariable.SOLO_VALOR, nombre, null, inicializador,
                null, null, linea, columna);
    }

    public CategoriaDeclaracionVariable getCategoria() { return categoria; }
    public String getNombre() { return nombre; }
    public NodoTipoRef getTipo() { return tipo; }
    public ExpresionPigLatin getInicializador() { return inicializador; }
    public String getNombreTipoEstructura() { return nombreTipoEstructura; }
    public InicializadorArreglo getInicializadorEstructura() { return inicializadorEstructura; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo t;
        ExpresionPigLatin initAUsar = inicializador;

        switch (categoria) {
            case CON_TIPO:
                t = tipo.resolver(ambito, errores);
                this.tipoCache = t;

                // Caso especial: "tipo {v1, v2, ...}" donde "tipo" es estructura/clase.
                // El parser lo clasifica como CON_TIPO (porque "Direccion" es un ID válido
                // como tipoBase, y "{...}" es una expresión válida como inicializador),
                // pero semánticamente es un literal POSICIONAL de estructura, no de arreglo.
                // Validamos campo por campo y anulamos initAUsar para que el chequeo
                // genérico de abajo no intente esAsignable(Direccion, cadena[]).
                if (inicializador instanceof InicializadorArreglo lit
                        && (t instanceof TipoEstructura || t instanceof TipoClase)) {
                    Simbolo defStruct = (t instanceof TipoEstructura te)
                            ? te.getDefinicion()
                            : ((TipoClase) t).getDefinicion();

                    List<Simbolo> campos = new ArrayList<>();
                    for (Simbolo m : defStruct.getMiembrosEnOrden()) {
                        if (m.getCategoria() == CategoriaSimbolo.CAMPO
                                || m.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                            campos.add(m);
                        }
                    }
                    List<ExpresionPigLatin> valores = lit.getElementos();

                    if (campos.size() != valores.size()) {
                        errores.reportar(linea, columna,
                                "Estructura '" + defStruct.getNombre() + "' espera " + campos.size()
                                        + " valores, se recibieron " + valores.size());
                    } else {
                        for (int i = 0; i < valores.size(); i++) {
                            Tipo tVal = valores.get(i).verificar(ambito, errores);
                            Tipo tCampo = campos.get(i).getTipo();
                            if (!Tipos.esAsignable(tCampo, tVal)) {
                                errores.reportar(valores.get(i).getLinea(), valores.get(i).getColumna(),
                                        "Campo " + (i + 1) + " ('" + campos.get(i).getNombre()
                                                + "') espera " + tCampo.nombre()
                                                + ", se recibió " + tVal.nombre());
                            }
                        }
                    }
                    initAUsar = null;
                }
                break;
            case SOLO_VALOR:
                // El tipo se infiere del valor
                t = inicializador.verificar(ambito, errores);
                this.tipoCache = t;
                break;
            case ESTRUCTURA:
                Simbolo s = ambito.resolver(nombreTipoEstructura);
                if (s == null) {
                    errores.reportar(linea, columna,
                            "Tipo importado desconocido: '" + nombreTipoEstructura + "'");
                    t = TipoPrimitivo.DESCONOCIDO;
                    break;
                }
                if (s.getCategoria() == CategoriaSimbolo.CLASE) {
                    t = new TipoClase(s);
                } else if (s.getCategoria() == CategoriaSimbolo.ESTRUCTURA) {
                    t = new TipoEstructura(s);
                } else {
                    errores.reportar(linea, columna, "'" + nombreTipoEstructura + "' no es un tipo");
                    t = TipoPrimitivo.DESCONOCIDO;
                    break;
                }
                this.tipoCache = t;

                // El {...} de una declaración ESTRUCTURA es un literal POSICIONAL de estructura,
                // NO un arreglo: se valida campo por campo contra los campos reales del tipo,
                // en el mismo orden que usaría generarC3D. No se llama a
                // inicializadorEstructura.verificar(...) porque ese método asume arreglo.
                if (inicializadorEstructura != null) {
                    List<Simbolo> campos = new ArrayList<>();
                    for (Simbolo m : s.getMiembrosEnOrden()) {
                        if (m.getCategoria() == CategoriaSimbolo.CAMPO
                                || m.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                            campos.add(m);
                        }
                    }
                    List<ExpresionPigLatin> valores = inicializadorEstructura.getElementos();

                    if (campos.size() != valores.size()) {
                        errores.reportar(linea, columna,
                                "Estructura '" + s.getNombre() + "' espera " + campos.size()
                                        + " valores, se recibieron " + valores.size());
                    } else {
                        for (int i = 0; i < valores.size(); i++) {
                            Tipo tVal = valores.get(i).verificar(ambito, errores);
                            Tipo tCampo = campos.get(i).getTipo();
                            if (!Tipos.esAsignable(tCampo, tVal)) {
                                errores.reportar(valores.get(i).getLinea(), valores.get(i).getColumna(),
                                        "Campo " + (i + 1) + " ('" + campos.get(i).getNombre()
                                                + "') espera " + tCampo.nombre()
                                                + ", se recibió " + tVal.nombre());
                            }
                        }
                    }
                }
                break;
            default:
                t = TipoPrimitivo.DESCONOCIDO;
        }

        Simbolo sim = new Simbolo(nombre, CategoriaSimbolo.VARIABLE, t, linea, columna);
        if (!ambito.declarar(sim)) {
            errores.reportar(linea, columna, "Variable ya declarada: '" + nombre + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }

        if (initAUsar != null && categoria == CategoriaDeclaracionVariable.CON_TIPO) {
            Tipo tInit = initAUsar.verificar(ambito, errores);
            if (!Tipos.esAsignable(t, tInit))
                errores.reportar(linea, columna,
                        "Inicialización incompatible: " + tInit.nombre() + " → " + t.nombre());
        }
        sim.marcarInicializado();
        return TipoPrimitivo.VOID;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        switch (categoria) {
            case CON_TIPO: {
                // ¿Es un literal de estructura "Tipo {v1, v2, ...}"?
                // Se consulta tipoCache (campo del nodo), no el ámbito del generador:
                // las locales del MAIOR viven en un AmbitoBloque que ya no está activo
                // cuando se traduce a C3D.
                if (inicializador instanceof InicializadorArreglo lit
                        && (tipoCache instanceof TipoEstructura || tipoCache instanceof TipoClase)) {

                    Simbolo defStruct = (tipoCache instanceof TipoEstructura te)
                            ? te.getDefinicion()
                            : ((TipoClase) tipoCache).getDefinicion();

                    String t = generador.nuevoTemporal();
                    generador.emitirNew(defStruct.getNombre(), t);

                    List<Simbolo> campos = new ArrayList<>();
                    for (Simbolo m : defStruct.getMiembrosEnOrden()) {
                        if (m.getCategoria() == CategoriaSimbolo.CAMPO
                                || m.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                            campos.add(m);
                        }
                    }
                    List<ExpresionPigLatin> valores = lit.getElementos();
                    for (int i = 0; i < campos.size() && i < valores.size(); i++) {
                        ResultadoC3D v = valores.get(i).generarC3D(generador);
                        generador.emitirGuardarCampo(t, campos.get(i).getNombre(), v.getLugar());
                    }
                    generador.emitirAsignacion(t, nombre);
                    return ResultadoC3D.vacio();
                }

                // Camino normal: variable escalar con inicializador opcional.
                if (inicializador != null) {
                    ResultadoC3D v = inicializador.generarC3D(generador);
                    generador.emitirAsignacion(v.getLugar(), nombre);
                }
                return ResultadoC3D.vacio();
            }
            case SOLO_VALOR: {
                // Inicializador obligatorio por gramática.
                ResultadoC3D v = inicializador.generarC3D(generador);
                generador.emitirAsignacion(v.getLugar(), nombre);
                return ResultadoC3D.vacio();
            }
            case ESTRUCTURA: {
                // t = new Tipo (malloc); luego t.campo_i = v_i en el ORDEN de declaración
                // de los campos (getMiembrosEnOrden(), no getMiembros()); luego nombre = t.
                Simbolo s = (tipoCache instanceof TipoEstructura te)
                        ? te.getDefinicion()
                        : (tipoCache instanceof TipoClase tc)
                        ? tc.getDefinicion()
                        : null;

                String t = generador.nuevoTemporal();
                generador.emitirNew(nombreTipoEstructura, t);

                List<Simbolo> campos = new ArrayList<>();
                if (s != null) {
                    for (Simbolo m : s.getMiembrosEnOrden()) {
                        if (m.getCategoria() == CategoriaSimbolo.CAMPO
                                || m.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                            campos.add(m);
                        }
                    }
                }

                List<ExpresionPigLatin> valores = inicializadorEstructura.getElementos();
                for (int i = 0; i < campos.size() && i < valores.size(); i++) {
                    ResultadoC3D v = valores.get(i).generarC3D(generador);
                    generador.emitirGuardarCampo(t, campos.get(i).getNombre(), v.getLugar());
                }

                generador.emitirAsignacion(t, nombre);
                return ResultadoC3D.vacio();
            }
            default:
                return ResultadoC3D.vacio();
        }
    }
}