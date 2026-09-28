package com.proyecto1.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.util.Optional;

/** Dialogos y alertas estandar de la aplicacion. */
public final class Notificaciones {

    private Notificaciones() {}

    public static void mostrarInformacion(String titulo, String mensaje) {
        Alert a = new Alert(AlertType.INFORMATION);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }

    public static void mostrarAdvertencia(String titulo, String mensaje) {
        Alert a = new Alert(AlertType.WARNING);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }

    public static void mostrarError(String titulo, String mensaje) {
        Alert a = new Alert(AlertType.ERROR);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }

    public static boolean confirmar(String titulo, String mensaje) {
        Alert a = new Alert(AlertType.CONFIRMATION);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    public enum RespuestaGuardar { GUARDAR, DESCARTAR, CANCELAR }

    public static RespuestaGuardar preguntarGuardarCambios(String nombreArchivo) {
        Alert alerta = new Alert(AlertType.CONFIRMATION);
        alerta.setTitle("Cambios sin guardar");
        alerta.setHeaderText("El archivo \"" + nombreArchivo + "\" tiene cambios sin guardar.");
        alerta.setContentText("Desea guardar los cambios antes de continuar?");

        ButtonType bGuardar = new ButtonType("Guardar");
        ButtonType bDescartar = new ButtonType("Descartar");
        ButtonType bCancelar = new ButtonType("Cancelar", ButtonType.CANCEL.getButtonData());
        alerta.getButtonTypes().setAll(bGuardar, bDescartar, bCancelar);

        Optional<ButtonType> r = alerta.showAndWait();
        if (r.isEmpty()) return RespuestaGuardar.CANCELAR;
        if (r.get() == bGuardar) return RespuestaGuardar.GUARDAR;
        if (r.get() == bDescartar) return RespuestaGuardar.DESCARTAR;
        return RespuestaGuardar.CANCELAR;
    }

    public static Optional<String> pedirTexto(String titulo, String mensaje, String valorPorDefecto) {
        TextInputDialog d = new TextInputDialog(valorPorDefecto);
        d.setTitle(titulo);
        d.setHeaderText(null);
        d.setContentText(mensaje);
        return d.showAndWait();
    }

    public static void mostrarFuncionPendiente(Stage propietario) {
        Alert a = new Alert(AlertType.INFORMATION);
        if (propietario != null) a.initOwner(propietario);
        a.setTitle("Funcion pendiente");
        a.setHeaderText(null);
        a.setContentText("Funcion pendiente de implementacion. "
                + "La conexion con el analizador se realizara en una fase posterior.");
        a.showAndWait();
    }
}