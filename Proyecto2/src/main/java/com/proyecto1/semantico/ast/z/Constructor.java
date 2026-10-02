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
 * (#constructorDef): "NombreClase ( parametros? ) { cuerpo }".
 *
 * El nombre del constructor coincide con el de su clase; guarda ese
 * nombre (es lo que después alimenta a
 * #etiquetaConstructor(String, int)).
 *
 * Todo método/constructor de Z recibe un parámetro implícito extra: "this". Por eso
 * el @code begin_func lleva parametros.size() + 1 argumentos. El "this" no se
 * declara como símbolo en la tabla (no es un identificador resoluble); es una
 * convención de generación: cualquier acceso a un atributo emite
 * (=., this, campo, t) / (.=, this, campo, v).
 */
public final class Constructor extends NodoZ /* o la base que ya uses */ {

    private final String nombre;              // == nombre de la clase
    private final List<Parametro> parametros;
    private final Bloque cuerpo;              // o InstruccionZ, según tu gramática

    /**
     * Ámbito de la función, creado y validado por verificar() y reutilizado por
     * generarC3D() para no reconstruir parámetros ni volver a tocar ManejadorErrores.
     * Es el mismo patrón que AccesoCampo usa para cachear tipoCampo.
     */
    private AmbitoFuncion ambitoPropio;
    private String nombreClaseReal;

    public Constructor(String nombre, List<Parametro> parametros, Bloque cuerpo,
                       int linea, int columna) {
        super(linea, columna);
        this.nombre = nombre;
        this.parametros = parametros;
        this.cuerpo = cuerpo;
    }

    public String getNombre() { return nombre; }
    public List<Parametro> getParametros() { return parametros; }
    public Bloque getCuerpo() { return cuerpo; }
    public AmbitoFuncion getAmbitoPropio() { return ambitoPropio; }

    /**
     * Idéntico al verificar() que ya tenías, MÁS una línea al final del setup del
     * ámbito: cachear el {AmbitoFuncion} recién creado en #ambitoPropio
     * para que generarC3D() lo reutilice.
     */
    public Tipo verificar(AmbitoClase ambClase, ManejadorErrores errores) {
        // (1) Guardar el nombre REAL de la clase para usarlo al generar la etiqueta C3D.
        //     El error por nombre incorrecto YA se reportó en AnalizadorSemanticoZ;
        //     aquí no se vuelve a chequear (evita el mensaje duplicado).
        this.nombreClaseReal = ambClase.getSimboloContenedor().getNombre();

        // (2) Lookup con clave específica: nombreClaseReal#aridad#Tipo1#Tipo2...
        StringBuilder sb = new StringBuilder(nombreClaseReal)
                .append("#").append(parametros.size());
        for (Parametro p : parametros) {
            Tipo tp = p.getTipo().resolver(ambClase, errores);
            sb.append("#").append(tp.nombre());
        }
        Simbolo simbolo = ambClase.getSimboloContenedor().buscarMiembro(sb.toString());

        AmbitoFuncion amb = new AmbitoFuncion(ambClase, simbolo);
        this.ambitoPropio = amb;

        for (Parametro p : parametros) {
            Tipo t = p.resolverTipo(amb, errores);
            Simbolo sp = new Simbolo(p.getNombre(), CategoriaSimbolo.PARAMETRO,
                    t, p.getLinea(), p.getColumna());
            if (!amb.declarar(sp)) {
                errores.reportar(p.getLinea(), p.getColumna(),
                        "Parámetro duplicado: '" + p.getNombre() + "'");
            }
        }

        cuerpo.verificar(amb, errores);
        return TipoPrimitivo.VOID;
    }


    /**
     *   (begin_func, etiquetaConstructor(nombre, aridad), parametros.size()+1, null).
     *       El +1 es el "this" implícito.
     *   entrarAmbito(ambitoPropio)}.
     *   Por cada atributo con inicializador no nulo:
     *       this.<campo> = <C3D del inicializador> (una cuádrupla (.=, this, campo, v)).
     *   C3D del cuerpo del usuario.
     *   salirAmbito(anterior).
     *   (end_func).
     * La firma lleva atributosClase porque el constructor debe inyectar los
     * field initializers de la clase y Constructor por sí solo no los conoce.
     * Clase#generarC3D es quien los pasa.
     */
    public ResultadoC3D generarC3D(GeneradorC3D generador, List<Atributo> atributosClase) {
        // Usar el nombre real de la clase (si verificar ya corrió), no el declarado.
        String nombreParaEtiqueta = (nombreClaseReal != null) ? nombreClaseReal : nombre;

        // Tipos formales de los parámetros (excluye "this").
        List<Tipo> tiposFormales = new java.util.ArrayList<>();
        for (Parametro p : parametros) {
            Tipo tp = (ambitoPropio != null)
                    ? ambitoPropio.resolverLocal(p.getNombre()) != null
                    ? ambitoPropio.resolverLocal(p.getNombre()).getTipo()
                    : null
                    : null;
            tiposFormales.add(tp != null ? tp : com.proyecto1.semantico.tipos.TipoPrimitivo.DESCONOCIDO);
        }
        String etiqueta = GeneradorC3D.etiquetaConstructor(nombreParaEtiqueta, tiposFormales);

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
            // El parámetro ya está declarado en ambitoPropio por verificar(): lo leemos de ahí
            // en vez de volver a resolver el NodoTipoRef (que requeriría un ManejadorErrores).
            Simbolo sp = (ambitoPropio != null) ? ambitoPropio.resolverLocal(p.getNombre()) : null;
            Tipo tp = (sp != null && sp.getTipo() != null) ? sp.getTipo() : TipoPrimitivo.DESCONOCIDO;
            pfs.add(new GeneradorC3D.ParametroFirma(p.getNombre(), tp));
        }

        // Un constructor NO retorna valor y SÍ es método de clase (tiene "this").
        generador.registrarFirma(etiqueta, pfs, TipoPrimitivo.VOID, true);
        // --- fin registro de firma ---

        generador.emitirBeginFunc(etiqueta, parametros.size() + 1);

        Ambito anterior = generador.entrarAmbito(ambitoPropio);

        for (Atributo a : atributosClase) {
            if (a.getInicializador() != null) {
                ResultadoC3D v = a.getInicializador().generarC3D(generador);
                generador.emitirGuardarCampo("this", a.getNombre(), v.getLugar());
            }
        }

        cuerpo.generarC3D(generador);

        generador.salirAmbito(anterior);
        generador.emitirEndFunc();
        return ResultadoC3D.vacio();
    }
}