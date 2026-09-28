package com.proyecto1.semantico.ast.y;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;

public final class HacerMientras extends NodoY implements InstruccionY {

    private final Bloque cuerpo;
    private final ExpresionY condicion;

    public HacerMientras(Bloque cuerpo, ExpresionY condicion, int linea, int columna) {
        super(linea, columna);
        this.cuerpo = cuerpo;
        this.condicion = condicion;
    }

    public Bloque getCuerpo() { return cuerpo; }
    public ExpresionY getCondicion() { return condicion; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoBloque amb = new AmbitoBloque(ambito, true); // esCiclo = true
        cuerpo.verificar(amb, errores);

        Tipo tc = condicion.verificar(ambito, errores);
        if (!Tipos.esBooleano(tc))
            errores.reportar(condicion.getLinea(), condicion.getColumna(),
                    "La condición de 'hacer-mientras' debe ser bool, se recibió " + tc.nombre());

        return TipoPrimitivo.VOID;
    }

    /**
     *   L_inicio:
     *   [cuerpo]                     (entre entrarCiclo y salirCiclo)
     *   L_cond:
     *   [condición]
     *   if_true c goto L_inicio
     *   L_fin:
     * L_inicio va ANTES del cuerpo (el cuerpo se ejecuta al menos una vez y se repite
     * desde ahí). Se registra el ciclo como entrarCiclo(L_cond, L_fin):
     * "continuar" salta a L_cond para evaluar la condición (si saltara a L_inicio se
     * repetiría el cuerpo sin comprobarla) y "romper" salta a L_fin.
     */
    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        /*
            entero i = 0
                mientras (i < 3) hacer
                    i++
         */
        // Se crea y se emite la etiqueta donde inicia cada iteración.
        // Cuádrupla generada "L0:"
        String inicio = generador.nuevaEtiqueta();
        generador.emitirEtiqueta(inicio);
        // Se genera el código de la condición ANTES de entrar al cuerpo.
        // Cuádrupla generada -> "3: t0 = i < 3"
        ResultadoC3D c = condicion.generarC3D(generador);
        // Se crea la etiqueta a la que saltaremos cuando el ciclo termine,
        // pero aún no la emitimos, solo la reservamos.
        String fin = generador.nuevaEtiqueta();
        // Si la condición evaluada arriba es FALSA, el ciclo se rompe y salta a la etiqueta 'fin'.
        // Cuádrupla generada "if_false t0 goto L1"
        generador.emitirIfFalse(c.getLugar(), fin);

        // Se le avisa al generador en qué ciclo estamos.
        // - Si el usuario usa 'continue', el generador sabrá que debe saltar a 'inicio' (L0).
        // - Si el usuario usa 'break', el generador sabrá que debe saltar a 'fin' (L1).
        generador.entrarCiclo(inicio, fin);
        // Se generan las instrucciones de lo que hay dentro del while.
        cuerpo.generarC3D(generador);
        // Limpiamos el entorno porque ya salimos del bloque del ciclo.
        generador.salirCiclo();
        // Una vez terminado el cuerpo, debemos volver incondicionalmente arriba a evaluar la condición.
        // Cuádrupla generada  "goto L0"
        generador.emitirGoto(inicio);
        // Aquí aterrizará el programa cuando la instrucción "if_false" se cumpla o si hubo un "break".
        // Cuádrupla generada "L1:"
        generador.emitirEtiqueta(fin);

        return ResultadoC3D.vacio();
    }
}