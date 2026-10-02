package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoEstructura;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.ArrayList;
import java.util.List;

/**
 * primaria NUEVO ID LPAREN argumentos? RPAREN (#primariaInstancia): creación
 * de un objeto nuevo. El ID puede referirse a una CLASE (importada de .z,
 * tiene constructor) o a una ESTRUCTURA (importada de .y, sin constructor:
 * los argumentos se asignan posicionalmente a los campos en orden de declaración).
 */
public final class NuevoObjeto extends NodoPigLatin implements ExpresionPigLatin {

    private final String nombreTipo;
    private final List<ExpresionPigLatin> argumentos;

    /**
     * Constructor de la clase resuelto en verificar(), cacheado para que
     * generarC3D pueda obtener los tipos FORMALES y construir la etiqueta
     * manglada coherente con la que registró Constructor.generarC3D.
     * Es null si verificar() no corrió o no encontró el constructor.
     */
    private Simbolo constructorResuelto;

    public NuevoObjeto(String nombreTipo, List<ExpresionPigLatin> argumentos, int linea, int columna) {
        super(linea, columna);
        this.nombreTipo = nombreTipo;
        this.argumentos = argumentos;
    }

    public String getNombreTipo() { return nombreTipo; }
    public List<ExpresionPigLatin> getArgumentos() { return argumentos; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Simbolo s = ambito.ambitoGlobal().resolverLocal(nombreTipo);
        if (s == null) {
            errores.reportar(linea, columna, "Tipo desconocido: '" + nombreTipo + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }

        if (s.getCategoria() == CategoriaSimbolo.CLASE) {
            // Verificación de aridad contra el constructor (mismo patrón que Z).
            // Se cachea el símbolo del constructor para usar sus tipos formales en generarC3D.
            Simbolo ctor = s.getMiembros().valores().stream()
                    .filter(m -> m.getCategoria() == CategoriaSimbolo.CONSTRUCTOR
                            && m.getParametros().size() == argumentos.size())
                    .findFirst().orElse(null);
            if (ctor == null)
                errores.reportar(linea, columna,
                        "No existe constructor de '" + nombreTipo + "' con " +
                                argumentos.size() + " argumentos");
            else
                this.constructorResuelto = ctor;
            for (ExpresionPigLatin a : argumentos) a.verificar(ambito, errores);
            return new TipoClase(s);
        }

        if (s.getCategoria() == CategoriaSimbolo.ESTRUCTURA) {
            // Verificación de aridad contra los campos declarados.
            long campos = s.getMiembrosEnOrden().stream()
                    .filter(m -> m.getCategoria() == CategoriaSimbolo.CAMPO)
                    .count();
            if (campos != argumentos.size())
                errores.reportar(linea, columna,
                        "La estructura '" + nombreTipo + "' tiene " + campos +
                                " campos, se dieron " + argumentos.size() + " valores");
            for (ExpresionPigLatin a : argumentos) a.verificar(ambito, errores);
            return new TipoEstructura(s);
        }

        errores.reportar(linea, columna, "'" + nombreTipo + "' no es un tipo instanciable");
        return TipoPrimitivo.DESCONOCIDO;
    }

    /**
     * Emite, en este orden:
     * (new, nombreTipo, null, t): reserva la celda en heap; t
     *       es la referencia al objeto recién creado.
     *   Según la categoría del símbolo resuelto:
     *         CLASE: como NuevoObjeto(Z). Evalúa los argumentos en
     *             orden, emite param(t) + un param por argumento, y
     *             (call, etiquetaConstructor(nombreTipo, nArgs), nArgs+1, null).
     *             El +1 es el this del constructor.
     *         ESTRUCTURA: asignación posicional de campos, mismo patrón que
     *             el caso ESTRUCTURA. Filtra s.getMiembrosEnOrden() quedándose solo con los de categoría
     *             CAMPO, y empareja por posición con los argumentos: por cada
     *             i, genera el C3D del argumento y emite (.=, t, campo_i, v_i)}.
     *         <li>Otra categoría o símbolo null: solo se emite el {@code new}, sin

     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        Ambito amb = generador.getAmbito();
        Simbolo s = (amb != null) ? amb.ambitoGlobal().resolverLocal(nombreTipo) : null;

        String t = generador.nuevoTemporal();
        generador.emitirNew(nombreTipo, t);

        if (s != null && s.getCategoria() == CategoriaSimbolo.CLASE) {
            // --- Caso CLASE: constructor con `this` + args ---
            List<String> lugaresArgs = new ArrayList<>();
            for (ExpresionPigLatin a : argumentos) {
                ResultadoC3D v = a.generarC3D(generador);
                lugaresArgs.add(v.getLugar());
            }
            generador.emitirParam(t);
            for (String lugar : lugaresArgs) {
                generador.emitirParam(lugar);
            }

            // Tipos FORMALES del constructor resuelto (para mangling coherente).
            List<Tipo> tiposFormales = new java.util.ArrayList<>();
            if (constructorResuelto != null) {
                for (Simbolo p : constructorResuelto.getParametros()) {
                    tiposFormales.add(p.getTipo() != null
                            ? p.getTipo()
                            : TipoPrimitivo.DESCONOCIDO);
                }
            }
            String etiqueta = GeneradorC3D.etiquetaConstructor(nombreTipo, tiposFormales);
            generador.emitirCall(etiqueta, argumentos.size() + 1, null);

            return ResultadoC3D.temporal(t, new TipoClase(s));
        }

        if (s != null && s.getCategoria() == CategoriaSimbolo.ESTRUCTURA) {
            // --- Caso ESTRUCTURA: asignación posicional de campos ---
            List<Simbolo> campos = new ArrayList<>();
            for (Simbolo m : s.getMiembrosEnOrden()) {
                if (m.getCategoria() == CategoriaSimbolo.CAMPO) {
                    campos.add(m);
                }
            }
            for (int i = 0; i < campos.size() && i < argumentos.size(); i++) {
                ResultadoC3D v = argumentos.get(i).generarC3D(generador);
                generador.emitirGuardarCampo(t, campos.get(i).getNombre(), v.getLugar());
            }
            return ResultadoC3D.temporal(t, new TipoEstructura(s));
        }

        // Símbolo no resuelto o categoría inesperada: solo el `new`, sin init.
        return ResultadoC3D.temporal(t, TipoPrimitivo.DESCONOCIDO);
    }
}