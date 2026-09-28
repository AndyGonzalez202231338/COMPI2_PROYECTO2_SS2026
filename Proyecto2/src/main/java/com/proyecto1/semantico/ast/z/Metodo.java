package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoClase;
import com.proyecto1.semantico.tabla.AmbitoFuncion;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.ArrayList;
import java.util.List;

/**
 * "public (tipo|void) Nombre(params) bloque".
 * A diferencia de Funcion de Y? (donde omitir "-> tipo" significa void),
 * aquí SIEMPRE hay una de las dos alternativas presente en la gramática (tipo o la
 * palabra reservada VOID); tipoRetorno == null representa justamente el caso
 * "era VOID", para que #esVoid() funcione igual que en Y?.
 */
public final class Metodo extends NodoZ {

    private final String nombre;
    private final List<Parametro> parametros;
    private final NodoTipoRef tipoRetorno; // null == era "void"
    private final Bloque cuerpo;

    /**
     * Ámbito de la función, creado por verificar() y reutilizado por generarC3D().
     * Mismo patrón que {@code AccesoCampo} usa para cachear tipoCampo: se
     * evita reconstruir parámetros y volver a tocar ManejadorErrores en la
     * fase de generación. Es null si verificar() aún no corrió.
     */
    private AmbitoFuncion ambitoPropio;

    public Metodo(String nombre, List<Parametro> parametros, NodoTipoRef tipoRetorno,
                  Bloque cuerpo, int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
        this.parametros = parametros;
        this.tipoRetorno = tipoRetorno;
        this.cuerpo = cuerpo;
    }

    public String getNombre() {
        return nombre;
    }

    public List<Parametro> getParametros() {
        return parametros;
    }

    public NodoTipoRef getTipoRetorno() {
        return tipoRetorno;
    }

    public boolean esVoid() {
        return tipoRetorno == null;
    }

    public Bloque getCuerpo() {
        return cuerpo;
    }

    public AmbitoFuncion getAmbitoPropio() {
        return ambitoPropio;
    }

    public void verificar(AmbitoClase ambClase, ManejadorErrores errores) {
        StringBuilder sb = new StringBuilder(nombre).append("#").append(parametros.size());
        for (Parametro p : parametros) {
            Tipo tp = p.getTipo().resolver(ambClase, errores);
            sb.append("#").append(tp.nombre());
        }
        Simbolo simbolo = ambClase.getSimboloContenedor().buscarMiembro(sb.toString());

        AmbitoFuncion amb = new AmbitoFuncion(ambClase, simbolo);
        this.ambitoPropio = amb;

        for (Parametro p : parametros) {
            Tipo t = p.resolverTipo(amb, errores);
            Simbolo sp = new Simbolo(p.getNombre(), CategoriaSimbolo.PARAMETRO, t, p.getLinea(), p.getColumna());
            if (!amb.declarar(sp))
                errores.reportar(p.getLinea(), p.getColumna(), "Parámetro duplicado: '" + p.getNombre() + "'");
        }

        cuerpo.verificar(amb, errores);

        if (!esVoid() && !amb.isTuvoRetorno())
            errores.reportar(linea, columna,
                    "El método '" + nombre + "' debe retornar un valor de tipo " + amb.getTipoRetorno().nombre());
    }

    /**
     * Emite, en este orden:
     *   begin_func, etiquetaMetodo(nombreClase, nombre), parametros.size()+1, null).
     *       El +1}es el "this" implícito que todo método de Z recibe.
     *   entrarAmbito(ambitoPropio) para que, mientras se genera el cuerpo,
     *       cualquier Identificador que resuelva a un ATRIBUTO emita
     *       (=., this, campo, t) en vez de tratarlo como variable local. Y para
     *       que cualquier Llamada con objetivo Identificador lea del
     *       generador el nombre de la clase actual (generador.getClaseActual(),
     *       que {@link Clase#generarC3D} deja fijado antes de recorrer sus métodos).
     *   C3D del cuerpo.
     *   salirAmbito(anterior) para restaurar el ámbito previo.
     *   end_func.
     *
     * La firma lleva nombreClase porque Metodo por sí solo no conoce
     * su clase contenedora (solo sabe su nombre corto).
     */
    public ResultadoC3D generarC3D(GeneradorC3D generador, String nombreClase) {
        String etiqueta = generador.etiquetaMetodo(nombreClase, nombre);

        // --- Registrar la firma ANTES del begin_func ---
        // "this" primero (TipoClase del contenedor), luego los formales en orden.
        List<GeneradorC3D.ParametroFirma> pfs = new ArrayList<>();

        Simbolo simboloClase = null;
        if (ambitoPropio != null && ambitoPropio.getPadre() instanceof AmbitoClase ac) {
            simboloClase = ac.getSimboloContenedor();
        }
        Tipo tipoThis = (simboloClase != null)
                ? new TipoClase(simboloClase)
                : TipoPrimitivo.DESCONOCIDO;
        pfs.add(new GeneradorC3D.ParametroFirma("this", tipoThis));

        for (Parametro p : parametros) {
            Simbolo sp = (ambitoPropio != null) ? ambitoPropio.resolverLocal(p.getNombre()) : null;
            Tipo tp = (sp != null && sp.getTipo() != null) ? sp.getTipo() : TipoPrimitivo.DESCONOCIDO;
            pfs.add(new GeneradorC3D.ParametroFirma(p.getNombre(), tp));
        }

        // Tipo de retorno: AmbitoFuncion ya lo tiene resuelto (lo usa Retorno.verificar).
        Tipo tipoRet = (ambitoPropio != null) ? ambitoPropio.getTipoRetorno() : TipoPrimitivo.VOID;
        if (tipoRet == null) tipoRet = TipoPrimitivo.VOID;

        // Un método SÍ es método de clase (tiene "this").
        generador.registrarFirma(etiqueta, pfs, tipoRet, true);
        // --- fin registro de firma ---

        generador.emitirBeginFunc(etiqueta, parametros.size() + 1);

        Ambito anterior = generador.entrarAmbito(ambitoPropio);
        cuerpo.generarC3D(generador);
        generador.salirAmbito(anterior);

        generador.emitirEndFunc();
        return ResultadoC3D.vacio();
    }
}