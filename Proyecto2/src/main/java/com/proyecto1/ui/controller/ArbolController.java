package com.proyecto1.ui.controller;

import com.proyecto1.ui.modelo.ArchivoUI;
import com.proyecto1.ui.modelo.CarpetaUI;
import com.proyecto1.ui.servicio.GestorArchivos;
import com.proyecto1.ui.util.Notificaciones;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controlador del arbol de trabajo. Modelo "workspace": el TreeView tiene
 * una raiz invisible que contiene N proyectos (cada uno una carpeta del
 * disco). Esto permite tener varios proyectos abiertos al mismo tiempo,
 * agregar nuevos en cualquier momento y cerrar los que ya no se usen.
 */
public class ArbolController {

    public interface EscuchaArbol {
        void archivoAbierto(File archivo);
        void mensajeInfo(String mensaje);
        void mensajeError(String mensaje);
    }

    private static final DataFormat FORMATO_RUTA_ARCHIVO =
            new DataFormat("proyecto1/ruta-archivo");

    private final TreeView<Object> treeView;
    private final EscuchaArbol escucha;

    /** Raiz invisible del workspace. Contiene los proyectos abiertos. */
    private final TreeItem<Object> raizWorkspace;

    public ArbolController(TreeView<Object> treeView, EscuchaArbol escucha) {
        this.treeView = treeView;
        this.escucha = escucha;
        this.raizWorkspace = new TreeItem<>(null);
        this.treeView.setRoot(raizWorkspace);
        this.treeView.setShowRoot(false);
        this.treeView.setCellFactory(v -> new CeldaArbol());
    }

    /** Lista observable con los proyectos abiertos (para bindings de visibilidad). */
    public ObservableList<TreeItem<Object>> getProyectos() {
        return raizWorkspace.getChildren();
    }

    /**
     * Agrega un proyecto (carpeta) al workspace. Si ya estaba abierto, lo
     * selecciona en vez de duplicarlo.
     */
    public void agregarProyecto(File carpetaProyecto) {
        if (carpetaProyecto == null || !carpetaProyecto.isDirectory()) {
            escucha.mensajeError("La ruta seleccionada no es una carpeta valida.");
            return;
        }
        String ruta = carpetaProyecto.getAbsolutePath();
        for (TreeItem<Object> hijo : raizWorkspace.getChildren()) {
            if (hijo.getValue() instanceof CarpetaUI) {
                String existente = ((CarpetaUI) hijo.getValue()).getCarpeta().getAbsolutePath();
                if (existente.equals(ruta)) {
                    escucha.mensajeInfo("El proyecto ya estaba abierto.");
                    treeView.getSelectionModel().select(hijo);
                    return;
                }
            }
        }
        NodoArbol nodoProyecto = new NodoArbol(new CarpetaUI(carpetaProyecto), true);
        nodoProyecto.setExpanded(true);
        raizWorkspace.getChildren().add(nodoProyecto);
        treeView.getSelectionModel().select(nodoProyecto);
        escucha.mensajeInfo("Proyecto agregado: " + ruta);
    }

    /** Quita un proyecto del workspace (no borra nada del disco). */
    public void quitarProyecto(File carpetaProyecto) {
        String ruta = carpetaProyecto.getAbsolutePath();
        for (TreeItem<Object> hijo : new ArrayList<>(raizWorkspace.getChildren())) {
            if (hijo.getValue() instanceof CarpetaUI) {
                String existente = ((CarpetaUI) hijo.getValue()).getCarpeta().getAbsolutePath();
                if (existente.equals(ruta)) {
                    raizWorkspace.getChildren().remove(hijo);
                    escucha.mensajeInfo("Proyecto cerrado: " + carpetaProyecto.getName());
                    return;
                }
            }
        }
    }

    /**
     * @return la carpeta del proyecto seleccionado (o el primero del
     *         workspace si el seleccionado no pertenece a ninguno), o
     *         {@code null} si no hay proyectos abiertos
     */
    public File getProyectoSeleccionado() {
        TreeItem<Object> sel = treeView.getSelectionModel().getSelectedItem();
        TreeItem<Object> actual = sel;
        while (actual != null && actual != raizWorkspace) {
            if (actual.getParent() == raizWorkspace && actual.getValue() instanceof CarpetaUI) {
                return ((CarpetaUI) actual.getValue()).getCarpeta();
            }
            actual = actual.getParent();
        }
        for (TreeItem<Object> hijo : raizWorkspace.getChildren()) {
            if (hijo.getValue() instanceof CarpetaUI) {
                return ((CarpetaUI) hijo.getValue()).getCarpeta();
            }
        }
        return null;
    }

    /**
     * @return la carpeta raiz del proyecto abierto que contiene a {@code archivo}, o
     *         {@code null} si el archivo no pertenece a ningun proyecto del workspace
     */
    public File getProyectoQueContiene(File archivo) {
        if (archivo == null) return null;
        java.nio.file.Path ruta = archivo.toPath().toAbsolutePath().normalize();
        File mejor = null;
        int mejorLongitud = -1;
        for (TreeItem<Object> hijo : raizWorkspace.getChildren()) {
            if (!(hijo.getValue() instanceof CarpetaUI)) continue;
            File carpeta = ((CarpetaUI) hijo.getValue()).getCarpeta();
            java.nio.file.Path raiz = carpeta.toPath().toAbsolutePath().normalize();
            // Si hay proyectos anidados gana el mas especifico (la ruta mas larga).
            if (ruta.startsWith(raiz) && raiz.getNameCount() > mejorLongitud) {
                mejor = carpeta;
                mejorLongitud = raiz.getNameCount();
            }
        }
        return mejor;
    }

    /** Refresca todos los proyectos releyendo el disco. */
    public void refrescarTodo() {
        for (TreeItem<Object> hijo : raizWorkspace.getChildren()) {
            if (hijo instanceof NodoArbol) {
                ((NodoArbol) hijo).recargar();
            }
        }
        escucha.mensajeInfo("Arbol de trabajo actualizado.");
    }

    public Optional<File> getArchivoSeleccionado() {
        TreeItem<Object> sel = treeView.getSelectionModel().getSelectedItem();
        if (sel != null && sel.getValue() instanceof ArchivoUI) {
            return Optional.of(((ArchivoUI) sel.getValue()).getArchivo());
        }
        return Optional.empty();
    }

    // ======================================================================
    // NODO DEL ARBOL
    // ======================================================================

    private class NodoArbol extends TreeItem<Object> {

        private boolean cargado = false;
        private final boolean esRaizProyecto;

        NodoArbol(Object valor) { this(valor, false); }

        NodoArbol(Object valor, boolean esRaizProyecto) {
            super(valor);
            this.esRaizProyecto = esRaizProyecto;
        }

        boolean isRaizProyecto() { return esRaizProyecto; }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            if (!cargado) {
                cargado = true;
                super.getChildren().setAll(construirHijos());
            }
            return super.getChildren();
        }

        @Override
        public boolean isLeaf() {
            return getValue() instanceof ArchivoUI;
        }

        void recargar() {
            cargado = false;
            super.getChildren().clear();
            if (isExpanded()) getChildren();
        }

        private List<TreeItem<Object>> construirHijos() {
            List<TreeItem<Object>> res = new ArrayList<>();
            Object valor = getValue();
            if (!(valor instanceof CarpetaUI)) return res;
            File carpeta = ((CarpetaUI) valor).getCarpeta();
            for (File hijo : GestorArchivos.listarHijos(carpeta)) {
                if (hijo.isDirectory()) {
                    res.add(new NodoArbol(new CarpetaUI(hijo)));
                } else {
                    res.add(new NodoArbol(new ArchivoUI(hijo)));
                }
            }
            return res;
        }
    }

    // ======================================================================
    // CELDA
    // ======================================================================

    private class CeldaArbol extends TreeCell<Object> {

        CeldaArbol() {
            configurarDobleClic();
            configurarArrastre();
        }

        @Override
        protected void updateItem(Object valor, boolean vacio) {
            super.updateItem(valor, vacio);
            if (vacio || valor == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            if (valor instanceof ArchivoUI) {
                ArchivoUI a = (ArchivoUI) valor;
                setText(a.getNombreParaMostrar());
                setContextMenu(crearMenuArchivo(a));
            } else if (valor instanceof CarpetaUI) {
                CarpetaUI c = (CarpetaUI) valor;
                setText(c.getNombre());
                boolean esRaiz = getTreeItem() instanceof NodoArbol
                        && ((NodoArbol) getTreeItem()).isRaizProyecto();
                setContextMenu(crearMenuCarpeta(c, esRaiz));
            } else {
                setText(String.valueOf(valor));
                setContextMenu(null);
            }
        }

        private void configurarDobleClic() {
            setOnMouseClicked((MouseEvent e) -> {
                if (e.getButton() == MouseButton.PRIMARY
                        && e.getClickCount() == 2
                        && !isEmpty()
                        && getItem() instanceof ArchivoUI) {
                    escucha.archivoAbierto(((ArchivoUI) getItem()).getArchivo());
                }
            });
        }

        private void configurarArrastre() {
            setOnDragDetected(e -> {
                if (isEmpty() || getItem() == null) return;
                File origen = obtenerFile(getItem());
                if (origen == null) return;
                Dragboard tablero = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent contenido = new ClipboardContent();
                contenido.put(FORMATO_RUTA_ARCHIVO, origen.getAbsolutePath());
                tablero.setContent(contenido);
                e.consume();
            });

            setOnDragOver(e -> {
                if (e.getGestureSource() != this
                        && e.getDragboard().hasContent(FORMATO_RUTA_ARCHIVO)
                        && getItem() instanceof CarpetaUI) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });

            setOnDragDropped(e -> {
                Dragboard tablero = e.getDragboard();
                boolean exito = false;
                if (tablero.hasContent(FORMATO_RUTA_ARCHIVO) && getItem() instanceof CarpetaUI) {
                    File origen = new File((String) tablero.getContent(FORMATO_RUTA_ARCHIVO));
                    File destino = ((CarpetaUI) getItem()).getCarpeta();
                    try {
                        GestorArchivos.mover(origen, destino);
                        escucha.mensajeInfo("Movido \"" + origen.getName()
                                + "\" a \"" + destino.getName() + "\".");
                        refrescarTodo();
                        exito = true;
                    } catch (IOException ex) {
                        escucha.mensajeError("No se pudo mover: " + ex.getMessage());
                        Notificaciones.mostrarError("Mover archivo", ex.getMessage());
                    }
                }
                e.setDropCompleted(exito);
                e.consume();
            });
        }

        private File obtenerFile(Object valor) {
            if (valor instanceof ArchivoUI) return ((ArchivoUI) valor).getArchivo();
            if (valor instanceof CarpetaUI) return ((CarpetaUI) valor).getCarpeta();
            return null;
        }

        private ContextMenu crearMenuArchivo(ArchivoUI a) {
            MenuItem abrir = new MenuItem("Abrir");
            abrir.setOnAction(e -> escucha.archivoAbierto(a.getArchivo()));

            MenuItem renombrar = new MenuItem("Renombrar");
            renombrar.setOnAction(e -> renombrarElemento(a.getArchivo()));

            MenuItem eliminar = new MenuItem("Eliminar");
            eliminar.setOnAction(e -> eliminarElemento(a.getArchivo()));

            MenuItem duplicar = new MenuItem("Duplicar");
            duplicar.setOnAction(e -> duplicarArchivo(a.getArchivo()));

            return new ContextMenu(abrir, renombrar, eliminar, duplicar);
        }

        private ContextMenu crearMenuCarpeta(CarpetaUI c, boolean esRaizProyecto) {
            MenuItem nuevoArchivo = new MenuItem("Nuevo archivo");
            nuevoArchivo.setOnAction(e -> crearArchivoEnCarpeta(c.getCarpeta()));

            MenuItem nuevaCarpeta = new MenuItem("Nueva carpeta");
            nuevaCarpeta.setOnAction(e -> crearSubcarpeta(c.getCarpeta()));

            MenuItem renombrar = new MenuItem("Renombrar");
            renombrar.setOnAction(e -> renombrarElemento(c.getCarpeta()));

            MenuItem eliminar = new MenuItem("Eliminar");
            eliminar.setOnAction(e -> eliminarElemento(c.getCarpeta()));

            MenuItem refrescar = new MenuItem("Refrescar");
            refrescar.setOnAction(e -> refrescarTodo());

            ContextMenu menu = new ContextMenu(
                    nuevoArchivo, nuevaCarpeta,
                    new SeparatorMenuItem(),
                    renombrar, eliminar, refrescar);

            // Si es la raiz de un proyecto, anadimos la opcion de cerrarlo.
            if (esRaizProyecto) {
                MenuItem cerrar = new MenuItem("Cerrar proyecto");
                cerrar.setOnAction(e -> quitarProyecto(c.getCarpeta()));
                menu.getItems().add(new SeparatorMenuItem());
                menu.getItems().add(cerrar);
            }
            return menu;
        }

        private void crearArchivoEnCarpeta(File carpeta) {
            Optional<String> nombre = Notificaciones.pedirTexto(
                    "Nuevo archivo",
                    "Nombre del nuevo archivo (incluya extension .y, .z o .pig):",
                    "nuevo.y");
            nombre.ifPresent(n -> {
                try {
                    GestorArchivos.crearArchivo(new File(carpeta, n), "");
                    escucha.mensajeInfo("Archivo creado: " + n);
                    refrescarTodo();
                } catch (IOException ex) {
                    escucha.mensajeError("No se pudo crear: " + ex.getMessage());
                    Notificaciones.mostrarError("Nuevo archivo", ex.getMessage());
                }
            });
        }

        private void crearSubcarpeta(File padre) {
            Optional<String> nombre = Notificaciones.pedirTexto(
                    "Nueva carpeta", "Nombre de la nueva carpeta:", "nueva_carpeta");
            nombre.ifPresent(n -> {
                try {
                    GestorArchivos.crearCarpeta(new File(padre, n));
                    escucha.mensajeInfo("Carpeta creada: " + n);
                    refrescarTodo();
                } catch (IOException ex) {
                    escucha.mensajeError("No se pudo crear: " + ex.getMessage());
                    Notificaciones.mostrarError("Nueva carpeta", ex.getMessage());
                }
            });
        }

        private void renombrarElemento(File elemento) {
            Optional<String> nuevoNombre = Notificaciones.pedirTexto(
                    "Renombrar",
                    "Nuevo nombre para \"" + elemento.getName() + "\":",
                    elemento.getName());
            nuevoNombre.ifPresent(n -> {
                try {
                    GestorArchivos.renombrar(elemento, n);
                    escucha.mensajeInfo("Renombrado \"" + elemento.getName() + "\" a \"" + n + "\".");
                    refrescarTodo();
                } catch (IOException ex) {
                    escucha.mensajeError("No se pudo renombrar: " + ex.getMessage());
                    Notificaciones.mostrarError("Renombrar", ex.getMessage());
                }
            });
        }

        private void eliminarElemento(File elemento) {
            boolean ok = Notificaciones.confirmar(
                    "Eliminar",
                    "Seguro que desea eliminar \"" + elemento.getName() + "\"? "
                            + "Esta accion no se puede deshacer.");
            if (!ok) return;
            try {
                GestorArchivos.eliminar(elemento);
                escucha.mensajeInfo("Eliminado: " + elemento.getName());
                refrescarTodo();
            } catch (IOException ex) {
                escucha.mensajeError("No se pudo eliminar: " + ex.getMessage());
                Notificaciones.mostrarError("Eliminar", ex.getMessage());
            }
        }

        private void duplicarArchivo(File archivo) {
            try {
                File copia = GestorArchivos.duplicar(archivo);
                escucha.mensajeInfo("Archivo duplicado como: " + copia.getName());
                refrescarTodo();
            } catch (IOException ex) {
                escucha.mensajeError("No se pudo duplicar: " + ex.getMessage());
                Notificaciones.mostrarError("Duplicar", ex.getMessage());
            }
        }
    }
}