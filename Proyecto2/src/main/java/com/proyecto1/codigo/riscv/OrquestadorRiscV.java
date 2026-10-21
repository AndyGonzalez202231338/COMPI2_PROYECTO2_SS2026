package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.cuadruplas.*;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Ensambla un archivo RISC-V (.s) completo a partir de las cuadruplas y firmas de un
// programa. Es el equivalente de OrquestadorC3DaC pero generando ensamblador en vez
// de C. Se disena para el emulador RARS (RV32I + extension F para flotantes).
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

    // Bytes reservados para el heap. Alcanza para miles de objetos pequenos; si un
    // programa pidiera mas, simplemente se saldria de la zona reservada (no hay
    // comprobacion de limite, igual que no hay liberacion de memoria).
    private static final int BYTES_HEAP = 4096;

    // Archivos cuyo codigo va en este .s. Para un .y o .z suelto es uno solo; para un
    // .pig es el propio .pig mas todos los .y/.z que importa, porque Venus ejecuta un
    // unico archivo y el main tiene que encontrar ahi las funciones que llama.
    private final List<UnidadRiscV> unidades;

    // Nombre de la funcion de entrada en el C3D:
    //   PigLatin -> "main"
    //   Y? / Z   -> null (no tienen un main propio; el .s igual se genera para
    //               inspeccionar sus funciones, pero sin punto de entrada ejecutable)
    private final String nombreFuncionEntrada;

    // Literales de cadena de TODAS las unidades; terminan en la seccion .data.
    private final TablaCadenas cadenas = new TablaCadenas();

    // Posicion de cada campo dentro de los objetos del heap. Se arma a partir de la
    // tabla de simbolos de cada unidad (ver LayoutHeap).
    private final LayoutHeap layoutHeap = new LayoutHeap();

    // Varios archivos en un mismo .s.
    public OrquestadorRiscV(List<UnidadRiscV> unidades, String nombreFuncionEntrada) {
        this.unidades = (unidades != null) ? unidades : List.of();
        this.nombreFuncionEntrada = nombreFuncionEntrada;
    }

    public String generarArchivoCompleto() {
        // 0) Registrar el layout del heap de todas las unidades: donde vive cada campo
        //    dentro de un objeto y cuanto ocupa cada clase o estructura.
        for (UnidadRiscV u : unidades) {
            layoutHeap.registrar(u.ambitoGlobal());
        }

        // 1) Juntar las funciones de todas las unidades. Cada funcion recuerda de que
        //    unidad viene (para el prefijo de sus etiquetas y para buscar su firma).
        List<FuncionCompilada> funciones = new ArrayList<>();
        for (UnidadRiscV u : unidades) {
            funciones.addAll(dividirPorFuncion(u));
        }

        // 2) RARS busca la etiqueta "main" si hay .globl main, pero para mayor
        //    compatibilidad la funcion de entrada se mueve al principio del .text.
        List<FuncionCompilada> ordenadas = new ArrayList<>();
        for (FuncionCompilada fc : funciones) {
            if (fc.begin().nombre().equals(nombreFuncionEntrada)) ordenadas.add(fc);
        }
        for (FuncionCompilada fc : funciones) {
            if (!fc.begin().nombre().equals(nombreFuncionEntrada)) ordenadas.add(fc);
        }

        // 3) Traducir el codigo. Se hace ANTES de escribir .data porque al traducir es
        //    cuando se descubren los literales de cadena.
        StringBuilder texto = new StringBuilder();
        for (FuncionCompilada fc : ordenadas) {
            boolean esEntrada = fc.begin().nombre().equals(nombreFuncionEntrada);
            emitirFuncion(fc, texto, esEntrada);
        }

        // 4) Armar el archivo: cabecera, .data con las cadenas, .text con el codigo.
        StringBuilder sb = new StringBuilder();
        sb.append("# Codigo RISC-V (RV32I + F) generado automaticamente\n");
        sb.append("# Pensado para el emulador RARS\n");
        if (unidades.size() > 1) {
            sb.append("# Incluye:");
            for (UnidadRiscV u : unidades) sb.append(" ").append(u.nombre());
            sb.append("\n");
        }
        sb.append("\n.data\n");
        if (cadenas.estaVacia()) {
            sb.append("# (sin cadenas)\n");
        } else {
            cadenas.emitirSeccionData(sb);
        }

        // ---- Zona del heap ----
        // Aqui viven los objetos, las estructuras y los arreglos: todo lo que se crea
        // con "new" y cuyo tamano no se conoce al escribir el programa.
        //
        // Se reserva un bloque fijo de memoria y se reparte con un "bump allocator":
        // un puntero que solo avanza. Cada "new" toma la direccion actual del puntero,
        // lo adelanta tantos bytes como ocupe el objeto, y devuelve la direccion que
        // tenia antes. Nunca se libera memoria (no hay "free" ni recolector), que es
        // suficiente para programas de este tamano y es lo mas simple que funciona.
        //
        //   heap_ptr: una palabra con la direccion de la proxima zona libre.
        //   heap_mem: el bloque de memoria que se va repartiendo.
        sb.append("\n# --- heap: memoria para new (objetos, estructuras y arreglos) ---\n");
        sb.append("heap_ptr: .word 0\n");
        sb.append("heap_mem: .space ").append(BYTES_HEAP).append("\n");
        sb.append("\n.text\n");
        sb.append(".globl main\n\n");
        sb.append(texto);
        return sb.toString();
    }

    // Emite una funcion completa: etiqueta, prologo, cuerpo (comentarios por ahora)
    // y epilogo.
    private void emitirFuncion(FuncionCompilada fc, StringBuilder sb, boolean esEntrada) {
        String nombre = fc.begin().nombre();

        // Nombres de los parametros, en orden, para colocarlos en los primeros slots.
        // Se toman de la firma si existe; si no, se generan como p0, p1, ... segun nArgs.
        List<String> params = nombresParametros(fc.unidad(), nombre, fc.begin().nArgs());

        FrameFuncion frame = FrameFuncion.construir(nombre, params, fc.cuerpo());
        TraductorCuadruplaRiscV traductor =
                new TraductorCuadruplaRiscV(frame, nombre, cadenas, fc.unidad().nombre(), layoutHeap);

        marcarParametrosFlotantes(traductor, fc.unidad(), nombre);

        int tam = frame.tamanoBytes();
        int nParams = params.size();

        // --- Etiqueta de la funcion ---
        sb.append(nombre).append(":\n");

        // En la funcion de entrada (main) se prepara el heap antes que nada: heap_ptr
        // arranca apuntando al inicio del bloque libre (heap_mem).
        if (esEntrada) {
            sb.append("    # inicio del heap: heap_ptr = direccion de heap_mem\n");
            sb.append("    la t0, heap_mem\n");
            sb.append("    la t1, heap_ptr\n");
            sb.append("    sw t0, 0(t1)\n");
        }

        // ============================================================
        // PROLOGO
        // ============================================================
        // Crea el stack frame de esta llamada. Recordar como estan los dos punteros:
        //   sp (stack pointer): apunta al TOPE de la pila. La pila crece hacia
        //       direcciones MENORES, asi que "reservar" espacio es RESTAR a sp.
        //   fp (frame pointer): apunta a la BASE del frame actual y NO se mueve
        //       mientras la funcion corre. Es el punto de referencia estable: cada
        //       variable, parametro y temporal se accede como offset(fp). Por eso,
        //       aunque sp baje y suba al hacer llamadas o apilar argumentos, los datos
        //       siempre se encuentran en el mismo offset(fp).
        //
        // Pasos del prologo, en orden:
        //   1) addi sp, sp, -tam   -> bajar sp para reservar todo el frame de una vez.
        //   2) sw ra, tam-4(sp)    -> guardar la direccion de retorno. Hay que guardarla
        //                             porque si esta funcion llama a otra (jal), ese jal
        //                             sobrescribe ra; sin guardarla, se perderia el
        //                             camino de vuelta al llamador.
        //   3) sw fp, tam-8(sp)    -> guardar el fp del llamador, para restaurarlo al salir
        //                             (si no, el llamador perderia su propia base de frame).
        //   4) addi fp, sp, tam    -> fijar fp a la BASE del frame (la parte alta del bloque
        //                             reservado). Desde aqui, ra queda en fp-4, el fp viejo
        //                             en fp-8, y los datos en fp-12 hacia abajo.
        sb.append("    # --- prologo de ").append(nombre)
                .append(" (frame = ").append(tam).append(" bytes) ---\n");
        sb.append("    addi sp, sp, -").append(tam).append("\n");
        sb.append("    sw ra, ").append(tam - 4).append("(sp)\n");
        sb.append("    sw fp, ").append(tam - 8).append("(sp)\n");
        sb.append("    addi fp, sp, ").append(tam).append("\n");

        // --- Copia de argumentos a los slots de parametro ---
        // El llamador dejo los argumentos JUSTO ENCIMA de este frame (ver
        // ConvencionLlamada). Como fp apunta a la base, el argumento i del llamador
        // esta en fp + i*4, y hay que copiarlo al slot del parametro i, que
        // FrameFuncion coloco en fp - 12 - i*4. Se usa t0 como puente.
        if (nParams > 0) {
            sb.append("    # copiar ").append(nParams).append(" argumento(s) a los parametros\n");
            for (int i = 0; i < nParams; i++) {
                int offArgLlamador = i * 4;                 // fp + i*4
                int offParam = frame.offsetDe(params.get(i)); // fp - 12 - i*4 (negativo)
                sb.append("    lw t0, ").append(offArgLlamador).append("(fp)\n");
                sb.append("    sw t0, ").append(offParam).append("(fp)\n");
            }
        }

        // ============================================================
        // CUERPO
        // ============================================================
        sb.append("    # --- cuerpo ---\n");
        for (Cuadrupla c : fc.cuerpo()) {
            traductor.traducir(c, sb);
        }

        // ============================================================
        // EPILOGO
        // ============================================================
        // Deshace lo del prologo, en orden inverso, y retorna. Esta etiquetado
        // (nombre_fin) porque cada "return" del cuerpo salta aqui en vez de duplicar
        // el codigo de salida. El valor de retorno ya viaja en a0 (lo dejo el return).
        //   1) lw ra  <- recuperar la direccion de retorno guardada.
        //   2) lw fp  <- recuperar la base de frame del llamador.
        //   3) addi sp, sp, tam -> subir sp para LIBERAR el frame (lo contrario del paso 1
        //                          del prologo). La pila queda como estaba antes de entrar.
        //   4) ret    <- saltar a la direccion que esta en ra (vuelve al llamador).
        sb.append("    # --- epilogo de ").append(nombre).append(" ---\n");
        sb.append(nombre).append("_fin:\n");
        sb.append("    lw ra, ").append(tam - 4).append("(sp)\n");
        sb.append("    lw fp, ").append(tam - 8).append("(sp)\n");
        sb.append("    addi sp, sp, ").append(tam).append("\n");
        if (esEntrada) {
            // Funcion de entrada (main): en vez de retornar a nadie, termina el programa.
            // RARS: servicio 10 en a7 termina el programa.
            sb.append("    li a7, 10\n");
            sb.append("    ecall\n\n");
        } else {
            sb.append("    ret\n\n");
        }
    }

    // Nombres de los parametros de una funcion. La firma (si existe) los trae en orden
    // e incluye "this" como primero en metodos/constructores de Z. Sin firma, se
    // generan nombres sinteticos p0..pN-1, que al menos reservan los slots correctos.
    private List<String> nombresParametros(UnidadRiscV unidad, String nombreFuncion, int nArgs) {
        GeneradorC3D.Firma firma = unidad.firmas().get(nombreFuncion);
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
    private record FuncionCompilada(UnidadRiscV unidad, CuadruplaBeginFunc begin, List<Cuadrupla> cuerpo) {}

    private List<FuncionCompilada> dividirPorFuncion(UnidadRiscV unidad) {
        List<Cuadrupla> cuadruplas = unidad.cuadruplas();
        List<FuncionCompilada> resultado = new ArrayList<>();
        int i = 0;
        while (i < cuadruplas.size()) {
            Cuadrupla c = cuadruplas.get(i);
            if (c instanceof CuadruplaBeginFunc bf) {
                int j = i + 1;
                while (j < cuadruplas.size() && !(cuadruplas.get(j) instanceof CuadruplaEndFunc)) {
                    j++;
                }
                resultado.add(new FuncionCompilada(unidad, bf, new ArrayList<>(cuadruplas.subList(i + 1, j))));
                i = j + 1;
            } else {
                i++;
            }
        }
        return resultado;
    }

    // Recorre la firma de la funcion y marca en el traductor cada parametro
    // cuyo tipo sea flotante, para que las cuadruplas que lo usan por primera
    // vez (antes de calcular nada con el) ya sepan tratarlo como float.
    private void marcarParametrosFlotantes(TraductorCuadruplaRiscV traductor,
                                           UnidadRiscV unidad, String nombreFuncion) {
        GeneradorC3D.Firma firma = unidad.firmas().get(nombreFuncion);
        if (firma == null || firma.parametros() == null) return;
        for (GeneradorC3D.ParametroFirma p : firma.parametros()) {
            if (p.tipo() == TipoPrimitivo.FLOTANTE) {
                traductor.marcarFlotante(p.nombre());
            }
        }
    }
}
