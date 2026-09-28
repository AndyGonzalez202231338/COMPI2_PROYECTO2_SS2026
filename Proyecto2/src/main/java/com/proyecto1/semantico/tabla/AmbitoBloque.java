package com.proyecto1.semantico.tabla;

/**
 * Ámbito de un bloque: el cuerpo de un si/sino, para, mientras, hacer-mientras, un caso
 * de elegir/switch, o un bloque "{}"/INDENT-DEDENT suelto. Las variables declaradas
 * aquí dejan de existir al salir del bloque (dejan de ser resolubles), que es
 * justamente lo que se logra al no volver a consultar este Ambito una vez terminado
 * de visitar sus instrucciones.
 *
 * Lleva DOS marcas independientes:
 *   - "esCiclo": true SOLO para el cuerpo directo de un para/mientras/hacer-mientras.
 *     Es lo único que habilita "continuar" (dentroDeAlgunCiclo()): "continuar" no tiene
 *     sentido dentro de un elegir/switch que no esté a su vez dentro de un ciclo.
 *   - "permiteRomper": true para un ciclo (mismo caso que esCiclo) O para el cuerpo de
 *     un caso/siempre de un elegir. Habilita "romper" (dentroDeAlgoRompible()): en Y?,
 *     igual que en C, "romper" sirve tanto para salir de un ciclo como de un switch.
 *
 * Se separan en dos flags (en vez de reusar esCiclo para ambos) porque no son lo mismo:
 * un elegir es "rompible" pero NO es un ciclo (un "continuar" dentro de un caso, sin un
 * ciclo por fuera, sigue siendo un error).
 */
public class AmbitoBloque extends Ambito {

    private final boolean esCiclo;
    private final boolean permiteRomper;

    /** Constructor original: un ciclo es siempre "rompible", cualquier otro bloque no. */
    public AmbitoBloque(Ambito padre, boolean esCiclo) {
        this(padre, esCiclo, esCiclo);
    }

    /** Constructor completo, para bloques que son "rompibles" sin ser un ciclo (caso de elegir). */
    public AmbitoBloque(Ambito padre, boolean esCiclo, boolean permiteRomper) {
        super(padre);
        this.esCiclo = esCiclo;
        this.permiteRomper = permiteRomper;
    }

    public boolean isEsCiclo() { return esCiclo; }

    /** Sube por la cadena de ámbitos (sin cruzar el límite de una función) buscando un ciclo. */
    public boolean dentroDeAlgunCiclo() {
        Ambito actual = this;
        while (actual != null && !(actual instanceof AmbitoFuncion)) {
            if (actual instanceof AmbitoBloque ab && ab.esCiclo) return true;
            actual = actual.getPadre();
        }
        return false;
    }

    /**
     * Sube por la cadena de ámbitos (sin cruzar el límite de una función) buscando algo
     * de lo que se pueda "romper": un ciclo o un caso/siempre de un elegir.
     */
    public boolean dentroDeAlgoRompible() {
        Ambito actual = this;
        while (actual != null && !(actual instanceof AmbitoFuncion)) {
            if (actual instanceof AmbitoBloque ab && (ab.esCiclo || ab.permiteRomper)) return true;
            actual = actual.getPadre();
        }
        return false;
    }

    @Override
    public String descripcion() {
        return "bloque";
    }
}