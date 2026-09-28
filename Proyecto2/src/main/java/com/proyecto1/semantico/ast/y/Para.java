package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

public final class Para extends NodoY implements InstruccionY {

    private final InstruccionY inicializacion;
    private final ExpresionY condicion;
    private final InstruccionY actualizacion;
    private final Bloque cuerpo;

    public Para(InstruccionY inicializacion, ExpresionY condicion, InstruccionY actualizacion,
                Bloque cuerpo, int linea, int columna) {
        super(linea, columna);
        this.inicializacion = inicializacion;
        this.condicion = condicion;
        this.actualizacion = actualizacion;
        this.cuerpo = cuerpo;
    }

    public InstruccionY getInicializacion() { return inicializacion; }
    public ExpresionY getCondicion() { return condicion; }
    public InstruccionY getActualizacion() { return actualizacion; }
    public Bloque getCuerpo() { return cuerpo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        // El "para" introduce su propio ámbito (la variable de init vive solo ahí)
        AmbitoBloque ambCiclo = new AmbitoBloque(ambito, true); // esCiclo = true

        if (inicializacion != null) inicializacion.verificar(ambCiclo, errores);

        if (condicion != null) {
            Tipo tc = condicion.verificar(ambCiclo, errores);
            if (!Tipos.esBooleano(tc))
                errores.reportar(condicion.getLinea(), condicion.getColumna(),
                        "La condición del 'para' debe ser bool, se recibió " + tc.nombre());
        }

        if (actualizacion != null) actualizacion.verificar(ambCiclo, errores);

        // El cuerpo tiene su propio sub-ámbito (hijo del de ciclo)
        AmbitoBloque ambCuerpo = new AmbitoBloque(ambCiclo, false);
        cuerpo.verificar(ambCuerpo, errores);

        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, en este orden:
     *   [init]                       (una sola vez, fuera del ciclo)
     *   L_inicio:
     *   [condición]                  (solo si hay condición)
     *   if_false c goto L_fin
     *   [cuerpo]                     (entre entrarCiclo y salirCiclo)
     *   L_act:
     *   [act]                        (solo si hay actualización)
     *   goto L_inicio
     *   L_fin:
     *
     * Se registra el ciclo como entrarCiclo(L_act, L_fin): "continuar" salta a
     * L_act (así la actualización SÍ se ejecuta; si saltara a L_inicio, un "continuar"
     * dejaría el contador sin avanzar y el ciclo sería infinito) y "romper" salta a
     * L_fin sin ejecutar la actualización. Init y act quedan FUERA de entrarCiclo /
     * salirCiclo. Sin condición el ciclo no emite if_false (solo termina con "romper").
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        //para (entero i = 0; i < 3; i++):
        // L0
        String inicio = generador.nuevaEtiqueta();
        // actualizar Etiqueta donde se hace el incremento/decremento (i++). L1
        String actualizar = generador.nuevaEtiqueta();
        // L2 Etiqueta de escape para salir del ciclo.
        String fin = generador.nuevaEtiqueta();

        // Cuádrupla generada "1: i = 0"
        if (inicializacion != null) {
            inicializacion.generarC3D(generador);
        }

        // Se emite la etiqueta donde inicia el bloque de validación.
        // Cuádrupla generada "L0:"
        generador.emitirEtiqueta(inicio);

        // Cuádruplas generadas "t0 = i < 3"
        if (condicion != null) {
            ResultadoC3D c = condicion.generarC3D(generador);
            // Si la condición es falsa, rompemos el ciclo saltando a 'fin'.
            // Cuádrupla generada "if_false t0 goto L2"
            generador.emitirIfFalse(c.getLugar(), fin);
        }


        // Para el 'continue', se le manda la etiqueta "L1",
        // Así, un continue ejecuta el i++ antes de volver a preguntar si i < 3.
        generador.entrarCiclo(actualizar, fin);
        cuerpo.generarC3D(generador);

        // Se limpia el entorno al salir del cuerpo.
        generador.salirCiclo();


        // Cuádrupla generada -> "L1"
        generador.emitirEtiqueta(actualizar);


        // Cuádruplas generadas "t1 = i", "t2 = i + 1", "i = t2"
        if (actualizacion != null) {
            actualizacion.generarC3D(generador);
        }

        // Después de actualizar, saltamos incondicionalmente a evaluar la condición nuevamente.
        // Cuádrupla generada "goto L0"
        generador.emitirGoto(inicio);

        // Se emite la etiqueta de salida donde aterriza el "if_false" (o un "break").
        // Cuádrupla generada "L2:"
        generador.emitirEtiqueta(fin);

        return ResultadoC3D.vacio();
    }
}