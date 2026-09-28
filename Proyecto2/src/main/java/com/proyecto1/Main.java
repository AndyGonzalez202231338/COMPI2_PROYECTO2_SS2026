package com.proyecto1;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class Main {
    public static void main(String[] args) throws Exception {
        String archivo = "/home/andy/Descargas/prueba.pig";

        CharStream input = CharStreams.fromFileName(archivo);

        LenguajeLexer lexer = new LenguajeLexer(input);

        // Pig Latin tampoco es sensible a indentación (igual que
        // Zetariano): NO se usa IndentTokenStream aquí, un
        // CommonTokenStream normal alcanza.
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GramaticaPigLatin parser = new GramaticaPigLatin(tokens);

        // El punto de entrada de Pig Latin es "programa"
        // (igual de nombre que el de Y?, pero es OTRA clase/gramática).
        ParseTree arbol = parser.programa();

        System.out.println(arbol.toStringTree(parser));
    }
}