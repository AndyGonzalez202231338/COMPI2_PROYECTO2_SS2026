package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.cuadruplas.*;

public final class TraductorCuadruplaRiscV {

    private final FrameFuncion frame;
    private final String nombreFuncion;
    private final java.util.List<String> paramsPendientes = new java.util.ArrayList<>();
    private final TablaCadenas cadenas;
    private final String prefijoEtiquetas;
    private final LayoutHeap layoutHeap;
    private final java.util.Set<String> temporalesFlotantes = new java.util.HashSet<>();
    private final java.util.Set<String> temporalesArreglosInline = new java.util.HashSet<>();
    private int contadorInterno = 0;

    public TraductorCuadruplaRiscV(FrameFuncion frame, String nombreFuncion,
                                   TablaCadenas cadenas, String prefijoEtiquetas,
                                   LayoutHeap layoutHeap) {
        this.frame = frame;
        this.nombreFuncion = nombreFuncion;
        this.cadenas = cadenas;
        this.layoutHeap = layoutHeap;
        this.prefijoEtiquetas = (prefijoEtiquetas == null || prefijoEtiquetas.isEmpty())
                ? "" : prefijoEtiquetas + "_";
    }

    public void traducir(Cuadrupla c, StringBuilder sb) {
        sb.append("    # ").append(c.toStringLegible()).append("\n");

        switch (c) {
            case CuadruplaAsignacion q -> traducirAsignacion(q, sb);
            case CuadruplaBinaria q -> traducirBinaria(q, sb);
            case CuadruplaUnaria q -> traducirUnaria(q, sb);
            case CuadruplaEtiqueta q -> traducirEtiqueta(q, sb);
            case CuadruplaGoto q -> traducirGoto(q, sb);
            case CuadruplaIfTrue q -> traducirIfTrue(q, sb);
            case CuadruplaIfFalse q -> traducirIfFalse(q, sb);
            case CuadruplaParam q -> traducirParam(q, sb);
            case CuadruplaCall q -> traducirCall(q, sb);
            case CuadruplaReturn q -> traducirReturn(q, sb);
            case CuadruplaPrint q -> traducirPrint(q, sb);
            case CuadruplaRead q -> traducirRead(q, sb);
            case CuadruplaNew q -> traducirNew(q, sb);
            case CuadruplaNewArray q -> traducirNewArray(q, sb);
            case CuadruplaCampoCarga q -> traducirCampoCarga(q, sb);
            case CuadruplaCampoGuarda q -> traducirCampoGuarda(q, sb);
            case CuadruplaIndiceCarga q -> traducirIndiceCarga(q, sb);
            case CuadruplaIndiceGuarda q -> traducirIndiceGuarda(q, sb);
            case CuadruplaConcat q -> traducirConcat(q, sb);
            case CuadruplaCompCadena q -> traducirCompCadena(q, sb);
            case CuadruplaConversion q -> traducirConversion(q, sb);
            default -> { }
        }
    }

    private void traducirAsignacion(CuadruplaAsignacion q, StringBuilder sb) {
        if (esFlotante(q.valor())) {
            cargarFloat(q.valor(), "ft0", sb);
            guardarFloat("ft0", q.destino(), sb);
            marcarFlotante(q.destino());
        } else {
            cargar(q.valor(), "t0", sb);
            guardar("t0", q.destino(), sb);
        }
    }

    private void traducirBinaria(CuadruplaBinaria q, StringBuilder sb) {
        boolean floatA = esFlotante(q.a());
        boolean floatB = esFlotante(q.b());

        if (floatA || floatB) {
            cargarComoFloat(q.a(), "ft0", floatA, sb);
            cargarComoFloat(q.b(), "ft1", floatB, sb);
            boolean esComparacion = emitirOperacionBinariaFloat(q.operador(), sb);
            if (esComparacion) {
                guardar("t2", q.t(), sb);
            } else {
                guardarFloat("ft2", q.t(), sb);
                marcarFlotante(q.t());
            }
        } else {
            cargar(q.a(), "t0", sb);
            cargar(q.b(), "t1", sb);
            emitirOperacionBinaria(q.operador(), sb);
            guardar("t2", q.t(), sb);
        }
    }

    private void emitirOperacionBinaria(String op, StringBuilder sb) {
        switch (op) {
            case "+" -> sb.append("    add t2, t0, t1\n");
            case "-" -> sb.append("    sub t2, t0, t1\n");
            case "*" -> sb.append("    mul t2, t0, t1\n");
            case "/" -> sb.append("    div t2, t0, t1\n");
            case "%" -> sb.append("    rem t2, t0, t1\n");
            case "<" -> sb.append("    slt t2, t0, t1\n");
            case ">" -> sb.append("    slt t2, t1, t0\n");
            case "<=" -> {
                sb.append("    slt t2, t1, t0\n");
                sb.append("    xori t2, t2, 1\n");
            }
            case ">=" -> {
                sb.append("    slt t2, t0, t1\n");
                sb.append("    xori t2, t2, 1\n");
            }
            case "==" -> {
                sb.append("    sub t2, t0, t1\n");
                sb.append("    seqz t2, t2\n");
            }
            case "!=" -> {
                sb.append("    sub t2, t0, t1\n");
                sb.append("    snez t2, t2\n");
            }
            case "&&" -> sb.append("    and t2, t0, t1\n");
            case "||" -> sb.append("    or t2, t0, t1\n");
            default -> sb.append("    # operador binario no soportado: ").append(op).append("\n");
        }
    }

    private boolean emitirOperacionBinariaFloat(String op, StringBuilder sb) {
        switch (op) {
            case "+" -> { sb.append("    fadd.s ft2, ft0, ft1\n"); return false; }
            case "-" -> { sb.append("    fsub.s ft2, ft0, ft1\n"); return false; }
            case "*" -> { sb.append("    fmul.s ft2, ft0, ft1\n"); return false; }
            case "/" -> { sb.append("    fdiv.s ft2, ft0, ft1\n"); return false; }
            case "<" -> { sb.append("    flt.s t2, ft0, ft1\n"); return true; }
            case ">" -> { sb.append("    flt.s t2, ft1, ft0\n"); return true; }
            case "<=" -> { sb.append("    fle.s t2, ft0, ft1\n"); return true; }
            case ">=" -> { sb.append("    fle.s t2, ft1, ft0\n"); return true; }
            case "==" -> { sb.append("    feq.s t2, ft0, ft1\n"); return true; }
            case "!=" -> {
                sb.append("    feq.s t2, ft0, ft1\n");
                sb.append("    xori t2, t2, 1\n");
                return true;
            }
            default -> {
                sb.append("    # operador float no soportado: ").append(op).append("\n");
                return false;
            }
        }
    }

    private void traducirUnaria(CuadruplaUnaria q, StringBuilder sb) {
        if (esFlotante(q.a())) {
            cargarFloat(q.a(), "ft0", sb);
            if (q.operador().equals("-")) {
                sb.append("    fneg.s ft2, ft0\n");
            } else {
                sb.append("    # operador unario float no soportado: ").append(q.operador()).append("\n");
            }
            guardarFloat("ft2", q.t(), sb);
            marcarFlotante(q.t());
        } else {
            cargar(q.a(), "t0", sb);
            switch (q.operador()) {
                case "-" -> sb.append("    neg t2, t0\n");
                case "!" -> sb.append("    seqz t2, t0\n");
                default -> sb.append("    # operador unario no soportado: ").append(q.operador()).append("\n");
            }
            guardar("t2", q.t(), sb);
        }
    }

    private void traducirEtiqueta(CuadruplaEtiqueta q, StringBuilder sb) {
        sb.append(etiqueta(q.etiqueta())).append(":\n");
    }

    private String etiqueta(String etiquetaC3D) {
        return prefijoEtiquetas + etiquetaC3D;
    }

    private void traducirGoto(CuadruplaGoto q, StringBuilder sb) {
        sb.append("    j ").append(etiqueta(q.etiqueta())).append("\n");
    }

    private void traducirIfTrue(CuadruplaIfTrue q, StringBuilder sb) {
        cargar(q.condicion(), "t0", sb);
        String siguiente = etiquetaInterna("if_true_cont");
        sb.append("    beqz t0, ").append(siguiente).append("\n");
        sb.append("    j ").append(etiqueta(q.etiqueta())).append("\n");
        sb.append(siguiente).append(":\n");
    }

    private void traducirIfFalse(CuadruplaIfFalse q, StringBuilder sb) {
        cargar(q.condicion(), "t0", sb);
        String siguiente = etiquetaInterna("if_false_cont");
        sb.append("    bnez t0, ").append(siguiente).append("\n");
        sb.append("    j ").append(etiqueta(q.etiqueta())).append("\n");
        sb.append(siguiente).append(":\n");
    }

    private void traducirParam(CuadruplaParam q, StringBuilder sb) {
        paramsPendientes.add(q.valor());
    }

    private void traducirCall(CuadruplaCall q, StringBuilder sb) {
        int n = paramsPendientes.size();

        if (n > 0) {
            sb.append("    addi sp, sp, -").append(n * 4).append("\n");
            for (int i = 0; i < n; i++) {
                cargar(paramsPendientes.get(i), "t0", sb);
                sb.append("    sw t0, ").append(i * 4).append("(sp)\n");
            }
        }

        sb.append("    jal ").append(q.funcion()).append("\n");

        if (n > 0) {
            sb.append("    addi sp, sp, ").append(n * 4).append("\n");
        }

        if (q.destino() != null) {
            guardar(ConvencionLlamada.REG_RETORNO, q.destino(), sb);
            if ("flotante".equals(q.tipoRetorno())) {
                marcarFlotante(q.destino());
            }
        }

        paramsPendientes.clear();
    }

    private void traducirReturn(CuadruplaReturn q, StringBuilder sb) {
        if (q.valor() != null) {
            cargar(q.valor(), ConvencionLlamada.REG_RETORNO, sb);
        }
        sb.append("    j ").append(nombreFuncion).append("_fin\n");
    }

    private void traducirPrint(CuadruplaPrint q, StringBuilder sb) {
        String tipo = tipoParaImprimir(q);
        switch (tipo) {
            case "cadena" -> {
                cargar(q.valor(), "a0", sb);
                sb.append("    li a7, 4\n");
                sb.append("    ecall\n");
            }
            case "caracter" -> {
                cargar(q.valor(), "a0", sb);
                sb.append("    li a7, 11\n");
                sb.append("    ecall\n");
            }
            case "flotante" -> {
                imprimirF2S(q.valor(), sb); // <--- VOLVEMOS A USAR ESTO
            }
            default -> {
                cargar(q.valor(), "a0", sb);
                sb.append("    li a7, 1\n");
                sb.append("    ecall\n");
            }
        }
        if (q.nuevaLinea()) {
            sb.append("    li a0, 10\n");
            sb.append("    li a7, 11\n");
            sb.append("    ecall\n");
        }
    }

    private void imprimirF2S(String operando, StringBuilder sb) {
        cargarFloat(operando, "ft0", sb);

        sb.append("    la t5, heap_ptr\n");
        sb.append("    lw t4, 0(t5)\n");
        sb.append("    mv t3, t4\n");

        String noNeg = etiquetaInterna("print_f2s_noneg");
        sb.append("    fmv.x.w t0, ft0\n");
        sb.append("    bgez t0, ").append(noNeg).append("\n");
        sb.append("    li t1, 45\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    fneg.s ft0, ft0\n");
        sb.append(noNeg).append(":\n");

        sb.append("    fcvt.w.s t0, ft0, rtz\n");

        String noCero = etiquetaInterna("print_f2s_nocero");
        String loop = etiquetaInterna("print_f2s_loop");
        String reverse = etiquetaInterna("print_f2s_rev");
        String revLoop = etiquetaInterna("print_f2s_revloop");
        String finEntero = etiquetaInterna("print_f2s_finentero");

        sb.append("    mv t6, t3\n");
        sb.append("    bnez t0, ").append(noCero).append("\n");
        sb.append("    li t1, 48\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    j ").append(reverse).append("\n");
        sb.append(noCero).append(":\n");

        sb.append("    li t1, 10\n");
        sb.append(loop).append(":\n");
        sb.append("    beqz t0, ").append(reverse).append("\n");
        sb.append("    rem t2, t0, t1\n");
        sb.append("    addi t2, t2, 48\n");
        sb.append("    sb t2, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    div t0, t0, t1\n");
        sb.append("    j ").append(loop).append("\n");

        sb.append(reverse).append(":\n");
        sb.append("    addi t0, t3, -1\n");
        sb.append(revLoop).append(":\n");
        sb.append("    bge t6, t0, ").append(finEntero).append("\n");
        sb.append("    lb t1, 0(t6)\n");
        sb.append("    lb t2, 0(t0)\n");
        sb.append("    sb t2, 0(t6)\n");
        sb.append("    sb t1, 0(t0)\n");
        sb.append("    addi t6, t6, 1\n");
        sb.append("    addi t0, t0, -1\n");
        sb.append("    j ").append(revLoop).append("\n");
        sb.append(finEntero).append(":\n");

        sb.append("    li t1, 46\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");

        sb.append("    fcvt.w.s t1, ft0, rtz\n");
        sb.append("    fcvt.s.w ft1, t1\n");
        sb.append("    fsub.s ft2, ft0, ft1\n");

        sb.append("    li t1, 1000000\n");
        sb.append("    fcvt.s.w ft1, t1\n");
        sb.append("    fmul.s ft2, ft2, ft1\n");
        sb.append("    fcvt.w.s t0, ft2\n");

        String noDesborde = etiquetaInterna("print_f2s_nodesborde");
        sb.append("    li t1, 1000000\n");
        sb.append("    blt t0, t1, ").append(noDesborde).append("\n");
        sb.append("    li t0, 999999\n");
        sb.append(noDesborde).append(":\n");

        int[] potencias = {100000, 10000, 1000, 100, 10, 1};
        for (int pot : potencias) {
            sb.append("    li t1, ").append(pot).append("\n");
            sb.append("    div t2, t0, t1\n");
            sb.append("    li t1, 10\n");
            sb.append("    rem t2, t2, t1\n");
            sb.append("    addi t2, t2, 48\n");
            sb.append("    sb t2, 0(t3)\n");
            sb.append("    addi t3, t3, 1\n");
        }

        sb.append("    sb zero, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    addi t3, t3, 3\n");
        sb.append("    andi t3, t3, -4\n");
        sb.append("    la t5, heap_ptr\n");
        sb.append("    sw t3, 0(t5)\n");

        sb.append("    mv a0, t4\n");
        sb.append("    li a7, 4\n");
        sb.append("    ecall\n");
    }

    private static String tipoParaImprimir(CuadruplaPrint q) {
        String t = q.tipoDato();
        if (t != null && !t.isEmpty() && !t.equals("desconocido")) return t;
        String v = q.valor();
        if (v == null || v.isEmpty()) return "entero";
        if (v.startsWith("\"")) return "cadena";
        if (v.startsWith("'")) return "caracter";
        if (esEnteroLiteral(v)) return "entero";
        if (v.matches("-?\\d+\\.\\d*")) return "flotante";
        return "entero";
    }

    /** read destino: el ecall de RARS depende del tipo que se esta leyendo.
     * entero   -> ecall 5, resultado en a0
     * flotante -> ecall 6, resultado en fa0
     * cadena   -> ecall 8, necesita un buffer (se reserva en el heap) y un tamano maximo; RARS escribe ahi los bytes leidos + '\0'.
     * caracter -> ecall 12, resultado en a0 (codigo ASCII) */
    private void traducirRead(CuadruplaRead q, StringBuilder sb) {
        String tipo = q.tipo();
        if ("flotante".equals(tipo)) {
            sb.append("    li a7, 6\n");
            sb.append("    ecall\n");
            guardarFloat("fa0", q.destino(), sb);
            marcarFlotante(q.destino());
        } else if ("cadena".equals(tipo)) {
            // Reservar un buffer de 256 bytes en el heap para la cadena leida.
            sb.append("    la t1, heap_ptr\n");
            sb.append("    lw a0, 0(t1)\n");      // a0 = direccion del buffer (heap_ptr actual)
            sb.append("    li a1, 256\n");         // tamano maximo del buffer
            sb.append("    li a7, 8\n");
            sb.append("    ecall\n");
            // Avanzar heap_ptr 256 bytes (alineado) para la siguiente reserva.
            sb.append("    lw t2, 0(t1)\n");
            sb.append("    addi t2, t2, 256\n");
            sb.append("    sw t2, 0(t1)\n");
            // El resultado (direccion del buffer) queda en a0 (RARS no lo cambia).
            guardar("a0", q.destino(), sb);
        } else if ("caracter".equals(tipo)) {
            sb.append("    li a7, 12\n");
            sb.append("    ecall\n");
            guardar("a0", q.destino(), sb);
        } else {
            // entero (o tipo desconocido: se asume entero)
            sb.append("    li a7, 5\n");
            sb.append("    ecall\n");
            guardar("a0", q.destino(), sb);
        }
    }

    private void emitirReservaHeap(StringBuilder sb) {
        sb.append("    la t1, heap_ptr\n");
        sb.append("    lw t2, 0(t1)\n");
        sb.append("    add t3, t2, t0\n");
        // Verificar que no se pase del limite del heap
        sb.append("    la t5, heap_end\n");
        sb.append("    bgeu t3, t5, _heap_overflow\n");
        sb.append("    sw t3, 0(t1)\n");
    }

    private void traducirNew(CuadruplaNew q, StringBuilder sb) {
        int tam = layoutHeap.tamanoDe(q.clase());
        if (tam <= 0) {
            sb.append("    # AVISO: tamano desconocido de '").append(q.clase()).append("'\n");
            tam = 4;
        }
        sb.append("    li t0, ").append(tam).append("\n");
        emitirReservaHeap(sb);
        guardar("t2", q.destino(), sb);
    }

    private void traducirNewArray(CuadruplaNewArray q, StringBuilder sb) {
        if (q.tamanos().isEmpty()) return;
        cargar(q.tamanos().get(0), "t4", sb);
        sb.append("    addi t0, t4, 1\n");
        sb.append("    slli t0, t0, 2\n");
        emitirReservaHeap(sb);
        sb.append("    sw t4, 0(t2)\n");
        guardar("t2", q.destino(), sb);
    }

    private void traducirCampoCarga(CuadruplaCampoCarga q, StringBuilder sb) {
        cargar(q.objeto(), "t1", sb);
        int off = offsetDeCampo(q.campo(), q.claseObjeto(), sb);

        String t = q.tipoCampo();
        if (t != null && t.endsWith("[]")) {
            if (q.esPuntero()) {
                sb.append("    lw t2, ").append(off).append("(t1)\n");
            } else {
                sb.append("    addi t2, t1, ").append(off).append("\n");
                temporalesArreglosInline.add(q.destino());
            }
        } else {
            sb.append("    lw t2, ").append(off).append("(t1)\n");
            if ("flotante".equals(t)) {
                marcarFlotante(q.destino());
            }
        }
        guardar("t2", q.destino(), sb);
    }

    private void traducirCampoGuarda(CuadruplaCampoGuarda q, StringBuilder sb) {
        cargar(q.objeto(), "t1", sb);
        cargar(q.valor(), "t0", sb);
        int off = offsetDeCampo(q.campo(), q.claseObjeto(), sb);
        sb.append("    sw t0, ").append(off).append("(t1)\n");
    }

    private int offsetDeCampo(String campo, StringBuilder sb) {
        return offsetDeCampo(campo, null, sb);
    }

    // Si se conoce la clase del objeto, resolver el offset DIRECTAMENTE contra esa
    // clase (sin ambiguedad posible). Sin clase, cae al resolutor "por nombre unico"
    // (offsetDeCampoUnico), que falla si dos clases distintas tienen un campo con el
    // mismo nombre en offsets distintos (ej. "tamanio" en Pila vs en Cola).
    private int offsetDeCampo(String campo, String claseObjeto, StringBuilder sb) {
        if (LayoutHeap.CAMPO_CLASS_ID.equals(campo)) return 0;

        if (claseObjeto != null) {
            int offClase = layoutHeap.offsetDe(claseObjeto, campo);
            if (offClase >= 0) return offClase;
            // La clase se conoce pero no tiene ese campo (podria ser un campo heredado
            // de una SUBCLASE que esta clase declarada no ve): caer al resolutor
            // generico como respaldo, en vez de fallar directamente.
        }

        int off = layoutHeap.offsetDeCampoUnico(campo);
        if (off == -2) {
            sb.append("    # AVISO: el campo '").append(campo).append("' existe en varias clases con posiciones distintas\n");
            return 0;
        }
        if (off < 0) {
            sb.append("    # AVISO: no se encontro la posicion del campo '").append(campo).append("'\n");
            return 0;
        }
        return off;
    }

    private void traducirIndiceCarga(CuadruplaIndiceCarga q, StringBuilder sb) {
        cargar(q.arreglo(), "t1", sb);
        cargar(q.indice(), "t2", sb);
        boolean esInline = temporalesArreglosInline.contains(q.arreglo());
        emitirDireccionElemento(sb, esInline);
        sb.append("    lw t2, 0(t1)\n");
        guardar("t2", q.destino(), sb);

        // NUEVO: Marcar el temporal como flotante si el arreglo es de floats
        if ("flotante".equals(q.tipoElemento())) {
            marcarFlotante(q.destino());
        }
    }

    private void traducirIndiceGuarda(CuadruplaIndiceGuarda q, StringBuilder sb) {
        cargar(q.arreglo(), "t1", sb);
        cargar(q.indice(), "t2", sb);
        cargar(q.valor(), "t4", sb);
        boolean esInline = temporalesArreglosInline.contains(q.arreglo());
        emitirDireccionElemento(sb, esInline);
        sb.append("    sw t4, 0(t1)\n");
    }

    private void emitirDireccionElemento(StringBuilder sb, boolean esInline) {
        if (!esInline) {
            sb.append("    addi t2, t2, 1\n");
        }
        sb.append("    slli t2, t2, 2\n");
        sb.append("    add t1, t1, t2\n");
    }

    private void traducirConcat(CuadruplaConcat q, StringBuilder sb) {
        sb.append("    la t3, heap_ptr\n");
        sb.append("    lw t3, 0(t3)\n");
        sb.append("    mv t4, t3\n");

        cargar(q.a(), "t0", sb);
        String loopA = etiquetaInterna("concat_a");
        String finA = etiquetaInterna("concat_a_fin");
        sb.append(loopA).append(":\n");
        sb.append("    lb t1, 0(t0)\n");
        sb.append("    beqz t1, ").append(finA).append("\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t0, t0, 1\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    j ").append(loopA).append("\n");
        sb.append(finA).append(":\n");

        cargar(q.b(), "t0", sb);
        String loopB = etiquetaInterna("concat_b");
        String finB = etiquetaInterna("concat_b_fin");
        sb.append(loopB).append(":\n");
        sb.append("    lb t1, 0(t0)\n");
        sb.append("    beqz t1, ").append(finB).append("\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t0, t0, 1\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    j ").append(loopB).append("\n");
        sb.append(finB).append(":\n");

        sb.append("    sb zero, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    addi t3, t3, 3\n");
        sb.append("    andi t3, t3, -4\n");
        sb.append("    la t0, heap_ptr\n");
        sb.append("    sw t3, 0(t0)\n");
        guardar("t4", q.t(), sb);
    }

    private void traducirCompCadena(CuadruplaCompCadena q, StringBuilder sb) {
        cargar(q.a(), "t0", sb);
        cargar(q.b(), "t1", sb);
        String loop = etiquetaInterna("strcmp_loop");
        String iguales = etiquetaInterna("strcmp_eq");
        String distintas = etiquetaInterna("strcmp_neq");
        String fin = etiquetaInterna("strcmp_fin");
        sb.append(loop).append(":\n");
        sb.append("    lb t2, 0(t0)\n");
        sb.append("    lb t3, 0(t1)\n");
        sb.append("    bne t2, t3, ").append(distintas).append("\n");
        sb.append("    beqz t2, ").append(iguales).append("\n");
        sb.append("    addi t0, t0, 1\n");
        sb.append("    addi t1, t1, 1\n");
        sb.append("    j ").append(loop).append("\n");
        sb.append(iguales).append(":\n");
        sb.append("    li t2, ").append(q.operador().equals("==") ? 1 : 0).append("\n");
        sb.append("    j ").append(fin).append("\n");
        sb.append(distintas).append(":\n");
        sb.append("    li t2, ").append(q.operador().equals("==") ? 0 : 1).append("\n");
        sb.append(fin).append(":\n");
        guardar("t2", q.t(), sb);
    }

    private void traducirConversion(CuadruplaConversion q, StringBuilder sb) {
        String orig = q.tipoOrigen();
        String dest = q.tipoDestino();

        if ("entero".equals(orig) && "flotante".equals(dest)) {
            cargar(q.valor(), "t0", sb);
            sb.append("    fcvt.s.w ft0, t0\n");
            guardarFloat("ft0", q.destino(), sb);
            marcarFlotante(q.destino());
        } else if ("flotante".equals(orig) && "entero".equals(dest)) {
            cargarFloat(q.valor(), "ft0", sb);
            sb.append("    fcvt.w.s t0, ft0, rtz\n");
            guardar("t0", q.destino(), sb);
        } else if ("caracter".equals(orig) && "entero".equals(dest)) {
            cargar(q.valor(), "t0", sb);
            guardar("t0", q.destino(), sb);
        } else if ("entero".equals(orig) && "caracter".equals(dest)) {
            cargar(q.valor(), "t0", sb);
            guardar("t0", q.destino(), sb);
        } else if ("caracter".equals(orig) && "cadena".equals(dest)) {
            emitirC2S(q, sb);
        } else if ("bool".equals(orig) && "cadena".equals(dest)) {
            emitirB2S(q, sb);
        } else if ("entero".equals(orig) && "cadena".equals(dest)) {
            emitirI2S(q, sb);
        } else if ("flotante".equals(orig) && "cadena".equals(dest)) {
            emitirF2S(q, sb);
        } else {
            sb.append("    # conversion no soportada: ").append(orig)
                    .append(" -> ").append(dest).append("\n");
        }
    }

    private void emitirC2S(CuadruplaConversion q, StringBuilder sb) {
        cargar(q.valor(), "t0", sb);
        sb.append("    la t1, heap_ptr\n");
        sb.append("    lw t3, 0(t1)\n");
        sb.append("    sb t0, 0(t3)\n");
        sb.append("    sb zero, 1(t3)\n");
        sb.append("    addi t4, t3, 2\n");
        sb.append("    addi t4, t4, 3\n");
        sb.append("    andi t4, t4, -4\n");
        sb.append("    sw t4, 0(t1)\n");
        guardar("t3", q.destino(), sb);
    }

    private void emitirB2S(CuadruplaConversion q, StringBuilder sb) {
        cargar(q.valor(), "t0", sb);
        String etTrue = cadenas.etiquetaDe("\"true\"");
        String etFalse = cadenas.etiquetaDe("\"false\"");
        String esFalso = etiquetaInterna("b2s_false");
        String fin = etiquetaInterna("b2s_fin");
        sb.append("    beqz t0, ").append(esFalso).append("\n");
        sb.append("    la t1, ").append(etTrue).append("\n");
        sb.append("    j ").append(fin).append("\n");
        sb.append(esFalso).append(":\n");
        sb.append("    la t1, ").append(etFalse).append("\n");
        sb.append(fin).append(":\n");
        guardar("t1", q.destino(), sb);
    }

    private void emitirI2S(CuadruplaConversion q, StringBuilder sb) {
        cargar(q.valor(), "t0", sb);
        sb.append("    la t5, heap_ptr\n");
        sb.append("    lw t4, 0(t5)\n");
        sb.append("    mv t3, t4\n");

        String noNeg = etiquetaInterna("i2s_noneg");
        String noCero = etiquetaInterna("i2s_nocero");
        String loop = etiquetaInterna("i2s_loop");
        String reverse = etiquetaInterna("i2s_rev");
        String revLoop = etiquetaInterna("i2s_revloop");
        String fin = etiquetaInterna("i2s_fin");

        sb.append("    bgez t0, ").append(noNeg).append("\n");
        sb.append("    li t1, 45\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    neg t0, t0\n");
        sb.append(noNeg).append(":\n");
        sb.append("    mv t6, t3\n");
        sb.append("    bnez t0, ").append(noCero).append("\n");
        sb.append("    li t1, 48\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    j ").append(reverse).append("\n");
        sb.append(noCero).append(":\n");
        sb.append("    li t1, 10\n");
        sb.append(loop).append(":\n");
        sb.append("    beqz t0, ").append(reverse).append("\n");
        sb.append("    rem t2, t0, t1\n");
        sb.append("    addi t2, t2, 48\n");
        sb.append("    sb t2, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    div t0, t0, t1\n");
        sb.append("    j ").append(loop).append("\n");
        sb.append(reverse).append(":\n");
        sb.append("    addi t0, t3, -1\n");
        sb.append(revLoop).append(":\n");
        sb.append("    bge t6, t0, ").append(fin).append("\n");
        sb.append("    lb t1, 0(t6)\n");
        sb.append("    lb t2, 0(t0)\n");
        sb.append("    sb t2, 0(t6)\n");
        sb.append("    sb t1, 0(t0)\n");
        sb.append("    addi t6, t6, 1\n");
        sb.append("    addi t0, t0, -1\n");
        sb.append("    j ").append(revLoop).append("\n");
        sb.append(fin).append(":\n");
        sb.append("    sb zero, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    addi t3, t3, 3\n");
        sb.append("    andi t3, t3, -4\n");
        sb.append("    la t5, heap_ptr\n");
        sb.append("    sw t3, 0(t5)\n");
        guardar("t4", q.destino(), sb);
    }

    private void emitirF2S(CuadruplaConversion q, StringBuilder sb) {
        cargarFloat(q.valor(), "ft0", sb);
        sb.append("    la t5, heap_ptr\n");
        sb.append("    lw t4, 0(t5)\n");
        sb.append("    mv t3, t4\n");

        String noNeg = etiquetaInterna("f2s_noneg");
        sb.append("    fmv.x.w t0, ft0\n");
        sb.append("    bgez t0, ").append(noNeg).append("\n");
        sb.append("    li t1, 45\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    fneg.s ft0, ft0\n");
        sb.append(noNeg).append(":\n");
        sb.append("    fcvt.w.s t0, ft0, rtz\n");

        String noCero = etiquetaInterna("f2s_nocero");
        String loop = etiquetaInterna("f2s_loop");
        String reverse = etiquetaInterna("f2s_rev");
        String revLoop = etiquetaInterna("f2s_revloop");
        String finEntero = etiquetaInterna("f2s_finentero");

        sb.append("    mv t6, t3\n");
        sb.append("    bnez t0, ").append(noCero).append("\n");
        sb.append("    li t1, 48\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    j ").append(reverse).append("\n");
        sb.append(noCero).append(":\n");
        sb.append("    li t1, 10\n");
        sb.append(loop).append(":\n");
        sb.append("    beqz t0, ").append(reverse).append("\n");
        sb.append("    rem t2, t0, t1\n");
        sb.append("    addi t2, t2, 48\n");
        sb.append("    sb t2, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    div t0, t0, t1\n");
        sb.append("    j ").append(loop).append("\n");
        sb.append(reverse).append(":\n");
        sb.append("    addi t0, t3, -1\n");
        sb.append(revLoop).append(":\n");
        sb.append("    bge t6, t0, ").append(finEntero).append("\n");
        sb.append("    lb t1, 0(t6)\n");
        sb.append("    lb t2, 0(t0)\n");
        sb.append("    sb t2, 0(t6)\n");
        sb.append("    sb t1, 0(t0)\n");
        sb.append("    addi t6, t6, 1\n");
        sb.append("    addi t0, t0, -1\n");
        sb.append("    j ").append(revLoop).append("\n");
        sb.append(finEntero).append(":\n");
        sb.append("    li t1, 46\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");

        sb.append("    fcvt.w.s t1, ft0, rtz\n");
        sb.append("    fcvt.s.w ft1, t1\n");
        sb.append("    fsub.s ft2, ft0, ft1\n");
        sb.append("    li t1, 1000000\n");
        sb.append("    fcvt.s.w ft1, t1\n");
        sb.append("    fmul.s ft2, ft2, ft1\n");
        sb.append("    fcvt.w.s t0, ft2\n");

        String noDesborde = etiquetaInterna("f2s_nodesborde");
        sb.append("    li t1, 1000000\n");
        sb.append("    blt t0, t1, ").append(noDesborde).append("\n");
        sb.append("    li t0, 999999\n");
        sb.append(noDesborde).append(":\n");

        int[] potencias = {100000, 10000, 1000, 100, 10, 1};
        for (int pot : potencias) {
            sb.append("    li t1, ").append(pot).append("\n");
            sb.append("    div t2, t0, t1\n");
            sb.append("    li t1, 10\n");
            sb.append("    rem t2, t2, t1\n");
            sb.append("    addi t2, t2, 48\n");
            sb.append("    sb t2, 0(t3)\n");
            sb.append("    addi t3, t3, 1\n");
        }

        sb.append("    sb zero, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    addi t3, t3, 3\n");
        sb.append("    andi t3, t3, -4\n");
        sb.append("    la t5, heap_ptr\n");
        sb.append("    sw t3, 0(t5)\n");
        guardar("t4", q.destino(), sb);
    }

    private String etiquetaInterna(String prefijo) {
        return prefijoEtiquetas + nombreFuncion + "_" + prefijo + "_" + (contadorInterno++);
    }

    private static boolean esFlotanteLiteral(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("-?\\d+\\.\\d*");
    }

    private boolean esFlotante(String operando) {
        if (operando == null) return false;
        if (esFlotanteLiteral(operando)) return true;
        return temporalesFlotantes.contains(operando);
    }

    public void marcarFlotante(String nombre) {
        if (nombre != null) temporalesFlotantes.add(nombre);
    }

    private void cargarFloat(String operando, String freg, StringBuilder sb) {
        if (esFlotanteLiteral(operando)) {
            float val = Float.parseFloat(operando);
            int bits = Float.floatToRawIntBits(val);
            String etiq = cadenas.etiquetaDeFlotante(bits);
            sb.append("    la t0, ").append(etiq).append("\n");
            sb.append("    flw ").append(freg).append(", 0(t0)\n");
            return;
        }
        if (frame.tiene(operando)) {
            sb.append("    flw ").append(freg).append(", ")
                    .append(frame.offsetDe(operando)).append("(fp)\n");
        } else {
            sb.append("    # AVISO: '").append(operando).append("' no tiene slot (float)\n");
        }
    }

    private void guardarFloat(String freg, String destino, StringBuilder sb) {
        if (destino != null && frame.tiene(destino)) {
            sb.append("    fsw ").append(freg).append(", ")
                    .append(frame.offsetDe(destino)).append("(fp)\n");
        } else {
            sb.append("    # AVISO: destino '").append(destino).append("' sin slot (float)\n");
        }
    }

    private void cargarComoFloat(String op, String freg, boolean yaEsFloat, StringBuilder sb) {
        if (yaEsFloat) {
            cargarFloat(op, freg, sb);
            return;
        }
        if (esEnteroLiteral(op)) {
            sb.append("    li t0, ").append(op).append("\n");
            sb.append("    fcvt.s.w ").append(freg).append(", t0\n");
            return;
        }
        if (frame.tiene(op)) {
            sb.append("    lw t0, ").append(frame.offsetDe(op)).append("(fp)\n");
            sb.append("    fcvt.s.w ").append(freg).append(", t0\n");
        } else {
            sb.append("    li t0, 0\n");
            sb.append("    fcvt.s.w ").append(freg).append(", t0\n");
        }
    }

    private void cargar(String operando, String reg, StringBuilder sb) {
        if (esFlotanteLiteral(operando)) {
            float val = Float.parseFloat(operando);
            int bits = Float.floatToRawIntBits(val);
            sb.append("    li ").append(reg).append(", ").append(bits).append("\n");
            return;
        }
        if (operando == null) {
            sb.append("    li ").append(reg).append(", 0\n");
            return;
        }
        if (operando.equals("true")) {
            sb.append("    li ").append(reg).append(", 1\n");
            return;
        }
        if (operando.equals("false") || operando.equalsIgnoreCase("null")) {
            sb.append("    li ").append(reg).append(", 0\n");
            return;
        }
        if (esEnteroLiteral(operando)) {
            sb.append("    li ").append(reg).append(", ").append(operando).append("\n");
            return;
        }
        if (operando.startsWith("\"")) {
            sb.append("    la ").append(reg).append(", ").append(cadenas.etiquetaDe(operando)).append("\n");
            return;
        }
        if (operando.startsWith("'")) {
            sb.append("    li ").append(reg).append(", ").append(codigoCaracter(operando)).append("\n");
            return;
        }
        if (frame.tiene(operando)) {
            sb.append("    lw ").append(reg).append(", ").append(frame.offsetDe(operando)).append("(fp)\n");
        } else {
            sb.append("    # AVISO: '").append(operando).append("' no tiene slot; se carga 0\n");
            sb.append("    li ").append(reg).append(", 0\n");
        }
    }

    private void guardar(String reg, String destino, StringBuilder sb) {
        if (destino != null && frame.tiene(destino)) {
            sb.append("    sw ").append(reg).append(", ").append(frame.offsetDe(destino)).append("(fp)\n");
        } else {
            sb.append("    # AVISO: destino '").append(destino).append("' sin slot; no se guarda\n");
        }
    }

    private static int codigoCaracter(String literal) {
        String s = literal.substring(1, literal.length() - 1);
        if (s.startsWith("\\") && s.length() >= 2) {
            return switch (s.charAt(1)) {
                case 'n' -> 10;
                case 't' -> 9;
                case 'r' -> 13;
                case '0' -> 0;
                default -> s.charAt(1);
            };
        }
        return s.isEmpty() ? 0 : s.charAt(0);
    }

    private static boolean esEnteroLiteral(String s) {
        if (s.isEmpty()) return false;
        int i = 0;
        if (s.charAt(0) == '-' || s.charAt(0) == '+') {
            if (s.length() == 1) return false;
            i = 1;
        }
        for (; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}