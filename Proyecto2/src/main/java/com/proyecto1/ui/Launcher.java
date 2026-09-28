package com.proyecto1.ui;

/**
 * Punto de entrada del JAR ejecutable.
 *
 * <p>No extiende {@code javafx.application.Application} a propósito: si la clase
 * con {@code main()} extiende Application, la JVM exige que JavaFX esté en el
 * module-path y lanza "JavaFX runtime components are missing" al ejecutar el JAR
 * con {@code -jar}. Al delegar desde una clase normal, JavaFX se carga desde el
 * classpath (incluido en el fat JAR) sin problemas.
 */
public final class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}