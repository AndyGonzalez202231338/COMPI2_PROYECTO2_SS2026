package com.proyecto1.ui.controller;

import com.proyecto1.ui.editor.EditorCodigo;
import com.proyecto1.ui.modelo.ArchivoUI;
import com.proyecto1.ui.resaltado.ResaltadorSintaxis;
import com.proyecto1.ui.servicio.GestorArchivos;
import com.proyecto1.ui.util.Notificaciones;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class EditorController {

    public interface EscuchaEditor {
        void mensajeInfo(String mensaje);
        void mensajeError(String mensaje);
        void estadoCambiado(EditorController editor);
    }

    // =================================================================
    // Campos inyectados por el FXML (nombre EXACTO al fx:id del FXML)
    // =================================================================
    @FXML private Text textoNumeros;         // ← es Text, no TextArea
    @FXML private EditorCodigo editorCodigo;
    @FXML private ScrollPane scrollEditor;
    @FXML private Pane panelGutter;

    private ArchivoUI archivoUI;
    private EscuchaEditor escucha;
    private Tab tabAsociada;
    private boolean modificado = false;
    private boolean cargandoContenido = false;
    private ResaltadorSintaxis resaltador;

    // =================================================================
    // Fabrica
    // =================================================================

    public static EditorController crearPestana(File archivo, TabPane tabPane, EscuchaEditor escucha)
            throws IOException {
        URL urlFxml = Objects.requireNonNull(
                EditorController.class.getResource("/com/proyecto1/ui/editor.fxml"),
                "No se encontro editor.fxml");
        FXMLLoader cargador = new FXMLLoader(urlFxml);
        Parent contenido = cargador.load();
        EditorController controlador = cargador.getController();

        controlador.escucha = escucha;
        controlador.archivoUI = new ArchivoUI(archivo);
        controlador.cargarContenidoDesdeDisco();

        Tab tab = new Tab(controlador.archivoUI.getNombre());
        tab.setContent(contenido);
        tab.setUserData(controlador);
        controlador.tabAsociada = tab;
        tab.setOnCloseRequest(evento -> controlador.alIntentarCerrar(evento));
        tab.setOnClosed(evento -> controlador.resaltador.detener());

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        Platform.runLater(controlador.editorCodigo::requestFocus);
        return controlador;
    }

    public static EditorController desdeTab(Tab tab) {
        if (tab != null && tab.getUserData() instanceof EditorController)
            return (EditorController) tab.getUserData();
        return null;
    }

    // =================================================================
    // Inicializacion
    // =================================================================

    @FXML
    private void initialize() {
        // Fuente compartida por gutter y editor.
        Font fuenteEditor = Font.font("Monospaced", 13);
        textoNumeros.setFont(fuenteEditor);
        // Un Text usa por defecto el BASELINE como origen: la primera linea
        // quedaba dibujada por encima del Pane (por eso se veia "2" primero).
        // Ademas un Pane ignora el padding CSS, asi que se posiciona a mano.
        textoNumeros.setTextOrigin(VPos.TOP);
        textoNumeros.setLayoutX(8);
        textoNumeros.setLayoutY(EditorCodigo.PADDING_SUPERIOR);
        editorCodigo.setFuente(fuenteEditor);

        // ScrollPane compartido: el editor lo usa para seguir al cursor.
        editorCodigo.setScrollPane(scrollEditor);
        scrollEditor.setFocusTraversable(false);
        configurarGutterFijo();

        // El editor avisa cuando el texto cambia: refrescar gutter y
        // marcar la pestana como modificada.
        editorCodigo.agregarEscuchaTexto(nuevo -> {
            actualizarNumerosDeLinea(nuevo);
            if (!cargandoContenido) marcarModificado(true);
        });

        // El editor avisa cuando el caret se mueve: actualizar barra de estado.
        editorCodigo.setEscuchaCursor((linea, columna) -> notificarEstadoCambiado());

        // Resaltador: tokeniza y le devuelve al editor los Text coloreados.
        resaltador = new ResaltadorSintaxis(editorCodigo);

        Platform.runLater(editorCodigo::requestFocus);
    }

    /**
     * El gutter scrollea verticalmente junto con el texto (comparten el
     * contenido del ScrollPane), pero en horizontal se compensa con
     * translateX para que los numeros no desaparezcan por la izquierda.
     */
    private void configurarGutterFijo() {
        panelGutter.setViewOrder(-1); // dibujar por encima del editor sin alterar el layout
        Runnable fijar = () -> {
            double anchoContenido = scrollEditor.getContent().getLayoutBounds().getWidth();
            double anchoViewport = scrollEditor.getViewportBounds().getWidth();
            double desplazamiento = scrollEditor.getHvalue() * Math.max(0, anchoContenido - anchoViewport);
            panelGutter.setTranslateX(desplazamiento);
        };
        scrollEditor.hvalueProperty().addListener((o, a, b) -> fijar.run());
        scrollEditor.viewportBoundsProperty().addListener((o, a, b) -> fijar.run());
        scrollEditor.getContent().layoutBoundsProperty().addListener((o, a, b) -> fijar.run());
    }

    // =================================================================
    // Carga / guardado
    // =================================================================

    private void cargarContenidoDesdeDisco() throws IOException {
        String contenido = GestorArchivos.abrirArchivo(archivoUI.getArchivo());
        cargandoContenido = true;
        editorCodigo.setTexto(contenido);
        cargandoContenido = false;
        actualizarNumerosDeLinea(contenido);
        marcarModificado(false);
    }

    public void guardar() throws IOException {
        GestorArchivos.guardarArchivo(archivoUI.getArchivo(), editorCodigo.getTexto());
        marcarModificado(false);
        escucha.mensajeInfo("Archivo guardado: " + archivoUI.getNombre());
    }

    public void guardarComo(File nuevoArchivo) throws IOException {
        GestorArchivos.guardarArchivo(nuevoArchivo, editorCodigo.getTexto());
        this.archivoUI = new ArchivoUI(nuevoArchivo);
        marcarModificado(false);
        escucha.mensajeInfo("Archivo guardado como: " + nuevoArchivo.getName());
    }

    private void alIntentarCerrar(Event evento) {
        if (!modificado) return;
        Notificaciones.RespuestaGuardar respuesta =
                Notificaciones.preguntarGuardarCambios(archivoUI.getNombre());
        switch (respuesta) {
            case GUARDAR:
                try { guardar(); }
                catch (IOException ex) {
                    Notificaciones.mostrarError("Guardar", ex.getMessage());
                    evento.consume();
                }
                break;
            case CANCELAR:
                evento.consume();
                break;
            default:
                break;
        }
    }

    // =================================================================
    // Gutter de numeros de linea (un Text, no un TextArea)
    // =================================================================

    private void actualizarNumerosDeLinea(String texto) {
        int cantidad = texto.isEmpty() ? 1 : texto.split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= cantidad; i++) {
            sb.append(i);
            if (i < cantidad) sb.append("\n");
        }
        textoNumeros.setText(sb.toString());
    }

    // =================================================================
    // Estado
    // =================================================================

    private void marcarModificado(boolean valor) {
        this.modificado = valor;
        if (tabAsociada != null) tabAsociada.setText(archivoUI.getNombreParaMostrar());
        notificarEstadoCambiado();
    }

    private void notificarEstadoCambiado() {
        if (escucha != null) escucha.estadoCambiado(this);
    }

    public ArchivoUI getArchivoUI() { return archivoUI; }
    public boolean isModificado()   { return modificado; }
    public Tab getTab()             { return tabAsociada; }
    public String getContenido()    { return editorCodigo.getTexto(); }
    public EditorCodigo getEditor() { return editorCodigo; }
    public void enfocar()           { editorCodigo.requestFocus(); }

    public int[] getLineaYColumna() {
        return new int[] { editorCodigo.getLineaActual(), editorCodigo.getColumnaActual() };
    }

    // =================================================================
    // Delegados del menu Editar
    // =================================================================

    public void deshacer() {
        Notificaciones.mostrarInformacion("Deshacer",
                "El historial de undo/redo aun no esta implementado.");
    }

    public void rehacer() {
        Notificaciones.mostrarInformacion("Rehacer",
                "El historial de undo/redo aun no esta implementado.");
    }

    public void cortar()          { editorCodigo.fireEvent(atajo(javafx.scene.input.KeyCode.X)); }
    public void copiar()          { editorCodigo.fireEvent(atajo(javafx.scene.input.KeyCode.C)); }
    public void pegar()           { editorCodigo.fireEvent(atajo(javafx.scene.input.KeyCode.V)); }
    public void seleccionarTodo() { editorCodigo.fireEvent(atajo(javafx.scene.input.KeyCode.A)); }

    private javafx.scene.input.KeyEvent atajo(javafx.scene.input.KeyCode code) {
        return new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                code, false, true, false, false);
    }

    public void buscarYReemplazar(String buscar, String reemplazar) {
        if (buscar == null || buscar.isEmpty()) return;
        editorCodigo.setTexto(editorCodigo.getTexto().replace(buscar, reemplazar));
    }

    public boolean buscarSiguiente(String texto) {
        if (texto == null || texto.isEmpty()) return false;
        return editorCodigo.getTexto().contains(texto);
    }
}