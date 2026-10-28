package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.cuadruplas.*;

/**
 * Traduce UNA cuadrupla de codigo intermedio (C3D) a instrucciones RISC-V (RV32I + extension F para flotantes) y las agrega al StringBuilder.
 *
 * Se crea una instancia por cada funcion/metodo/constructor del programa. Cada instancia conoce su propio frame
 * (FrameFuncion), la tabla de cadenas compartida (TablaCadenas) y el layout del heap (LayoutHeap).
 *
 * Estrategia "stack-only": ningun dato vive en registros entre una cuadrupla y la siguiente. Los registros t0, t1 y t2
 * se usan SOLO como temporales de trabajo dentro de cada cuadrupla: cargar operandos, operar y guardar el
 * resultado de vuelta en la pila.
 *
 * Los flotantes se manejan con la extension F (fadd.s, fmul.s, flw, fsw, etc.). Un Set<String> llamado temporalesFlotantes
 * rastrea que nombres (temporales y variables) contienen un valor float, para que las operaciones binarias sepan
 * si deben usar instrucciones enteras (add, mul) o flotantes (fadd.s, fmul.s).
 */
public final class TraductorCuadruplaRiscV {

    private final FrameFuncion frame; /** mapea cada nombre (parametro, variable, temporal) a un offset fijo en la pila. */
    private final String nombreFuncion;  /**Se usa para generar la etiqueta del epilogo (nombre + "_fin") y para diferenciar etiquetas internas entre funciones del mismo archivo. */
    private final java.util.List<String> paramsPendientes = new java.util.ArrayList<>(); /** Acumulador de parametros pendientes: cada CuadruplaParam agrega un valor aqui, y el siguiente CuadruplaCall los consume todos de golpe*/
    private final TablaCadenas cadenas;  /** Tabla de literales de cadena y flotantes que van en la seccion .data del .s. Compartida entre todas las funciones del mismo archivo. */
    private final String prefijoEtiquetas; /** Prefijo para las etiquetas de control de flujo (L0, L1...) generadas por el C3D. Incluye el nombre del archivo fuente para evitar colisiones entre archivos distintos en el mismo .s. */
    private final LayoutHeap layoutHeap; /** Sabe en que offset (en bytes) vive cada campo dentro de un objeto del heap. Se consulta para traducir CuadruplaCampoCarga y CuadruplaCampoGuarda. */
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
    /** Punto de entrada: despacha cada cuadrupla a su metodo traductor. Cada cuadrupla se emite primero como comentario (# ...) para que
     * el .s sea legible y depurable.*/
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
    /** Asignacion: destino = valor
     * Si el valor es flotante, usa flw/fsw (registros ft0). Si no, usa lw/sw (registros t0).*/
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
    /** Operacion binaria: t = a op b
     * Si alguno de los operandos es flotante, ambos se cargan como float (convirtiendo el entero si hace falta) y se usa la
     * instruccion float correspondiente (fadd.s, fmul.s, etc.).
     * Las comparaciones float (flt.s, feq.s, fle.s) dejan el resultado en un registro ENTERO (t2), no flotante, porque los
     * if_true/if_false trabajan con enteros (0 o 1).*/
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
    //Emite la instruccion entera correspondiente al operador. Operandos en t0 y t1, resultado en t2.
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
    /** Emite la instruccion float correspondiente al operador. Operandos en ft0 y ft1; resultado aritmetico en ft2, resultado
     * de comparacion en t2 (registro entero, 0 o 1).
     * Devuelve true si el resultado es una comparacion (queda en t2), false si es aritmetico (queda en ft2).*/
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
    /** Operacion unaria: t = op a
     * Para flotantes: fneg.s (negacion).
     * Para enteros: neg (negacion aritmetica) o seqz (not logico).*/
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
    /** Control de flujo: etiquetas, goto, if_true, if_false
     * Las etiquetas del C3D (L0, L1...) se emiten con un prefijo que incluye el nombre del archivo fuente, para evitar colisiones
     * cuando varias unidades se juntan en un solo .s.
     * Los branches condicionales (beqz, bnez) solo pueden saltar +-4KB (offset de 13 bits). Para programas grandes se usa el
     * patron "branch invertido + j": se invierte la condicion con un salto CORTO (solo sobre la siguiente instruccion) y se usa "j"
     * (que tiene rango de +-1MB) para el salto real.*/
    private void traducirEtiqueta(CuadruplaEtiqueta q, StringBuilder sb) {
        sb.append(etiqueta(q.etiqueta())).append(":\n");
    }

    private String etiqueta(String etiquetaC3D) {
        return prefijoEtiquetas + etiquetaC3D;
    }

    private void traducirGoto(CuadruplaGoto q, StringBuilder sb) {
        sb.append("    j ").append(etiqueta(q.etiqueta())).append("\n");
    }
    /** if_true cond goto L: salta a L si cond != 0.
     * Usa branch invertido + j para evitar el limite de +-4KB de bnez. */
    private void traducirIfTrue(CuadruplaIfTrue q, StringBuilder sb) {
        cargar(q.condicion(), "t0", sb);
        String siguiente = etiquetaInterna("if_true_cont");
        sb.append("    beqz t0, ").append(siguiente).append("\n");
        sb.append("    j ").append(etiqueta(q.etiqueta())).append("\n");
        sb.append(siguiente).append(":\n");
    }
    /** if_false cond goto L: salta a L si cond == 0.
     * Usa branch invertido + j para evitar el limite de +-4KB de beqz.*/
    private void traducirIfFalse(CuadruplaIfFalse q, StringBuilder sb) {
        cargar(q.condicion(), "t0", sb);
        String siguiente = etiquetaInterna("if_false_cont");
        sb.append("    bnez t0, ").append(siguiente).append("\n");
        sb.append("    j ").append(etiqueta(q.etiqueta())).append("\n");
        sb.append(siguiente).append(":\n");
    }
    /** Llamadas a funcion: param, call, return
     * Convencion de llamada (simplificada, todo por la pila):
     *   1. Cada "param v" acumula v en paramsPendientes.
     *   2. "call f, n" baja el sp n*4 bytes, apila todos los params
     *      con sw, hace jal f, sube el sp de vuelta y guarda a0 en
     *      el slot del destino (si lo hay).
     *   3. "return v" carga v en a0 y salta al epilogo (nombre_fin).
     * El callee (la funcion llamada) copia los parametros desde offsets positivos de fp a sus slots locales
     * (esto lo hace el OrquestadorRiscV en el prologo, no este traductor).*/
    private void traducirParam(CuadruplaParam q, StringBuilder sb) {
        paramsPendientes.add(q.valor());
    }
    /**vEmite la llamada completa: apilar parametros, jal, restaurar sp,vguardar el valor de retorno.
     * Si la cuadrupla tiene tipoRetorno == "flotante", el temporal de destino se marca como flotante para que
     * operaciones posteriores lo traten con instrucciones float.*/
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
    /** Entrada y salida: print, read
     * Ecalls de RARS:
     *   1  -> imprimir entero (a0 = valor)
     *   2  -> imprimir flotante (fa0 = valor)
     *   4  -> imprimir cadena (a0 = direccion, termina en \0)
     *   5  -> leer entero del teclado (resultado en a0)
     *   6  -> leer flotante del teclado (resultado en fa0)
     *   8  -> leer cadena (a0 = buffer, a1 = tamano maximo)
     *  10  -> terminar programa
     *  11  -> imprimir caracter (a0 = codigo ASCII)
     *  12  -> leer caracter (resultado en a0)*/
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
    /** Imprime un flotante con formato fijo de 6 decimales (ej. "3.750000").
     * Algoritmo:
     *   1. Revisar el signo con fmv.x.w (mover bits a registro entero).
     *   2. Extraer la parte entera con fcvt.w.s (truncar hacia cero).
     *   3. Convertir la parte entera a digitos ASCII con division repetida por 10 e invertir el orden (mismo algoritmo que emitirI2S).
     *   4. Poner el punto decimal ('.').
     *   5. Extraer la parte fraccionaria: frac = valor - parteEntera, multiplicar por 1000000, truncar a entero, y extraer cada
     *      digito dividiendo por potencias de 10 decrecientes.
     *   6. Guardar la cadena resultante en el heap, imprimirla con ecall 4 y avanzar heap_ptr (alineado a 4 bytes).*/
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
    /** Determina el tipo de dato a imprimir. Primero revisa el campo tipoDato de la cuadrupla; si no lo tiene,
     * intenta inferirlo del valor (literal de cadena, caracter, entero o flotante).*/
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
    /** Heap: new, newarray
     * Los objetos y arreglos se crean en un bloque de memoria contiguo (heap_mem en la seccion .data). heap_ptr es
     * una variable global que apunta al siguiente byte libre.
     * "new Clase" reserva tantos bytes como LayoutHeap.tamanoDe(clase) indica y devuelve la direccion del inicio.
     * "newarray tipo[n]" reserva (n+1)*4 bytes: la primera palabra guarda la longitud y las siguientes n palabras son los elementos.*/
    private void emitirReservaHeap(StringBuilder sb) {
        sb.append("    la t1, heap_ptr\n");
        sb.append("    lw t2, 0(t1)\n");
        sb.append("    add t3, t2, t0\n");
        sb.append("    la t5, heap_end\n");// Verificar que no se pase del limite del heap
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
    /** Acceso a campos de objetos: CampoCarga, CampoGuarda
     * Las cuadruplas traen el nombre del campo (ej. "nombre", "saldo"), no su offset numerico. El offset se resuelve consultando al
     * LayoutHeap. Si la cuadrupla trae la clase del objeto (claseObjeto), se resuelve directamente contra esa clase, evitando ambiguedad
     * cuando dos clases distintas tienen un campo con el mismo nombre en posiciones diferentes (ej. "tamanio" en Pila vs en Cola).*/
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
    /** Acceso a arreglos por indice: IndiceCarga, IndiceGuarda
     * La direccion del elemento se calcula como:
     *   base + (indice + 1) * 4
     * El +1 es porque la primera palabra del arreglo guarda la longitud; los elementos empiezan en la segunda palabra.
     * Para arreglos inline (embebidos en una estructura de Y?), no se
     * suma 1 porque no hay palabra de longitud.*/
    private void traducirIndiceCarga(CuadruplaIndiceCarga q, StringBuilder sb) {
        cargar(q.arreglo(), "t1", sb);
        cargar(q.indice(), "t2", sb);
        boolean esInline = temporalesArreglosInline.contains(q.arreglo());
        emitirDireccionElemento(sb, esInline);
        sb.append("    lw t2, 0(t1)\n");
        guardar("t2", q.destino(), sb);
        if ("flotante".equals(q.tipoElemento())) { // Marcar el temporal como flotante si el arreglo es de floats
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
    /** Calcula t1 = base + (indice [+1]) * 4, dejando la direccion del elemento en t1. */
    private void emitirDireccionElemento(StringBuilder sb, boolean esInline) {
        if (!esInline) {
            sb.append("    addi t2, t2, 1\n");
        }
        sb.append("    slli t2, t2, 2\n");
        sb.append("    add t1, t1, t2\n");
    }
    /** Cadenas: concat, comparacion, conversion
     * Las cadenas viven en el heap como secuencias de bytes terminadas en \0 (estilo C). En la pila solo se guarda
     * la DIRECCION (un puntero de 4 bytes) al primer byte de la cadena en el heap.
     * Despues de cada operacion que escribe bytes al heap (concat, c2s, i2s, f2s), heap_ptr se alinea a multiplo
     * de 4 bytes con addi+andi para que la siguiente reserva (new, newarray) no tenga problemas de alineacion con lw/sw.*/

    /** t = concat(a, b)
     * Copia la cadena A byte a byte al heap (desde heap_ptr actual), luego la cadena B, pone un \0 al final,
     * actualiza heap_ptr (alineado) y devuelve la direccion del inicio de la copia.*/
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
    /** t = strcmp(op, a, b) donde op es "==" o "!="
     *  Compara byte a byte las dos cadenas. Deja 1 o 0 en el temporal segun el operador.*/
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
    /** Conversiones de tipo: i2f, f2i, c2i, i2c, c2s, b2s, i2s, f2s
     * Las conversiones que producen una cadena (c2s, b2s, i2s, f2s) escriben el resultado al heap y devuelven su direccion.*/
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
    /** Caracter a cadena: un byte + terminador \0 en el heap. */
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
    /** Bool a cadena: apunta a "true" o "false" precargados en .data. */
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
    /** Entero a cadena: division repetida por 10, los digitos quedan en orden inverso y luego se invierten.
     *  Maneja negativos (agrega '-') y el caso especial del cero.*/
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
    /** Flotante a cadena con 6 decimales fijos: misma logica que imprimirF2S pero guarda el resultado en el heap
     *  como cadena (para uso posterior en concat, etc.), en vez de imprimirlo directamente con ecall 4.*/
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
    /** Tracking de flotantes
     * El traductor necesita saber si un operando es flotante (bits IEEE 754) para decidir entre instrucciones enteras
     * y float. Como las cuadruplas no siempre llevan informacion de tipo, se mantiene un Set<String> con los nombres
     * de temporales/variables que se sabe que contienen un float. Se marca un nombre como flotante al traducir
     * cuadruplas que producen un resultado float (asignacion de literal float, fadd.s, fcvt.s.w, carga de campo
     * flotante, retorno de funcion que devuelve float, etc.).*/
    private static boolean esFlotanteLiteral(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("-?\\d+\\.\\d*");
    }
    /** True si el operando es flotante: literal con punto o temporal marcado. */
    private boolean esFlotante(String operando) {
        if (operando == null) return false;
        if (esFlotanteLiteral(operando)) return true;
        return temporalesFlotantes.contains(operando);
    }
    //Marca un nombre como flotante. Publico para que OrquestadorRiscV pueda marcar los parametros de funcion ANTES de traducir el cuerpo.
    public void marcarFlotante(String nombre) {
        if (nombre != null) temporalesFlotantes.add(nombre);
    }
    /** Carga y guardado de flotantes
     * Los literales flotantes se guardan en .data como .word con sus bits IEEE 754 (RARS no acepta .float) y se cargan con flw.
     * Las variables/temporales flotantes se leen/escriben de la pila con flw/fsw.*/
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
    /** Carga y guardado de enteros (el nucleo del acceso a la pila)
     * cargar() toma un operando (puede ser literal, cadena, nombre de variable/temporal, true/false/null) y deja su
     * valor en un registro entero. guardar() hace lo inverso: toma un registro y lo escribe en el slot de pila del destino.
     * Cada nombre (variable, parametro, temporal) tiene un slot unico en el frame, accesible como offset negativo desde fp:
     *   -12(fp) = primer dato, -16(fp) = segundo, etc.*/
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
    /** Guarda un registro entero en el slot de pila del destino. */
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