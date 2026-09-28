package com.proyecto1.ui.resaltado;

/**
 * Representa un tramo (fragmento contiguo) del texto del editor al que se
 * le debe aplicar un estilo de color determinado.
 * <p>
 * Es una clase inmutable y muy liviana a proposito: {@link HiloResaltado}
 * genera potencialmente miles de instancias por archivo grande, y
 * {@link ResaltadorSintaxis} las recorre una sola vez para construir el
 * {@code TextFlow} visible. No depende de JavaFX ni de ANTLR, por lo que
 * se puede construir y comparar libremente desde el hilo de fondo.
 * </p>
 *
 * @author Proyecto1
 */
public final class TramoColoreado {

    /** Posicion (offset) donde inicia el tramo, en caracteres desde el inicio del texto. */
    private final int inicio;

    /** Longitud del tramo, en caracteres. */
    private final int longitud;

    /**
     * Nombre de la clase CSS a aplicar (ver constantes en {@link MapaColores}),
     * o {@code null} si el tramo no requiere un color especial (texto normal).
     */
    private final String claseCss;

    /**
     * Crea un tramo coloreado.
     *
     * @param inicio   offset de inicio (inclusive), en caracteres
     * @param longitud cantidad de caracteres que abarca el tramo (debe ser mayor a 0)
     * @param claseCss clase CSS a aplicar, o {@code null} para texto sin estilo especial
     */
    public TramoColoreado(int inicio, int longitud, String claseCss) {
        if (longitud <= 0) {
            throw new IllegalArgumentException("La longitud de un TramoColoreado debe ser mayor a 0.");
        }
        this.inicio = inicio;
        this.longitud = longitud;
        this.claseCss = claseCss;
    }

    public int getInicio() {
        return inicio;
    }

    public int getLongitud() {
        return longitud;
    }

    /** @return el offset (exclusivo) donde termina el tramo: {@code inicio + longitud} */
    public int getFin() {
        return inicio + longitud;
    }

    public String getClaseCss() {
        return claseCss;
    }

    @Override
    public String toString() {
        return "TramoColoreado{[" + inicio + "," + getFin() + "), clase=" + claseCss + "}";
    }
}