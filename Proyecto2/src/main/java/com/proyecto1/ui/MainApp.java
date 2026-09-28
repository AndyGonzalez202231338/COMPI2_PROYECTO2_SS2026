package com.proyecto1.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

/**
 * Clase de arranque de la interfaz grafica del IDE del compilador.
 * Fase actual: solo frontend. El backend se conectara en una fase posterior.
 */
public class MainApp extends Application {

    private static final String TEMA = "estilos.css";

    @Override
    public void start(Stage escenarioPrincipal) throws IOException {
        URL urlFxml = obtenerRecurso("main.fxml");
        FXMLLoader cargador = new FXMLLoader(urlFxml);
        Parent raiz = cargador.load();

        Scene escena = new Scene(raiz, 1200, 800);
        escena.getStylesheets().add(obtenerRecurso(TEMA).toExternalForm());

        escenarioPrincipal.setTitle("Compilador - Proyecto 1");
        escenarioPrincipal.setScene(escena);
        escenarioPrincipal.setMinWidth(900);
        escenarioPrincipal.setMinHeight(600);
        escenarioPrincipal.show();
    }

    private URL obtenerRecurso(String nombreArchivo) {
        return Objects.requireNonNull(
                MainApp.class.getResource(nombreArchivo),
                "No se encontro el recurso: " + nombreArchivo);
    }

    public static void main(String[] args) {
        launch(args);
    }
}