package com.proyecto1.ui.controller;

import com.proyecto1.semantico.errores.ErrorSemantico;
import com.proyecto1.servicio.ResultadoAnalisis;
import com.proyecto1.servicio.ServicioAnalisis;
import com.proyecto1.ui.modelo.ArchivoUI;
import com.proyecto1.ui.modelo.FilaError;
import com.proyecto1.ui.servicio.GestorArchivos;
import com.proyecto1.ui.util.Notificaciones;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Controlador principal del IDE. Coordina arbol, editor, consola y menus. */
public class MainController implements ArbolController.EscuchaArbol, EditorController.EscuchaEditor {

    @FXML private BorderPane raizPrincipal;
    @FXML private MenuBar barraMenu;
    @FXML private ToolBar barraHerramientas;

    @FXML private CheckMenuItem miMostrarArbol;
    @FXML private CheckMenuItem miMostrarPanelInferior;

    @FXML private TitledPane panelArbol;
    @FXML private TreeView<Object> arbolTrabajo;
    @FXML private VBox panelArbolVacio;

    @FXML private TabPane panelPestanas;
    @FXML private VBox panelBienvenida;

    @FXML private SplitPane splitCentral;

    /** Panel inferior tipo "consola de IDE": pestana Consola + pestana Errores (tabla). */
    @FXML private TabPane panelInferior;
    @FXML private Tab tabConsola;
    @FXML private Tab tabErrores;
    @FXML private TextArea consolaSalida;

    @FXML private TableView<FilaError> tablaErrores;
    @FXML private TableColumn<FilaError, FilaError.Tipo> colTipo;
    @FXML private TableColumn<FilaError, Integer> colFila;
    @FXML private TableColumn<FilaError, Integer> colColumna;
    @FXML private TableColumn<FilaError, String> colDescripcion;
    private final ObservableList<FilaError> filasErrores = FXCollections.observableArrayList();

    @FXML private HBox barraEstado;
    @FXML private Label lblRutaActiva;
    @FXML private Label lblPosicionCursor;
    @FXML private Label lblLenguajeDetectado;
    @FXML private Label lblEstadoModificado;

    @FXML private Button btnNuevoArchivo;
    @FXML private Button btnAbrirArchivo;
    @FXML private Button btnGuardar;
    @FXML private Button btnGuardarComo;
    @FXML private Button btnDeshacer;
    @FXML private Button btnRehacer;
    @FXML private Button btnBuscar;
    @FXML private Button btnAnalizar;
    @FXML private Button btnEjecutar;

    private ArbolController arbolController;

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final FileChooser.ExtensionFilter FILTRO_LENGUAJES =
            new FileChooser.ExtensionFilter("Archivos soportados (*.y, *.z, *.pig)", "*.y", "*.z", "*.pig");
    private static final FileChooser.ExtensionFilter FILTRO_TODOS =
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*");

    @FXML
    private void initialize() {
        arbolController = new ArbolController(arbolTrabajo, this);

        // Al cambiar de pestana, actualizar barra de estado y dar foco
        // al editor de la pestana seleccionada. EditorCodigo ya gestiona
        // su propio caret/seleccion internamente; solo hay que pedirle
        // el foco para que empiece a recibir teclado.
        panelPestanas.getSelectionModel().selectedItemProperty()
                .addListener((obs, vieja, nueva) -> {
                    actualizarBarraEstado(EditorController.desdeTab(nueva));
                    EditorController ed = EditorController.desdeTab(nueva);
                    if (ed != null) {
                        Platform.runLater(ed::enfocar);
                    }
                });

        // El overlay de bienvenida se ve cuando no hay pestanas abiertas.
        panelBienvenida.visibleProperty().bind(Bindings.isEmpty(panelPestanas.getTabs()));
        panelBienvenida.managedProperty().bind(Bindings.isEmpty(panelPestanas.getTabs()));

        // El overlay del arbol se ve cuando no hay NINGUN proyecto abierto.
        panelArbolVacio.visibleProperty().bind(Bindings.isEmpty(arbolController.getProyectos()));
        panelArbolVacio.managedProperty().bind(Bindings.isEmpty(arbolController.getProyectos()));

        configurarMenuWorkspace();
        configurarTablaErrores();

        actualizarBarraEstado(null);
        escribirInfo("IDE iniciado. Cree un proyecto nuevo o abra una carpeta para comenzar.");
    }

    /**
     * Menu contextual del arbol cuando se hace clic derecho sobre el FONDO.
     * Las celdas tienen su propio menu contextual definido en ArbolController.
     */
    private void configurarMenuWorkspace() {
        MenuItem itemNuevoProyecto = new MenuItem("Nuevo proyecto...");
        itemNuevoProyecto.setOnAction(e -> accionNuevoProyecto());

        MenuItem itemAbrirCarpeta = new MenuItem("Abrir carpeta...");
        itemAbrirCarpeta.setOnAction(e -> accionAbrirCarpeta());

        ContextMenu menu = new ContextMenu(
                itemNuevoProyecto,
                new SeparatorMenuItem(),
                itemAbrirCarpeta);

        arbolTrabajo.setContextMenu(menu);
    }

    // ==================== MENU VER ====================

    @FXML private void accionAlternarArbol() {
        boolean v = miMostrarArbol.isSelected();
        panelArbol.setVisible(v); panelArbol.setManaged(v);
    }
    @FXML private void accionAlternarPanelInferior() {
        mostrarPanelInferior(miMostrarPanelInferior.isSelected());
    }

    /**
     * Muestra u oculta el panel inferior (consola + errores). Se quita/agrega del SplitPane
     * en vez de solo ocultarlo: asi el editor recupera TODO el alto cuando esta oculto.
     */
    private void mostrarPanelInferior(boolean visible) {
        miMostrarPanelInferior.setSelected(visible);
        boolean estaEnSplit = splitCentral.getItems().contains(panelInferior);
        if (visible && !estaEnSplit) {
            splitCentral.getItems().add(panelInferior);
            splitCentral.setDividerPositions(0.66);
        } else if (!visible && estaEnSplit) {
            splitCentral.getItems().remove(panelInferior);
        }
    }

    // ==================== TABLA DE ERRORES ====================

    private void configurarTablaErrores() {
        tablaErrores.setItems(filasErrores);

        colTipo.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getTipo()));
        colTipo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(FilaError.Tipo tipo, boolean vacia) {
                super.updateItem(tipo, vacia);
                getStyleClass().removeAll("tipo-lexico", "tipo-sintactico", "tipo-semantico", "tipo-advertencia");
                if (vacia || tipo == null) {
                    setText(null);
                } else {
                    setText(tipo.getEtiqueta());
                    getStyleClass().add("tipo-" + tipo.name().toLowerCase());
                }
            }
        });

        colFila.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getFila()));
        colFila.setCellFactory(col -> celdaNumerica());
        colColumna.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getColumna()));
        colColumna.setCellFactory(col -> celdaNumerica());

        // La descripcion NO se corta con "...": se parte en varias lineas segun el ancho actual de
        // la columna (y la fila crece), asi el mensaje completo se ve aunque la ventana sea angosta.
        colDescripcion.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getDescripcion()));
        colDescripcion.setCellFactory(col -> new TableCell<>() {
            private final Text texto = new Text();
            {
                texto.getStyleClass().add("texto-celda-error");
                texto.wrappingWidthProperty().bind(col.widthProperty().subtract(20));
                setPrefHeight(Region.USE_COMPUTED_SIZE);
            }
            @Override
            protected void updateItem(String descripcion, boolean vacia) {
                super.updateItem(descripcion, vacia);
                if (vacia || descripcion == null) {
                    setGraphic(null);
                } else {
                    texto.setText(descripcion);
                    setGraphic(texto);
                }
            }
        });
    }

    /** Muestra el numero, o "-" si el error no tiene posicion (valor <= 0). */
    private TableCell<FilaError, Integer> celdaNumerica() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Integer valor, boolean vacia) {
                super.updateItem(valor, vacia);
                setText(vacia || valor == null ? null : (valor > 0 ? String.valueOf(valor) : "-"));
            }
        };
    }

    // ==================== MENU EJECUTAR (placeholders) ====================

    @FXML private void accionAnalizarArchivoActual() {
        EditorController editor = obtenerEditorActivo();
        if (editor == null) {
            escribirAdvertencia("No hay ningun archivo abierto para analizar.");
            return;
        }

        File archivo = editor.getArchivoUI().getArchivo();
        String texto = editor.getContenido();
        // Raiz del proyecto que contiene el archivo: ahi tambien se buscan los .y/.z que importa un .pig.
        File raizProyecto = arbolController.getProyectoQueContiene(archivo);
        editor.getEditor().limpiarMarcasDeError();
        escribirInfo("Analizando " + archivo.getName() + "...");

        Task<ResultadoAnalisis> tarea = new Task<>() {
            @Override
            protected ResultadoAnalisis call() {
                return new ServicioAnalisis(raizProyecto).analizar(archivo, texto);
            }
        };
        tarea.setOnSucceeded(evento -> mostrarResultadoAnalisis(editor, tarea.getValue()));
        tarea.setOnFailed(evento -> {
            Throwable causa = tarea.getException();
            String mensaje = causa != null && causa.getMessage() != null ? causa.getMessage() : "desconocido";
            escribirError("Error interno al analizar: " + mensaje);
        });

        Thread hilo = new Thread(tarea, "analisis-" + archivo.getName());
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Vuelca un {@link ResultadoAnalisis} en la consola, la tabla de errores y las marcas del editor. Corre en el hilo de JavaFX. */
    private void mostrarResultadoAnalisis(EditorController editor, ResultadoAnalisis resultado) {
        filasErrores.clear();
        List<Integer> lineasConError = new ArrayList<>();

        if (resultado.isExito()) {
            escribirInfo("[OK] " + resultado.getMensajeResumen());
        } else {
            escribirError("[ERROR] " + resultado.getMensajeResumen());
        }

        agregarErroresALaTabla(resultado.getErroresLexicos(), FilaError.Tipo.LEXICO, lineasConError);
        agregarErroresALaTabla(resultado.getErroresSintacticos(), FilaError.Tipo.SINTACTICO, lineasConError);
        agregarErroresALaTabla(resultado.getErroresSemanticos(), FilaError.Tipo.SEMANTICO, lineasConError);

        for (ErrorSemantico advertencia : resultado.getAdvertencias()) {
            FilaError fila = FilaError.desde(FilaError.Tipo.ADVERTENCIA,
                    advertencia.getLinea(), advertencia.getColumna(), advertencia.getMensaje());
            filasErrores.add(fila);
            escribirAdvertencia("linea " + fila.getFila() + ":" + fila.getColumna() + " - " + fila.getDescripcion());
        }

        if (!lineasConError.isEmpty()) {
            editor.getEditor().marcarLineasConError(lineasConError);
        }

        // La pestana Errores lleva la cuenta; si hay algo que ver, se abre sola (y se muestra el
        // panel inferior aunque estuviera oculto). Si todo salio bien, se deja la Consola a la vista.
        tabErrores.setText(filasErrores.isEmpty() ? "Errores" : "Errores (" + filasErrores.size() + ")");
        if (!filasErrores.isEmpty()) {
            mostrarPanelInferior(true);
            panelInferior.getSelectionModel().select(tabErrores);
        }
    }

    private void agregarErroresALaTabla(List<ErrorSemantico> errores, FilaError.Tipo tipo, List<Integer> lineasConError) {
        for (ErrorSemantico error : errores) {
            FilaError fila = FilaError.desde(tipo, error.getLinea(), error.getColumna(), error.getMensaje());
            filasErrores.add(fila);
            escribirError("[" + tipo.getEtiqueta() + "] linea " + fila.getFila() + ":" + fila.getColumna()
                    + " - " + fila.getDescripcion());
            lineasConError.add(error.getLinea());
        }
    }
    @FXML private void accionCompilarProyecto() {
        escribirAdvertencia("Compilar: pendiente de conexion con el backend.");
        Notificaciones.mostrarFuncionPendiente(obtenerVentana());
    }
    @FXML private void accionEjecutarUltimoAnalisis() {
        escribirAdvertencia("Ejecutar: pendiente de conexion con el backend.");
        Notificaciones.mostrarFuncionPendiente(obtenerVentana());
    }

    // ==================== MENU AYUDA ====================

    @FXML private void accionAcercaDe() {
        Notificaciones.mostrarInformacion("Acerca de",
                "Compilador - Proyecto 1\n"
                        + "IDE para los lenguajes Y?, Zetariano y PigLatin.\n"
                        + "Proyecto de Compiladores 2.");
    }
    @FXML private void accionManualUsuario() {
        Notificaciones.mostrarInformacion("Manual de usuario",
                "Manual pendiente de redaccion.");
    }

    @FXML private void accionSalir() {
        Stage v = obtenerVentana();
        if (v == null) return;
        if (cerrarTodasLasPestanas()) v.close();
    }

    // ==================== MENU ARCHIVO ====================

    @FXML private void accionNuevoArchivo() {
        FileChooser d = new FileChooser();
        d.setTitle("Nuevo archivo");
        d.setInitialDirectory(carpetaInicial());
        d.getExtensionFilters().setAll(FILTRO_LENGUAJES, FILTRO_TODOS);
        d.setInitialFileName("nuevo.y");
        File archivo = d.showSaveDialog(obtenerVentana());
        if (archivo == null) return;
        try {
            GestorArchivos.crearArchivo(archivo, "");
            escribirInfo("Archivo creado: " + archivo.getAbsolutePath());
            arbolController.refrescarTodo();
            abrirArchivoEnEditor(archivo);
        } catch (IOException ex) {
            escribirError("No se pudo crear: " + ex.getMessage());
            Notificaciones.mostrarError("Nuevo archivo", ex.getMessage());
        }
    }

    @FXML
    private void accionNuevoProyecto() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Elegir ubicacion para el nuevo proyecto");
        dc.setInitialDirectory(carpetaInicial());
        File padre = dc.showDialog(obtenerVentana());
        if (padre == null) return;

        Optional<String> nombre = Notificaciones.pedirTexto(
                "Nuevo proyecto",
                "Nombre de la carpeta del nuevo proyecto:",
                "proyecto_nuevo");
        nombre.ifPresent(n -> {
            File nueva = new File(padre, n);
            try {
                GestorArchivos.crearCarpeta(nueva);
                escribirInfo("Proyecto creado: " + nueva.getAbsolutePath());
                agregarProyecto(nueva);
            } catch (IOException ex) {
                escribirError("No se pudo crear: " + ex.getMessage());
                Notificaciones.mostrarError("Nuevo proyecto", ex.getMessage());
            }
        });
    }

    @FXML private void accionAbrirArchivo() {
        FileChooser d = new FileChooser();
        d.setTitle("Abrir archivo");
        d.setInitialDirectory(carpetaInicial());
        d.getExtensionFilters().setAll(FILTRO_LENGUAJES, FILTRO_TODOS);
        File archivo = d.showOpenDialog(obtenerVentana());
        if (archivo != null) abrirArchivoEnEditor(archivo);
    }

    @FXML
    private void accionAbrirCarpeta() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Abrir carpeta de proyecto");
        dc.setInitialDirectory(carpetaInicial());
        File carpeta = dc.showDialog(obtenerVentana());
        if (carpeta != null) agregarProyecto(carpeta);
    }

    /** Agrega una carpeta como proyecto al workspace (no reemplaza los existentes). */
    private void agregarProyecto(File carpeta) {
        arbolController.agregarProyecto(carpeta);
    }

    @FXML private void accionGuardar() {
        EditorController ed = obtenerEditorActivo();
        if (ed == null) { escribirAdvertencia("No hay archivo abierto."); return; }
        try { ed.guardar(); }
        catch (IOException ex) {
            escribirError("No se pudo guardar: " + ex.getMessage());
            Notificaciones.mostrarError("Guardar", ex.getMessage());
        }
    }

    @FXML private void accionGuardarComo() {
        EditorController ed = obtenerEditorActivo();
        if (ed == null) { escribirAdvertencia("No hay archivo abierto."); return; }
        FileChooser d = new FileChooser();
        d.setTitle("Guardar como");
        d.setInitialDirectory(carpetaInicial());
        d.setInitialFileName(ed.getArchivoUI().getNombre());
        d.getExtensionFilters().setAll(FILTRO_LENGUAJES, FILTRO_TODOS);
        File destino = d.showSaveDialog(obtenerVentana());
        if (destino == null) return;
        try { ed.guardarComo(destino); arbolController.refrescarTodo(); }
        catch (IOException ex) {
            escribirError("No se pudo guardar: " + ex.getMessage());
            Notificaciones.mostrarError("Guardar como", ex.getMessage());
        }
    }

    @FXML private void accionCerrarPestanaActual() {
        cerrarPestana(panelPestanas.getSelectionModel().getSelectedItem());
    }

    private void cerrarPestana(Tab tab) {
        if (tab == null) return;
        Event e = new Event(tab, tab, Tab.TAB_CLOSE_REQUEST_EVENT);
        Event.fireEvent(tab, e);
        if (!e.isConsumed()) panelPestanas.getTabs().remove(tab);
    }

    private boolean cerrarTodasLasPestanas() {
        List<Tab> copia = new ArrayList<>(panelPestanas.getTabs());
        for (Tab t : copia) {
            EditorController ed = EditorController.desdeTab(t);
            if (ed != null && ed.isModificado()) {
                Event e = new Event(t, t, Tab.TAB_CLOSE_REQUEST_EVENT);
                Event.fireEvent(t, e);
                if (e.isConsumed()) return false;
            }
        }
        return true;
    }

    // ==================== MENU EDITAR ====================

    @FXML private void accionDeshacer() { EditorController e = obtenerEditorActivo(); if (e != null) e.deshacer(); }
    @FXML private void accionRehacer()  { EditorController e = obtenerEditorActivo(); if (e != null) e.rehacer(); }
    @FXML private void accionCortar()   { EditorController e = obtenerEditorActivo(); if (e != null) e.cortar(); }
    @FXML private void accionCopiar()   { EditorController e = obtenerEditorActivo(); if (e != null) e.copiar(); }
    @FXML private void accionPegar()    { EditorController e = obtenerEditorActivo(); if (e != null) e.pegar(); }
    @FXML private void accionSeleccionarTodo() { EditorController e = obtenerEditorActivo(); if (e != null) e.seleccionarTodo(); }

    @FXML private void accionBuscarReemplazar() {
        EditorController editor = obtenerEditorActivo();
        if (editor == null) {
            escribirAdvertencia("Abra un archivo para buscar o reemplazar.");
            return;
        }
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Buscar / Reemplazar");
        dlg.initOwner(obtenerVentana());
        dlg.initModality(Modality.NONE);

        TextField cBuscar = new TextField(); cBuscar.setPromptText("Texto a buscar");
        TextField cReempl = new TextField(); cReempl.setPromptText("Texto de reemplazo");

        GridPane gp = new GridPane();
        gp.setHgap(8); gp.setVgap(8); gp.setPadding(new Insets(12));
        gp.add(new Label("Buscar:"), 0, 0);          gp.add(cBuscar, 1, 0);
        gp.add(new Label("Reemplazar por:"), 0, 1);  gp.add(cReempl, 1, 1);
        dlg.getDialogPane().setContent(gp);

        ButtonType bSig  = new ButtonType("Buscar siguiente", ButtonBar.ButtonData.OTHER);
        ButtonType bTodo = new ButtonType("Reemplazar todo", ButtonBar.ButtonData.APPLY);
        ButtonType bCerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().setAll(bSig, bTodo, bCerrar);

        Button btnSig = (Button) dlg.getDialogPane().lookupButton(bSig);
        btnSig.addEventFilter(ActionEvent.ACTION, ev -> {
            if (!editor.buscarSiguiente(cBuscar.getText()))
                escribirAdvertencia("Sin coincidencias de \"" + cBuscar.getText() + "\".");
            ev.consume();
        });
        Button btnTodo = (Button) dlg.getDialogPane().lookupButton(bTodo);
        btnTodo.addEventFilter(ActionEvent.ACTION, ev -> {
            editor.buscarYReemplazar(cBuscar.getText(), cReempl.getText());
            escribirInfo("Reemplazo realizado en " + editor.getArchivoUI().getNombre() + ".");
            ev.consume();
        });
        dlg.show();
    }

    // ==================== EscuchaArbol ====================

    @Override public void archivoAbierto(File archivo) { abrirArchivoEnEditor(archivo); }

    private void abrirArchivoEnEditor(File archivo) {
        for (Tab t : panelPestanas.getTabs()) {
            EditorController ed = EditorController.desdeTab(t);
            if (ed != null && ed.getArchivoUI().getArchivo().getAbsolutePath()
                    .equals(archivo.getAbsolutePath())) {
                panelPestanas.getSelectionModel().select(t);
                ed.enfocar();
                return;
            }
        }
        try {
            EditorController.crearPestana(archivo, panelPestanas, this);
            escribirInfo("Archivo abierto: " + archivo.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();   // ← AÑADIR
            escribirError("No se pudo abrir: " + ex.getMessage());
            Notificaciones.mostrarError("Abrir archivo", ex.getMessage());
        }
    }

    // ==================== EscuchaEditor ====================

    @Override public void estadoCambiado(EditorController editor) {
        if (editor != null && editor.getTab() == panelPestanas.getSelectionModel().getSelectedItem())
            actualizarBarraEstado(editor);
    }

    private void actualizarBarraEstado(EditorController editor) {
        if (editor == null) {
            lblRutaActiva.setText("Sin archivo abierto");
            lblPosicionCursor.setText("Linea 1, Columna 1");
            lblLenguajeDetectado.setText("Lenguaje: Desconocido");
            lblEstadoModificado.setText("");
            actualizarTituloVentana(null);
            return;
        }
        ArchivoUI a = editor.getArchivoUI();
        int[] lc = editor.getLineaYColumna();
        lblRutaActiva.setText(a.getRutaAbsoluta());
        lblPosicionCursor.setText("Linea " + lc[0] + ", Columna " + lc[1]);
        lblLenguajeDetectado.setText("Lenguaje: " + a.getLenguaje().getNombreVisible());
        lblEstadoModificado.setText(editor.isModificado() ? "Modificado" : "");
        actualizarTituloVentana(a);
    }

    private void actualizarTituloVentana(ArchivoUI a) {
        Stage v = obtenerVentana();
        if (v == null) return;
        v.setTitle(a == null ? "Compilador - Proyecto 1"
                : "Compilador - Proyecto 1 - " + a.getNombreParaMostrar());
    }

    private EditorController obtenerEditorActivo() {
        return EditorController.desdeTab(panelPestanas.getSelectionModel().getSelectedItem());
    }

    // ==================== Consola ====================

    @Override public void mensajeInfo(String m)  { escribirInfo(m); }
    @Override public void mensajeError(String m) { escribirError(m); }

    public void escribirInfo(String m)        { agregarLinea("INFO", m); }
    public void escribirError(String m)       { agregarLinea("ERROR", m); }
    public void escribirAdvertencia(String m) { agregarLinea("ADVERTENCIA", m); }

    public void limpiar() { if (consolaSalida != null) consolaSalida.clear(); }

    private void agregarLinea(String etiqueta, String mensaje) {
        if (consolaSalida == null) return;
        String hora = LocalTime.now().format(FORMATO_HORA);
        consolaSalida.appendText("[" + hora + "] [" + etiqueta + "] " + mensaje + System.lineSeparator());
    }

    // ==================== Utilidades ====================

    private Stage obtenerVentana() {
        if (raizPrincipal == null || raizPrincipal.getScene() == null) return null;
        return (Stage) raizPrincipal.getScene().getWindow();
    }

    private File carpetaInicial() {
        File proy = arbolController.getProyectoSeleccionado();
        if (proy != null) return proy;
        return new File(System.getProperty("user.home"));
    }
}