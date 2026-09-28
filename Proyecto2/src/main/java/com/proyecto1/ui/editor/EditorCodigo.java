package com.proyecto1.ui.editor;

import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;
import javafx.animation.Timeline;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor de codigo propio. Usa una fuente monoespaciada, por lo que la
 * posicion de cualquier caracter se calcula con (linea, columna) *
 * (alto de linea, ancho de caracter). Asi el caret, la seleccion y los
 * clics NO dependen de los bounds de los nodos Text del TextFlow (que
 * cambian cada vez que el resaltador reemplaza los nodos).
 */
public class EditorCodigo extends Pane {

    public interface EscuchaTexto { void textoCambiado(String nuevoTexto); }
    public interface EscuchaCursor { void cursorCambiado(int linea, int columna); }

    /** Margen interno. El gutter usa PADDING_SUPERIOR para alinear sus numeros. */
    public static final double PADDING_IZQUIERDO = 10;
    public static final double PADDING_SUPERIOR = 8;

    private static final int ESPACIOS_POR_TAB = 4;

    private final StringBuilder texto = new StringBuilder();
    private int caret = 0;
    private int ancla = 0;
    private int columnaPreferida = -1;

    private final TextFlow flow = new TextFlow();
    private final Pane capaErrores = new Pane();
    private final Pane capaCaret = new Pane();
    private final Pane capaSeleccion = new Pane();
    private final Line caretLinea = new Line();
    private final Timeline parpadeo;

    private Font fuente = Font.font("Monospaced", 13);
    private final Color colorCaret = Color.web("#0c4a6e");
    private final Color colorSeleccion = Color.web("#0ea5e9", 0.30);

    private ScrollPane scroll;
    private int totalLineas = 1;
    private int maxColumnas = 0;

    private double altoLineaCache = 0;
    private double anchoCarCache = 0;

    private final List<EscuchaTexto> escuchasTexto = new ArrayList<>();
    private EscuchaCursor escuchaCursor;

    public EditorCodigo() {
        setFocusTraversable(true);
        setStyle("-fx-background-color: white;");

        // Un Pane NO aplica padding a sus hijos: hay que posicionar el flow a mano.
        flow.setLayoutX(PADDING_IZQUIERDO);
        flow.setLayoutY(PADDING_SUPERIOR);

        // FIX: la linea del caret nunca se agregaba a ninguna capa -> invisible.
        capaCaret.getChildren().add(caretLinea);
        // capaErrores va PRIMERO (mas atras) para que quede detras del texto y
        // de la seleccion, igual que un resaltado de fondo.
        getChildren().addAll(capaErrores, capaSeleccion, flow, capaCaret);
        capaErrores.setMouseTransparent(true);
        capaSeleccion.setMouseTransparent(true);
        flow.setMouseTransparent(true);
        capaCaret.setMouseTransparent(true);

        caretLinea.setStroke(colorCaret);
        caretLinea.setStrokeWidth(1.5);
        caretLinea.setVisible(false);

        // Que el texto no se salga del area del editor.
        Rectangle recorte = new Rectangle();
        recorte.widthProperty().bind(widthProperty());
        recorte.heightProperty().bind(heightProperty());
        setClip(recorte);

        // Parpadeo del caret
        parpadeo = new Timeline(new KeyFrame(Duration.millis(530),
                e -> caretLinea.setVisible(!caretLinea.isVisible())));
        parpadeo.setCycleCount(Timeline.INDEFINITE);
        focusedProperty().addListener((obs, antes, enfocado) -> {
            if (enfocado) {
                reiniciarParpadeo();
            } else {
                parpadeo.stop();
                caretLinea.setVisible(false);
            }
        });

        setOnKeyPressed(this::alPresionarTecla);
        setOnKeyTyped(this::alTeclear);
        setOnMousePressed(this::alPresionarMouse);
        setOnMouseDragged(this::alArrastrarMouse);
        setOnMouseClicked(e -> requestFocus());

        redibujarTodo();
    }

    // API publica
    public String getTexto() { return texto.toString(); }

    public void setTexto(String nuevo) {
        texto.setLength(0);
        texto.append(normalizar(nuevo));
        caret = Math.min(caret, texto.length());
        ancla = caret;
        redibujarTodo();
        notificarTexto();
    }

    public void setFuente(Font f) {
        this.fuente = f;
        this.altoLineaCache = 0;
        this.anchoCarCache = 0;
        redibujarTodo();
    }

    public Font getFuente() { return fuente; }

    /** ScrollPane que contiene a este editor (para seguir al cursor). */
    public void setScrollPane(ScrollPane sp) { this.scroll = sp; }

    public void agregarEscuchaTexto(EscuchaTexto e) {
        if (e != null) escuchasTexto.add(e);
    }
    public void setEscuchaCursor(EscuchaCursor e) { this.escuchaCursor = e; }

    public void aplicarTramosColoreados(List<Text> nodos) {
        flow.getChildren().setAll(nodos);
        redibujarCaretYSeleccion();
    }

    /**
     * Pinta de fondo (rojo translucido) cada linea de {@code lineasBase1} (numeradas
     * desde 1, igual que {@link #getLineaActual()}). No toca el resaltado de
     * sintaxis (vive en {@code flow}, esta capa es independiente y va detras de
     * todo). Lineas fuera de rango se ignoran en silencio.
     */
    public void marcarLineasConError(List<Integer> lineasBase1) {
        capaErrores.getChildren().clear();
        double alto = medirAltoLinea();
        double ancho = medirAnchoCaracter();
        double anchoMarca = Math.max(getWidth() - PADDING_IZQUIERDO, (maxColumnas + 2) * ancho);

        for (int lineaBase1 : lineasBase1) {
            int indiceLinea = lineaBase1 - 1;
            if (indiceLinea < 0 || indiceLinea >= contarLineas()) continue;
            Rectangle marca = new Rectangle(PADDING_IZQUIERDO, PADDING_SUPERIOR + indiceLinea * alto,
                    anchoMarca, alto);
            marca.setFill(Color.web("#d32f2f", 0.15));
            capaErrores.getChildren().add(marca);
        }
    }

    public void limpiarMarcasDeError() {
        capaErrores.getChildren().clear();
    }

    /** Linea actual (base 1). */
    public int getLineaActual() { return lineaDe(caret) + 1; }

    /** Columna actual (base 1). */
    public int getColumnaActual() { return caret - inicioLinea(caret) + 1; }

    /** Unifica saltos de linea y convierte tabs a espacios (el calculo de columnas lo requiere). */
    private static String normalizar(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace('\r', '\n')
                .replace("\t", " ".repeat(ESPACIOS_POR_TAB));
    }

    // Edicion
    private void insertar(String s) {
        if (haySeleccion()) borrarSeleccion();
        texto.insert(caret, s);
        caret += s.length();
        ancla = caret;
        columnaPreferida = -1;
        redibujarTodo();
        notificarTexto();
        notificarCursor();
    }

    private void borrarHaciaAtras() {
        if (haySeleccion()) { borrarSeleccion(); return; }
        if (caret == 0) return;
        texto.deleteCharAt(caret - 1);
        caret--; ancla = caret;
        redibujarTodo(); notificarTexto(); notificarCursor();
    }

    private void borrarHaciaAdelante() {
        if (haySeleccion()) { borrarSeleccion(); return; }
        if (caret >= texto.length()) return;
        texto.deleteCharAt(caret);
        redibujarTodo(); notificarTexto(); notificarCursor();
    }

    private void borrarSeleccion() {
        int ini = Math.min(caret, ancla);
        int fin = Math.max(caret, ancla);
        texto.delete(ini, fin);
        caret = ini; ancla = ini;
        redibujarTodo(); notificarTexto(); notificarCursor();
    }

    private boolean haySeleccion() { return caret != ancla; }

    // Teclado
    private void alPresionarTecla(KeyEvent e) {
        boolean shift = e.isShiftDown();
        boolean ctrl = e.isControlDown() || e.isMetaDown();
        switch (e.getCode()) {
            case LEFT:  moverCaret(caret - 1, shift); e.consume(); break;
            case RIGHT: moverCaret(caret + 1, shift); e.consume(); break;
            case UP:    moverLinea(-1, shift); e.consume(); break;
            case DOWN:  moverLinea(+1, shift); e.consume(); break;
            case HOME:  moverCaret(inicioLinea(caret), shift); e.consume(); break;
            case END:   moverCaret(finLinea(caret), shift); e.consume(); break;
            case BACK_SPACE: borrarHaciaAtras(); e.consume(); break;
            case DELETE:     borrarHaciaAdelante(); e.consume(); break;
            case ENTER:      insertar("\n"); e.consume(); break;
            case TAB:        insertar(" ".repeat(ESPACIOS_POR_TAB)); e.consume(); break;
            default:
                if (ctrl) {
                    switch (e.getCode()) {
                        case A: seleccionarTodo(); e.consume(); break;
                        case C: copiar(); e.consume(); break;
                        case X: cortar(); e.consume(); break;
                        case V: pegar(); e.consume(); break;
                        default: break;
                    }
                }
        }
    }

    private void alTeclear(KeyEvent e) {
        String s = e.getCharacter();
        if (s == null || s.isEmpty()) return;
        char c = s.charAt(0);
        if (c < 32 || c == 127) return;
        insertar(s);
        e.consume();
    }

    private void moverCaret(int nuevo, boolean extendiendo) {
        caret = Math.max(0, Math.min(texto.length(), nuevo));
        if (!extendiendo) ancla = caret;
        columnaPreferida = -1;
        redibujarCaretYSeleccion();
        notificarCursor();
    }

    private void moverLinea(int delta, boolean extendiendo) {
        int col = (columnaPreferida >= 0) ? columnaPreferida : getColumnaActual();
        int lineaDestino = getLineaActual() + delta;
        if (lineaDestino < 1 || lineaDestino > contarLineas()) return;

        int ini = inicioDeLineaNumero(lineaDestino - 1);
        int fin = finLinea(ini);
        int objetivo = Math.min(ini + col - 1, fin);
        moverCaret(objetivo, extendiendo);
        columnaPreferida = col; // moverCaret la resetea; la restauramos
    }

    private void seleccionarTodo() {
        ancla = 0;
        caret = texto.length();
        redibujarCaretYSeleccion();
        notificarCursor();
    }

    private void seleccionarPalabraEn(int pos) {
        int ini = pos, fin = pos;
        while (ini > 0 && esParteDePalabra(texto.charAt(ini - 1))) ini--;
        while (fin < texto.length() && esParteDePalabra(texto.charAt(fin))) fin++;
        if (ini == fin && fin < texto.length() && texto.charAt(fin) != '\n') fin++; // simbolo suelto
        ancla = ini;
        caret = fin;
        columnaPreferida = -1;
        redibujarCaretYSeleccion();
        notificarCursor();
    }

    private void seleccionarLineaEn(int pos) {
        ancla = inicioLinea(pos);
        caret = Math.min(finLinea(pos) + 1, texto.length());
        columnaPreferida = -1;
        redibujarCaretYSeleccion();
        notificarCursor();
    }

    private static boolean esParteDePalabra(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // Portapapeles
    private void copiar() {
        if (!haySeleccion()) return;
        int ini = Math.min(caret, ancla), fin = Math.max(caret, ancla);
        ClipboardContent cc = new ClipboardContent();
        cc.putString(texto.substring(ini, fin));
        Clipboard.getSystemClipboard().setContent(cc);
    }

    private void cortar() { if (!haySeleccion()) return; copiar(); borrarSeleccion(); }

    private void pegar() {
        String s = Clipboard.getSystemClipboard().getString();
        if (s == null) return;
        insertar(normalizar(s));
    }

    // Mouse
    private void alPresionarMouse(MouseEvent e) {
        requestFocus();
        int pos = indiceDesdePunto(e.getX(), e.getY());
        if (e.getClickCount() == 2)      seleccionarPalabraEn(pos);
        else if (e.getClickCount() >= 3) seleccionarLineaEn(pos);
        else                             moverCaret(pos, e.isShiftDown());
        e.consume();
    }

    private void alArrastrarMouse(MouseEvent e) {
        moverCaret(indiceDesdePunto(e.getX(), e.getY()), true);
        e.consume();
    }

    /** Punto (x,y) en coordenadas del editor -> indice de caracter. */
    private int indiceDesdePunto(double x, double y) {
        double alto = medirAltoLinea();
        double ancho = medirAnchoCaracter();

        int linea = (int) Math.floor((y - PADDING_SUPERIOR) / alto);
        linea = Math.max(0, Math.min(contarLineas() - 1, linea));

        int ini = inicioDeLineaNumero(linea);
        int fin = finLinea(ini);

        int col = (int) Math.round((x - PADDING_IZQUIERDO) / ancho);
        col = Math.max(0, Math.min(fin - ini, col));
        return ini + col;
    }

    // Redibujado
    private void redibujarTodo() {
        recalcularTamano();
        Text t = new Text(texto.toString());
        t.setFont(fuente);
        flow.getChildren().setAll(t);
        redibujarCaretYSeleccion();
    }

    private void redibujarCaretYSeleccion() {
        actualizarCaret();
        actualizarSeleccion();
    }

    private void actualizarCaret() {
        double x = PADDING_IZQUIERDO + (caret - inicioLinea(caret)) * medirAnchoCaracter();
        double y = PADDING_SUPERIOR + lineaDe(caret) * medirAltoLinea();
        caretLinea.setStartX(x); caretLinea.setEndX(x);
        caretLinea.setStartY(y); caretLinea.setEndY(y + medirAltoLinea());
        if (isFocused()) reiniciarParpadeo(); // que no se apague mientras se escribe
    }

    private void reiniciarParpadeo() {
        caretLinea.setVisible(true);
        parpadeo.playFromStart();
    }

    private void actualizarSeleccion() {
        capaSeleccion.getChildren().clear();
        if (!haySeleccion()) return;

        int ini = Math.min(caret, ancla);
        int fin = Math.max(caret, ancla);
        double alto = medirAltoLinea();
        double ancho = medirAnchoCaracter();

        int pos = ini;
        int linea = lineaDe(ini);
        while (true) {
            int nl = texto.indexOf("\n", pos);
            boolean incluyeSaltoDeLinea = nl >= 0 && nl < fin;
            int finTramo = incluyeSaltoDeLinea ? nl : fin;

            int colIni = pos - inicioLinea(pos);
            double w = (finTramo - pos) * ancho;
            if (incluyeSaltoDeLinea) w += ancho; // muestra el salto de linea seleccionado

            if (w > 0) {
                Rectangle r = new Rectangle(
                        PADDING_IZQUIERDO + colIni * ancho,
                        PADDING_SUPERIOR + linea * alto,
                        w, alto);
                r.setFill(colorSeleccion);
                capaSeleccion.getChildren().add(r);
            }

            if (!incluyeSaltoDeLinea) break;
            pos = nl + 1;
            linea++;
        }
    }

    // Tamano propio (para que el ScrollPane sepa cuanto scrollear)
    private void recalcularTamano() {
        int lineas = 1, max = 0, col = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') { lineas++; max = Math.max(max, col); col = 0; }
            else col++;
        }
        max = Math.max(max, col);
        boolean cambio = lineas != totalLineas || max != maxColumnas;
        totalLineas = lineas;
        maxColumnas = max;
        if (cambio) requestLayout();
    }

    private double anchoNecesario() {
        return PADDING_IZQUIERDO * 2 + (maxColumnas + 2) * medirAnchoCaracter();
    }

    private double altoNecesario() {
        return PADDING_SUPERIOR * 2 + totalLineas * medirAltoLinea();
    }

    // Pref y min iguales: con fitToWidth/fitToHeight el ScrollPane llena el
    // viewport si el contenido es mas chico, y muestra barras si es mas grande.
    @Override protected double computePrefWidth(double alto)  { return anchoNecesario(); }
    @Override protected double computePrefHeight(double ancho) { return altoNecesario(); }
    @Override protected double computeMinWidth(double alto)   { return anchoNecesario(); }
    @Override protected double computeMinHeight(double ancho) { return altoNecesario(); }

    /**
     * Desplaza el ScrollPane lo minimo necesario para que el caret quede visible.
     * El gutter permanece fijo a la izquierda, por eso la ventana visible del
     * editor (en sus coordenadas) es [offX, offX + viewport - anchoGutter].
     */
    private void asegurarCaretVisible() {
        if (scroll == null || scroll.getContent() == null || getScene() == null) return;
        scroll.applyCss();
        scroll.layout();

        Bounds vp = scroll.getViewportBounds();
        Bounds cont = scroll.getContent().getLayoutBounds();
        double anchoGutter = getLayoutX();
        double visibleW = vp.getWidth() - anchoGutter;
        double visibleH = vp.getHeight();
        if (visibleW <= 0 || visibleH <= 0) return;

        double ancho = medirAnchoCaracter();
        double alto = medirAltoLinea();
        double cx = PADDING_IZQUIERDO + (caret - inicioLinea(caret)) * ancho;
        double cy = PADDING_SUPERIOR + lineaDe(caret) * alto;

        double maxOffY = Math.max(0, cont.getHeight() - visibleH);
        double maxOffX = Math.max(0, cont.getWidth() - vp.getWidth());
        double offY = scroll.getVvalue() * maxOffY;
        double offX = scroll.getHvalue() * maxOffX;

        if (cy - PADDING_SUPERIOR < offY) {
            offY = cy - PADDING_SUPERIOR;
        } else if (cy + alto + PADDING_SUPERIOR > offY + visibleH) {
            offY = cy + alto + PADDING_SUPERIOR - visibleH;
        }
        if (cx - PADDING_IZQUIERDO < offX) {
            offX = cx - PADDING_IZQUIERDO;
        } else if (cx + ancho + PADDING_IZQUIERDO > offX + visibleW) {
            offX = cx + ancho + PADDING_IZQUIERDO - visibleW;
        }

        if (maxOffY > 0) scroll.setVvalue(Math.max(0, Math.min(1, offY / maxOffY)));
        if (maxOffX > 0) scroll.setHvalue(Math.max(0, Math.min(1, offX / maxOffX)));
    }

    // Metricas y utilidades de lineas
    private double medirAltoLinea() {
        if (altoLineaCache > 0) return altoLineaCache;
        Text t = new Text("Wg");
        t.setFont(fuente);
        altoLineaCache = t.getLayoutBounds().getHeight();
        return altoLineaCache;
    }

    private double medirAnchoCaracter() {
        if (anchoCarCache > 0) return anchoCarCache;
        // Se mide una cadena larga y se divide: evita errores de redondeo.
        Text t = new Text("M".repeat(40));
        t.setFont(fuente);
        anchoCarCache = t.getLayoutBounds().getWidth() / 40.0;
        return anchoCarCache;
    }

    /** Numero de linea (base 0) que contiene la posicion dada. */
    private int lineaDe(int pos) {
        int l = 0;
        for (int i = 0; i < pos && i < texto.length(); i++)
            if (texto.charAt(i) == '\n') l++;
        return l;
    }

    private int contarLineas() {
        int l = 1;
        for (int i = 0; i < texto.length(); i++)
            if (texto.charAt(i) == '\n') l++;
        return l;
    }

    /** Indice donde empieza la linea numero {@code linea} (base 0). */
    private int inicioDeLineaNumero(int linea) {
        int ini = 0;
        for (int l = 0; l < linea; l++) {
            int nl = texto.indexOf("\n", ini);
            if (nl < 0) break;
            ini = nl + 1;
        }
        return ini;
    }

    private int inicioLinea(int pos) {
        if (pos <= 0) return 0;
        int i = Math.min(pos, texto.length()) - 1;
        while (i >= 0 && texto.charAt(i) != '\n') i--;
        return i + 1;
    }

    private int finLinea(int pos) {
        int i = pos;
        while (i < texto.length() && texto.charAt(i) != '\n') i++;
        return i;
    }

    private void notificarTexto() {
        for (EscuchaTexto e : escuchasTexto) e.textoCambiado(texto.toString());
    }

    private void notificarCursor() {
        Platform.runLater(this::asegurarCaretVisible);
        if (escuchaCursor != null)
            escuchaCursor.cursorCambiado(getLineaActual(), getColumnaActual());
    }
}