package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.cuadruplas.*;

// Traduce UNA cuadrupla a instrucciones RISC-V (RV32I) y las agrega al StringBuilder.
//
// Convencion de trabajo (estrategia simple, sin asignacion de registros):
//   - Ningun dato vive permanentemente en un registro. Cada variable y temporal tiene
//     su lugar en la pila, accedido como offset(fp).
//   - Para operar: se cargan los operandos a registros temporales t0 y t1, se hace la
//     operacion dejando el resultado en t2, y se guarda t2 de vuelta a la pila.
//   - Es lento pero correcto siempre, incluso con recursion y con muchos temporales.
//
// Registros de trabajo que se usan aqui:
//   t0, t1 -> operandos cargados de la pila (o literales)
//   t2     -> resultado de la operacion, antes de guardarlo
// Estos tres son volatiles: cualquier call los puede pisar, por eso nunca guardamos
// nada "entre" cuadruplas en ellos; cada cuadrupla los usa y descarta.
//
// Estado por subfase:
//   subfase 1        -> asignacion, binaria, unaria (enteros)
//   subfase 2 (ESTA) -> etiqueta, goto, if
//   subfase 3        -> param, call, return
//   subfase 4        -> print, read
//   subfase 5        -> new, newarray, campo, indice (heap)
//   subfase 6        -> dispatch por _class_id
//   subfase 7        -> flotantes y cadenas
// Lo no implementado todavia se emite como comentario, para no romper el .s.
public final class TraductorCuadruplaRiscV {

    private final FrameFuncion frame;

    public TraductorCuadruplaRiscV(FrameFuncion frame) {
        this.frame = frame;
    }

    public void traducir(Cuadrupla c, StringBuilder sb) {
        // Comentario con la forma legible de la cuadrupla, para poder seguir el .s.
        sb.append("    # ").append(c.toStringLegible()).append("\n");

        switch (c) {
            case CuadruplaAsignacion q -> traducirAsignacion(q, sb);
            case CuadruplaBinaria q -> traducirBinaria(q, sb);
            case CuadruplaUnaria q -> traducirUnaria(q, sb);

            case CuadruplaEtiqueta q -> traducirEtiqueta(q, sb);
            case CuadruplaGoto q -> traducirGoto(q, sb);
            case CuadruplaIfTrue q -> traducirIfTrue(q, sb);
            case CuadruplaIfFalse q -> traducirIfFalse(q, sb);

            // Todavia no implementadas: se dejan solo como comentario (ya emitido arriba).
            default -> { /* pendiente de subfases siguientes */ }
        }
    }

    // ---------------------------------------------------------------
    // Asignacion:  destino = valor
    // ---------------------------------------------------------------
    // Carga "valor" a t0 y lo guarda en el slot de "destino".
    private void traducirAsignacion(CuadruplaAsignacion q, StringBuilder sb) {
        cargar(q.valor(), "t0", sb);
        guardar("t0", q.destino(), sb);
    }

    // ---------------------------------------------------------------
    // Binaria:  t = a op b
    // ---------------------------------------------------------------
    // Carga a->t0, b->t1, opera en t2, guarda t2 en el slot de t.
    private void traducirBinaria(CuadruplaBinaria q, StringBuilder sb) {
        cargar(q.a(), "t0", sb);
        cargar(q.b(), "t1", sb);
        emitirOperacionBinaria(q.operador(), sb);
        guardar("t2", q.t(), sb);
    }

    // Traduce el operador a una o varias instrucciones que dejan el resultado en t2,
    // operando sobre t0 y t1. Solo enteros en esta subfase.
    private void emitirOperacionBinaria(String op, StringBuilder sb) {
        switch (op) {
            case "+" -> sb.append("    add t2, t0, t1\n");
            case "-" -> sb.append("    sub t2, t0, t1\n");
            case "*" -> sb.append("    mul t2, t0, t1\n");
            case "/" -> sb.append("    div t2, t0, t1\n");
            case "%" -> sb.append("    rem t2, t0, t1\n");

            // Comparaciones: dejan 1 o 0 en t2. Las usa el if de la subfase 2, pero se
            // implementan aqui porque son operaciones binarias que producen un valor.
            //   <   -> slt directo
            //   >   -> slt con operandos invertidos (a > b  <=>  b < a)
            //   <=  -> not(a > b)
            //   >=  -> not(a < b)
            //   ==  -> resta y "igual a cero" (seqz)
            //   !=  -> resta y "distinto de cero" (snez)
            case "<" -> sb.append("    slt t2, t0, t1\n");
            case ">" -> sb.append("    slt t2, t1, t0\n");
            case "<=" -> {
                sb.append("    slt t2, t1, t0\n");   // t2 = (a > b)
                sb.append("    xori t2, t2, 1\n");   // t2 = not(a > b) = (a <= b)
            }
            case ">=" -> {
                sb.append("    slt t2, t0, t1\n");   // t2 = (a < b)
                sb.append("    xori t2, t2, 1\n");   // t2 = not(a < b) = (a >= b)
            }
            case "==" -> {
                sb.append("    sub t2, t0, t1\n");
                sb.append("    seqz t2, t2\n");
            }
            case "!=" -> {
                sb.append("    sub t2, t0, t1\n");
                sb.append("    snez t2, t2\n");
            }

            // && y || no deberian llegar aqui: el C3D ya los convirtio en saltos
            // (corto circuito). Si llegaran, se tratan como and/or bit a bit, que para
            // valores 0/1 da el mismo resultado.
            case "&&" -> sb.append("    and t2, t0, t1\n");
            case "||" -> sb.append("    or t2, t0, t1\n");

            default -> sb.append("    # operador binario no soportado: ").append(op).append("\n");
        }
    }

    // ---------------------------------------------------------------
    // Unaria:  t = op a
    // ---------------------------------------------------------------
    private void traducirUnaria(CuadruplaUnaria q, StringBuilder sb) {
        cargar(q.a(), "t0", sb);
        switch (q.operador()) {
            case "-" -> sb.append("    neg t2, t0\n");        // negacion aritmetica
            case "!" -> sb.append("    seqz t2, t0\n");       // not logico: 1 si era 0
            default -> sb.append("    # operador unario no soportado: ").append(q.operador()).append("\n");
        }
        guardar("t2", q.t(), sb);
    }
    // Las etiquetas del C3D (L0, L1, ...) son unicas en todo el archivo (el contador
    // es global), asi que se usan tal cual como etiquetas del .s sin riesgo de colision
    // entre funciones.

    // Etiqueta: solo se imprime como destino de salto.
    private void traducirEtiqueta(CuadruplaEtiqueta q, StringBuilder sb) {
        sb.append(q.etiqueta()).append(":\n");
    }

    // goto L: salto incondicional.
    private void traducirGoto(CuadruplaGoto q, StringBuilder sb) {
        sb.append("    j ").append(q.etiqueta()).append("\n");
    }

    // if_true cond goto L: carga la condicion y salta si NO es cero (bnez).
    // La condicion es un 0/1 que dejo una comparacion previa (slt, seqz, ...).
    private void traducirIfTrue(CuadruplaIfTrue q, StringBuilder sb) {
        cargar(q.condicion(), "t0", sb);
        sb.append("    bnez t0, ").append(q.etiqueta()).append("\n");
    }

    // if_false cond goto L: carga la condicion y salta si es cero (beqz).
    private void traducirIfFalse(CuadruplaIfFalse q, StringBuilder sb) {
        cargar(q.condicion(), "t0", sb);
        sb.append("    beqz t0, ").append(q.etiqueta()).append("\n");
    }

    // ===============================================================
    // Carga y guardado (el nucleo del acceso a memoria de la pila)
    // ===============================================================

    // Deja el valor de "operando" en el registro indicado.
    //   - literal entero  -> li reg, valor
    //   - literal booleano true/false -> li reg, 1 / 0
    //   - nombre en la pila -> lw reg, offset(fp)
    // Los literales de cadena y caracter no se manejan aqui (subfase 7 / casos de print).
    private void cargar(String operando, String reg, StringBuilder sb) {
        if (operando == null) {
            sb.append("    li ").append(reg).append(", 0\n");
            return;
        }
        if (operando.equals("true")) {
            sb.append("    li ").append(reg).append(", 1\n");
            return;
        }
        if (operando.equals("false") || operando.equals("null")) {
            sb.append("    li ").append(reg).append(", 0\n");
            return;
        }
        if (esEnteroLiteral(operando)) {
            sb.append("    li ").append(reg).append(", ").append(operando).append("\n");
            return;
        }
        // Es un nombre (variable o temporal): cargar de su slot en la pila.
        if (frame.tiene(operando)) {
            sb.append("    lw ").append(reg).append(", ").append(frame.offsetDe(operando)).append("(fp)\n");
        } else {
            // No deberia pasar: un nombre sin slot. Se marca para depurar.
            sb.append("    # AVISO: '").append(operando).append("' no tiene slot; se carga 0\n");
            sb.append("    li ").append(reg).append(", 0\n");
        }
    }

    // Guarda el registro en el slot de "destino" en la pila.
    private void guardar(String reg, String destino, StringBuilder sb) {
        if (destino != null && frame.tiene(destino)) {
            sb.append("    sw ").append(reg).append(", ").append(frame.offsetDe(destino)).append("(fp)\n");
        } else {
            sb.append("    # AVISO: destino '").append(destino).append("' sin slot; no se guarda\n");
        }
    }

    // true si el texto es un entero literal (posible signo + digitos). Un flotante
    // (contiene '.') no cuenta aqui: se trata en la subfase de flotantes.
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
