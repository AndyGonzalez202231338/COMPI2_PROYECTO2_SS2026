package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

public final class Mientras extends NodoY implements InstruccionY {

    private final ExpresionY condicion;
    private final Bloque cuerpo;

    public Mientras(ExpresionY condicion, Bloque cuerpo, int linea, int columna) {
        super(linea, columna);
        this.condicion = condicion;
        this.cuerpo = cuerpo;
    }

    public ExpresionY getCondicion() { return condicion; }
    public Bloque getCuerpo() { return cuerpo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo tc = condicion.verificar(ambito, errores);
        if (!Tipos.esBooleano(tc))
            errores.reportar(condicion.getLinea(), condicion.getColumna(),
                    "La condición de 'mientras' debe ser bool, se recibió " + tc.nombre());

        AmbitoBloque amb = new AmbitoBloque(ambito, true); // esCiclo = true
        cuerpo.verificar(amb, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     *   L_inicio:
     *   [cuádruplas de la condición]
     *   if_false c goto L_fin
     *   [cuerpo]                 (entre entrarCiclo y salirCiclo)
     *   goto L_inicio
     *   L_fin:
     * Las dos etiquetas se piden por adelantado porque L_fin ya se necesita en el
     * if_false, antes de emitirla. "continuar" salta a L_inicio (se vuelve a evaluar la
     * condición) y "romper" a L_fin; ambas se registran en las pilas del generador solo
     * mientras se genera el cuerpo.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        /*
            hacer:
                i++
            mientras (i < 3)
         */
        // Se generan las etiquetas que controlarán el flujo del ciclo.
        // 'inicio' será el punto donde se evalúa la condición en cada iteración (ej. L0).
        String inicio = generador.nuevaEtiqueta();
        // 'fin' será el punto de escape cuando la condición sea falsa o haya un break (ej. L1).
        String fin = generador.nuevaEtiqueta();

        // Se emite la etiqueta de inicio "L0" en el código C3D.
        // A este punto regresará el programa al terminar cada iteración.
        generador.emitirEtiqueta(inicio);

        // Se genera el C3D de la condición. Retorna el temporal donde quedó el resultado "t0".
        ResultadoC3D c = condicion.generarC3D(generador);

        // Se emite el salto condicional. Si la condición (c.getLugar()) es FALSA,
        // el ciclo se rompe y el flujo salta directamente a la etiqueta 'fin' "if_false t0 goto L1".
        generador.emitirIfFalse(c.getLugar(), fin);

        // Se le indica al generador que estamos dentro de un ciclo.
        // Esto es crucial para que si dentro del cuerpo viene un "continue", sepa que debe saltar a 'inicio'.
        // Y si viene un "break", sepa que debe saltar a 'fin'.
        generador.entrarCiclo(inicio, fin);

        // Se compilan y emiten todas las instrucciones que están dentro del "mientras".
        cuerpo.generarC3D(generador);

        // Limpiamos el entorno porque ya terminamos de procesar el bloque del ciclo,
        // así los break/continue de ciclos más externos vuelven a funcionar correctamente.
        generador.salirCiclo();

        // Al terminar de ejecutar el cuerpo, se emite un salto incondicional de regreso
        // a la etiqueta 'inicio' para volver a evaluar la condición (ej. goto L0).
        generador.emitirGoto(inicio);

        // Finalmente, se emite la etiqueta 'fin' "L1"
        // Aquí aterrizará el programa cuando el "if_false" de arriba se cumpla.
        generador.emitirEtiqueta(fin);

        // Se retorna un resultado vacío ya que las sentencias de control no devuelven un valor temporal.
        return ResultadoC3D.vacio();
    }
}