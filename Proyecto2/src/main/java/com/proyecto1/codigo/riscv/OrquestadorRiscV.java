package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.cuadruplas.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Ensambla un archivo RISC-V (.s) completo a partir de las cuadruplas y firmas de un
// programa. Es el equivalente de OrquestadorC3DaC pero generando ensamblador en vez
// de C. Se disena para el emulador RARS/Venus (RV32I).
//
// Estructura del .s que produce:
//
//     .data                 <- seccion de datos (cadenas, por ahora vacia o minima)
//     .text                 <- seccion de codigo
//     .globl main
//     main:                 <- punto de entrada: arranca la funcion "main" o el
//                              equivalente del lenguaje, y al terminar hace exit
//     <cada funcion>:       <- una etiqueta por funcion, con su prologo y epilogo
//
// Division por funcion: la lista de cuadruplas es plana; se recorre detectando
// begin_func ... end_func, igual que hace el backend C.
//
// Manejo del stack (clave del proyecto):
//   - Prologo de cada funcion: baja sp para reservar el frame, guarda fp y ra del
//     llamador, y fija fp a la base del nuevo frame.
//   - Epilogo: restaura fp y ra, sube sp para liberar el frame, y retorna (ret).
//   fp es la base fija: todas las variables/temporales se acceden con offset(fp).
//   sp es el tope movil: se mueve al entrar/salir y al pasar argumentos.
//
// Estado de la subfase 0: el prologo y epilogo se emiten COMPLETOS y correctos (son
// lo mas importante del manejo de memoria). El cuerpo de cada funcion se emite como
// comentarios (ver TraductorCuadruplaRiscV), pendiente de las subfases siguientes.
public final class OrquestadorRiscV {

    private final List<Cuadrupla> cuadruplas;
    private final Map<String, GeneradorC3D.Firma> firmas;

    // Nombre de la funcion de entrada en el C3D:
    //   PigLatin -> "main"
    //   Y / Z   -> null (no tienen un main propio; el .s igual se genera para
    //               inspeccionar sus funciones, pero sin punto de entrada ejecutable)
    private final String nombreFuncionEntrada;

    public OrquestadorRiscV(List<Cuadrupla> cuadruplas,
                            Map<String, GeneradorC3D.Firma> firmas,
                            String nombreFuncionEntrada) {
        this.cuadruplas = (cuadruplas != null) ? cuadruplas : List.of();
        this.firmas = (firmas != null) ? firmas : Map.of();
        this.nombreFuncionEntrada = nombreFuncionEntrada;
    }

    public String generarArchivoCompleto() {
        StringBuilder sb = new StringBuilder();

        sb.append("# Codigo RISC-V (RV32I) generado automaticamente\n");
        sb.append("# Pensado para el emulador RARS/Venus\n\n");

        // Seccion de datos. Por ahora vacia; en la subfase de cadenas se llenara con
        // las literales de texto del programa.
        sb.append(".data\n");
        sb.append("# (sin datos estaticos todavia)\n\n");

        // Seccion de codigo.
        sb.append(".text\n");
        sb.append(".globl main\n\n");

        List<FuncionCompilada> funciones = dividirPorFuncion();

        // Cada funcion con su prologo, cuerpo y epilogo. La funcion de entrada
        // (normalmente "main" en PigLatin) es ademas el punto de arranque del
        // programa: RARS/Venus ejecutan desde la etiqueta "main", asi que no hace
        // falta un wrapper aparte; basta con que esa funcion exista con ese nombre
        // y que su epilogo termine con exit en vez de ret.
        for (FuncionCompilada fc : funciones) {
            boolean esEntrada = fc.begin().nombre().equals(nombreFuncionEntrada);
            emitirFuncion(fc, sb, esEntrada);
        }

        return sb.toString();
    }

    // Emite una funcion completa: etiqueta, prologo, cuerpo (comentarios por ahora)
    // y epilogo.
    private void emitirFuncion(FuncionCompilada fc, StringBuilder sb, boolean esEntrada) {
        String nombre = fc.begin().nombre();

        // Nombres de los parametros, en orden, para colocarlos en los primeros slots.
        // Se toman de la firma si existe; si no, se generan como p0, p1, ... segun nArgs.
        List<String> params = nombresParametros(nombre, fc.begin().nArgs());

        FrameFuncion frame = FrameFuncion.construir(nombre, params, fc.cuerpo());
        TraductorCuadruplaRiscV traductor = new TraductorCuadruplaRiscV(frame);

        int tam = frame.tamanoBytes();

        // --- Etiqueta de la funcion ---
        sb.append(nombre).append(":\n");

        // --- Prologo ---
        // Reservar el frame y guardar fp y ra del llamador. El orden importa:
        //   1) bajar sp el tamano del frame
        //   2) guardar ra en el tope (fp quedara justo encima)
        //   3) guardar el fp viejo
        //   4) fijar el nuevo fp a la base del frame (parte alta del bloque reservado)
        sb.append("    # --- prologo de ").append(nombre)
                .append(" (frame = ").append(tam).append(" bytes) ---\n");
        sb.append("    addi sp, sp, -").append(tam).append("\n");
        sb.append("    sw ra, ").append(tam - 4).append("(sp)\n");
        sb.append("    sw fp, ").append(tam - 8).append("(sp)\n");
        sb.append("    addi fp, sp, ").append(tam).append("\n");

        // --- Cuerpo ---
        sb.append("    # --- cuerpo ---\n");
        for (Cuadrupla c : fc.cuerpo()) {
            traductor.traducir(c, sb);
        }

        // --- Epilogo ---
        // Restaurar fp y ra, liberar el frame. Se etiqueta (nombre_fin) para que un
        // "return" en medio de la funcion pueda saltar aqui (subfase 3).
        sb.append("    # --- epilogo de ").append(nombre).append(" ---\n");
        sb.append(nombre).append("_fin:\n");
        sb.append("    lw ra, ").append(tam - 4).append("(sp)\n");
        sb.append("    lw fp, ").append(tam - 8).append("(sp)\n");
        sb.append("    addi sp, sp, ").append(tam).append("\n");
        if (esEntrada) {
            // Funcion de entrada: en vez de retornar a nadie, termina el programa.
            sb.append("    li a7, 10\n");   // ecall 10 = exit en RARS
            sb.append("    ecall\n\n");
        } else {
            sb.append("    ret\n\n");
        }
    }

    // Nombres de los parametros de una funcion. La firma (si existe) los trae en orden
    // e incluye "this" como primero en metodos/constructores de Z. Sin firma, se
    // generan nombres sinteticos p0..pN-1, que al menos reservan los slots correctos.
    private List<String> nombresParametros(String nombreFuncion, int nArgs) {
        GeneradorC3D.Firma firma = firmas.get(nombreFuncion);
        if (firma != null && firma.parametros() != null) {
            List<String> out = new ArrayList<>();
            for (GeneradorC3D.ParametroFirma p : firma.parametros()) {
                out.add(p.nombre());
            }
            return out;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < nArgs; i++) out.add("p" + i);
        return out;
    }

    // begin_func + las cuadruplas hasta el end_func correspondiente.
    private record FuncionCompilada(CuadruplaBeginFunc begin, List<Cuadrupla> cuerpo) {}

    private List<FuncionCompilada> dividirPorFuncion() {
        List<FuncionCompilada> resultado = new ArrayList<>();
        int i = 0;
        while (i < cuadruplas.size()) {
            Cuadrupla c = cuadruplas.get(i);
            if (c instanceof CuadruplaBeginFunc bf) {
                int j = i + 1;
                while (j < cuadruplas.size() && !(cuadruplas.get(j) instanceof CuadruplaEndFunc)) {
                    j++;
                }
                resultado.add(new FuncionCompilada(bf, new ArrayList<>(cuadruplas.subList(i + 1, j))));
                i = j + 1;
            } else {
                i++;
            }
        }
        return resultado;
    }
}
