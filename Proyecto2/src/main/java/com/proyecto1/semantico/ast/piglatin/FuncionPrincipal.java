package com.proyecto1.semantico.ast.piglatin;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.AmbitoBloque;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.List;

/**
 * La funcionPrincipal (#funcionPrincipalDef): MAIOR >> sentencia*
 * FINIS ;. Es el único punto de entrada del programa PigLatin (no tiene nombre,
 * parámetros ni tipo de retorno como Funcion en Y; por eso no se reutiliza
 * esa forma aquí).
 */
public final class FuncionPrincipal extends NodoPigLatin {

    private final List<InstruccionPigLatin> cuerpo;

    public FuncionPrincipal(List<InstruccionPigLatin> cuerpo, int linea, int columna) {
        super(linea, columna);
        this.cuerpo = cuerpo;
    }

    public List<InstruccionPigLatin> getCuerpo() {
        return cuerpo;
    }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        AmbitoBloque amb = new AmbitoBloque(ambito, false);
        for (InstruccionPigLatin i : cuerpo) i.verificar(amb, errores);
        return TipoPrimitivo.VOID;
    }

    /**
     * Emite, en este orden:
     *   (begin_func, "main", 0, null): punto de entrada fijo, aridad 0
     *       (PigLatin no tiene this implícito, no recibe nada).</li>
     *   C3D de las variables globales, en el orden en que aparecen en el
     *       programa. Se declaran DENTRO del begin_func...end_func
     *       porque el C3D no distingue variables globales de locales.
     *   C3D del cuerpo, en orden.
     *   (end_func, null, null, null)
     * La firma lleva variablesGlobales porque FuncionPrincipal
     * no las conoce por sí misma (solo tiene su propio cuerpo). Programa se
     * las pasa.
     */
    public ResultadoC3D generarC3D(GeneradorC3D generador, List<InstruccionPigLatin> variablesGlobales) {
        generador.registrarFirma("main", List.of(), TipoPrimitivo.VOID, false);
        generador.emitirBeginFunc("main", 0);

        for (InstruccionPigLatin decl : variablesGlobales) {
            decl.generarC3D(generador);
        }
        for (InstruccionPigLatin i : cuerpo) {
            i.generarC3D(generador);
        }

        generador.emitirEndFunc();
        return ResultadoC3D.vacio();
    }
}