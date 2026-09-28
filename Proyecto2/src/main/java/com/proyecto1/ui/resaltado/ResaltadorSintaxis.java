package com.proyecto1.ui.resaltado;

import com.proyecto1.ui.editor.EditorCodigo;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Escucha los cambios del EditorCodigo, tokeniza el texto con ANTLR en
 * un hilo de fondo, y devuelve al editor la lista de Text ya
 * coloreados para que los muestre.
 */
public class ResaltadorSintaxis {

    private static final Duration RETRASO_DEBOUNCE = Duration.millis(200);

    private static final ExecutorService POOL_HILOS = crearPool();

    private static ExecutorService crearPool() {
        ThreadFactory fabrica = new ThreadFactory() {
            private final AtomicInteger contador = new AtomicInteger(1);
            @Override public Thread newThread(Runnable tarea) {
                Thread hilo = new Thread(tarea, "hilo-resaltado-" + contador.getAndIncrement());
                hilo.setDaemon(true);
                return hilo;
            }
        };
        return Executors.newCachedThreadPool(fabrica);
    }

    private final EditorCodigo editor;
    private final PauseTransition pausaDebounce;
    private Task<List<TramoColoreado>> tareaActual;

    public ResaltadorSintaxis(EditorCodigo editor) {
        this.editor = editor;
        this.pausaDebounce = new PauseTransition(RETRASO_DEBOUNCE);
        this.pausaDebounce.setOnFinished(e -> lanzarResaltado());

        editor.agregarEscuchaTexto(nuevoTexto -> solicitarResaltado());

        solicitarResaltado();
    }

    private void solicitarResaltado() {
        pausaDebounce.stop();
        pausaDebounce.playFromStart();
    }

    private void lanzarResaltado() {
        if (tareaActual != null && tareaActual.isRunning()) {
            tareaActual.cancel();
        }
        String textoActual = editor.getTexto();
        HiloResaltado nuevaTarea = new HiloResaltado(textoActual);
        nuevaTarea.setOnSucceeded(e -> {
            if (!textoActual.equals(editor.getTexto())) return;
            aplicarTramos(textoActual, nuevaTarea.getValue());
        });
        nuevaTarea.setOnFailed(e -> { });
        this.tareaActual = nuevaTarea;
        POOL_HILOS.submit(nuevaTarea);
    }

    private void aplicarTramos(String texto, List<TramoColoreado> tramos) {
        Font fuente = editor.getFuente();
        List<Text> nodos = new ArrayList<>();
        int pos = 0;
        for (TramoColoreado t : tramos) {
            if (t.getInicio() < pos) continue;
            if (t.getInicio() > pos) {
                nodos.add(nodo(texto.substring(pos, t.getInicio()), null, fuente));
            }
            int fin = Math.min(t.getFin(), texto.length());
            nodos.add(nodo(texto.substring(t.getInicio(), fin), t.getClaseCss(), fuente));
            pos = fin;
        }
        if (pos < texto.length()) {
            nodos.add(nodo(texto.substring(pos), null, fuente));
        }
        // El editor aplica los nodos coloreados Y reposiciona caret/seleccion.
        editor.aplicarTramosColoreados(nodos);
    }

    private Text nodo(String contenido, String claseCss, Font fuente) {
        Text t = new Text(contenido);
        t.getStyleClass().add("token-base");
        if (claseCss != null) t.getStyleClass().add(claseCss);
        t.setFont(fuente);
        return t;
    }

    public void detener() {
        pausaDebounce.stop();
        if (tareaActual != null) tareaActual.cancel();
    }
}