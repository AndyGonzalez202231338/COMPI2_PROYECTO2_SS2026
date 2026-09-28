package com.proyecto1.codigo.c;

import com.proyecto1.semantico.ast.GeneradorC3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Traduce una GeneradorC3D.Firma a la cabecera de una función C.
 *
 * Produce "<tipoRetorno> <etiqueta>(<param1>, <param2>, ...)", SIN llave
 * de apertura y SIN ";" final: el ensamblador del archivo completo decide si la
 * línea va seguida de "{" (definición) o ";" (prototipo).
 *
 * Sobre "this":no hay caso especial. Para un método o constructor de Z, GeneradorC3D.Firma#parametros()} YA INCLUYE al receptor implícito como su
 * PRIMER elemento (así lo decidieron Constructor.generarC3D y
 * Metodo.generarC3D al llamar registrarFirma(...)). Aquí se trata
 * como un ParametroFirma más: se traduce su tipo (que será <Clase>*) y su nombre (que será "this"), y se emite igual que
 * cualquier otro parámetro.
 *
 * Función sin parámetros: C exige (void) explícito, no () aunque es válido en prototipos modernos, evita warnings
 * con compiladores antiguos).
 */
public final class TraductorFirma {

    private TraductorFirma() {}  // clase de utilidades

    /**
     * Devuelve la cabecera C de firma: tipoRetorno + etiqueta + parámetros.
     * Ejemplo: "int Persona_getEdad(Persona* this)".
     * No incluye llave de apertura ni ";" final.
     */
    public static String traducirCabecera(GeneradorC3D.Firma firma) {
        if (firma == null) return "void __firma_null(void)";
        String tipoRetorno = TraductorTipos.aC(firma.tipoRetorno());
        return tipoRetorno + " " + firma.etiqueta() + traducirParametros(firma);
    }

    /**
     * Devuelve SOLO la lista de parámetros entre paréntesis, con los tipos ya
     * traducidos a C. Ejemplos:
     *   Sin parámetros -> (void)
     *   Un parámetro (int x) -> "(int x)"
     *   Dos parámetros (int a, char* s) -> "(int a, char* s)</li>
     *   Método de Z (Persona* this, int x) -> (Persona* this, int x)"
     *
     * Se expone aparte de traducirCabecera porque un llamador puede
     * querer armar la cabecera con otro formato (alineación, indentación en varias
     * líneas) sin tener que reconstruir la lista.
     */
    public static String traducirParametros(GeneradorC3D.Firma firma) {
        if (firma == null || firma.parametros() == null || firma.parametros().isEmpty()) {
            return "(void)";
        }
        List<String> partes = new ArrayList<>();
        for (GeneradorC3D.ParametroFirma p : firma.parametros()) {
            partes.add(TraductorTipos.aC(p.tipo()) + " " + p.nombre());
        }
        return "(" + String.join(", ", partes) + ")";
    }
}