package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Acceso;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.ResolucionMiembros;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.List;

/** NEW ID LPAREN argumentList? RPAREN (#primarioInstanciaClase): "new Persona(args)". */
public final class NuevoObjeto extends NodoZ implements ExpresionZ {

    private final String nombreClase;
    private final List<ExpresionZ> argumentos;

    /**
     * Símbolo del constructor resuelto en verificar(). Se usa en generarC3D para
     * obtener los tipos FORMALES (no los de los argumentos reales) y construir la
     * etiqueta manglada coherente con la que registró Constructor.generarC3D.
     * Es null si verificar() no corrió o no encontró el constructor.
     */
    private Simbolo constructorResuelto;

    public NuevoObjeto(String nombreClase, List<ExpresionZ> argumentos, int linea, int columna) {
        super(linea, columna);
        this.nombreClase = nombreClase;
        this.argumentos = argumentos;
    }

    public String getNombreClase() { return nombreClase; }
    public List<ExpresionZ> getArgumentos() { return argumentos; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Simbolo c = ambito.ambitoGlobal().resolverLocal(nombreClase);
        if (c == null || c.getCategoria() != CategoriaSimbolo.CLASE) {
            errores.reportar(linea, columna, "Clase desconocida: '" + nombreClase + "'");
            return TipoPrimitivo.DESCONOCIDO;
        }

        // (1) Verificar argumentos UNA sola vez, guardando sus tipos.
        List<Tipo> tiposArgs = new ArrayList<>();
        for (ExpresionZ a : argumentos) tiposArgs.add(a.verificar(ambito, errores));

        /**
         * (2) Constructor de la clase: clave exacta, despues el mas especifico que acepte
         * los argumentos (por subtipos) y por ultimo la clave generica nombre#aridad para
         * poder reportar "argumento incompatible".
         */
        Simbolo ctor = ResolucionMiembros.resolverConstructor(c, tiposArgs);

        if (ctor == null) {
            errores.reportar(linea, columna,
                    "No existe constructor de '" + nombreClase + "' con "
                            + argumentos.size() + " argumentos");
        } else {
            this.constructorResuelto = ctor;   // cachear para generarC3D
            // Un constructor private solo se puede usar dentro de su propia clase.
            Acceso.verificar(ctor, ambito, errores, linea, columna);
            List<Simbolo> params = ctor.getParametros();
            for (int i = 0; i < tiposArgs.size(); i++) {
                if (!Tipos.esAsignable(params.get(i).getTipo(), tiposArgs.get(i))) {
                    errores.reportar(argumentos.get(i).getLinea(), argumentos.get(i).getColumna(),
                            "Argumento " + (i+1) + " incompatible: se esperaba "
                                    + params.get(i).getTipo().nombre() + ", se recibió "
                                    + tiposArgs.get(i).nombre());
                }
            }
        }
        return new TipoClase(c);
    }

    /**
     * Emite, en este orden:
     *   new, NombreClase, null, t): reserva la celda en heap; t es
     *       la referencia al objeto recién creado.
     *   C3D de cada argumento, en orden, guardando sus lugares (puede haber
     *       llamadas anidadas dentro de un argumento: sus propias cuádruplas se emiten
     *       aquí).
     *   Bloque de param: primero t (el objeto actúa como
     *       this implícito del constructor), luego cada argumento.
     *   (call, etiquetaConstructor(nombreClase, nArgs), nArgs+1, null):
     *       el +1 es el this; el resultado va a null porque un
     *       constructor no devuelve nada y la referencia ya está en t.
     *
     * Por qué los params se agrupan al final y no intercalados con los args:
     * así los nArgs+1 param contiguos anteriores al call son
     * exactamente sus argumentos (this + args en orden). Si un argumento contiene una
     * llamada anidada (new Foo(new Bar(1))), la llamada interna queda completa
     * antes de que se empiecen a emitir los params del new Foo.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        // 1) Reservar el objeto. El temporal t es la referencia (el futuro "this").
        String t = generador.nuevoTemporal();
        generador.emitirNew(nombreClase, t);

        // 2) Evaluar cada argumento (en orden), guardando el lugar donde quedó.
        List<String> lugaresArgs = new ArrayList<>();
        for (ExpresionZ a : argumentos) {
            ResultadoC3D v = a.generarC3D(generador);
            lugaresArgs.add(v.getLugar());
        }

        // 3) Bloque de params: this + args, en orden.
        generador.emitirParam(t);
        for (String lugar : lugaresArgs) {
            generador.emitirParam(lugar);
        }

        /**
         * 4) Llamar al constructor. Sin resultado: la referencia ya está en t.
         * Se usan los tipos FORMALES del símbolo resuelto (no los del argumento real)
         * para reproducir exactamente la etiqueta que generó Constructor.generarC3D.
         */
        List<Tipo> tiposFormales = new java.util.ArrayList<>();
        if (constructorResuelto != null) {
            for (com.proyecto1.semantico.tabla.Simbolo p : constructorResuelto.getParametros()) {
                tiposFormales.add(p.getTipo() != null
                        ? p.getTipo()
                        : TipoPrimitivo.DESCONOCIDO);
            }
        }
        String etiqueta = GeneradorC3D.etiquetaConstructor(nombreClase, tiposFormales);
        generador.emitirCall(etiqueta, argumentos.size() + 1, null);

        // Tipo del resultado: TipoClase(clase). Se resuelve del ámbito activo si se
        // puede; si no, DESCONOCIDO (degradación controlada, igual que Identificador).
        Tipo tipo = TipoPrimitivo.DESCONOCIDO;
        Ambito amb = generador.getAmbito();
        if (amb != null) {
            Simbolo c = amb.ambitoGlobal().resolverLocal(nombreClase);
            if (c != null) tipo = new TipoClase(c);
        }
        return ResultadoC3D.temporal(t, tipo);
    }
}