package com.proyecto1;

import com.proyecto1.codigo.c.OrquestadorC3DaC;
import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.cuadruplas.Cuadrupla;
import com.proyecto1.semantico.ast.y.Programa;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.y.ASTBuilderY;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pipeline completo de Y?: .y -> AST -> semántico -> C3D -> .c
 *
 * Uso:
 *   java com.proyecto1.MainC3DPrueba ruta/al/archivo.y
 * o dejar la ruta quemada abajo.
 */
public class MainC3DPrueba {

    /** Nombre en el AST de la función de entrada de Y (el "main" del lenguaje). */
    private static final String FUNCION_ENTRADA_Y = "principal";

    /** Prefijo para no colisionar con funciones de la libc (printf, malloc, ...). */
    private static final String PREFIJO_C = "y_";

    public static void main(String[] args) throws IOException {

        String ruta = (args.length > 0) ? args[0]
                : "/home/andy/Escritorio/Proyectos pig/Pruebas/prueba1.y";

        // 1) Lexer + parser
        CharStream input = CharStreams.fromFileName(ruta);
        LenguajeLexer lexer = new LenguajeLexer(input);
        IndentTokenStream tokens = new IndentTokenStream(lexer);
        GramaticaY parser = new GramaticaY(tokens);
        parser.removeErrorListeners();
        GramaticaY.ProgramaContext arbol = parser.programa();

        // 2) AST
        ASTBuilderY builder = new ASTBuilderY();
        Programa ast = builder.construir(arbol);

        // 3) Análisis semántico: registra símbolos en el ámbito global y valida.
        //    (Si tu analizador tiene un método de entrada distinto, ajustalo aquí.)
        AmbitoGlobal global = new AmbitoGlobal();
        ManejadorErrores errores = new ManejadorErrores();
        ast.verificar(global, errores);

        if (!errores.tieneErrores()) {
            System.err.println("Errores semánticos:");
            errores.obtenerErrores();
            return; // no seguimos si hay errores
        }

        // 4) C3D con el ámbito ya poblado (clave: tipos reales, no DESCONOCIDO)
        GeneradorC3D gen = new GeneradorC3D(global);
        ast.generarC3D(gen);

        // 5) Imprimir tabla de cuádruplas (útil para depurar)
        System.out.println("=== Cuádruplas generadas ===");
        int i = 0;
        for (Cuadrupla c : gen.getCuadruplas()) {
            System.out.printf("%3d: %s%n", i++, c.toStringLegible());
        }
        System.out.println("=== Fin (" + gen.getCuadruplas().size() + " cuádruplas) ===\n");

        // 6) Orquestar a C
        OrquestadorC3DaC orch = new OrquestadorC3DaC(
                gen.getCuadruplas(),
                gen.getFirmas(),
                PREFIJO_C,
                FUNCION_ENTRADA_Y,
                global.getTiposDefinidos()   // <-- structs
        );
        String codigoC = orch.generarArchivoCompleto();

        // 7) Escribir el .c al lado del .y (mismo nombre, extensión .c)
        Path salida = Path.of(ruta.replaceAll("\\.y$", ".c"));
        Files.writeString(salida, codigoC);
        System.out.println("Archivo C generado: " + salida.toAbsolutePath());
    }
}