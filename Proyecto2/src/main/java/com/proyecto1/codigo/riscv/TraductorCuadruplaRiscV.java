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
//   subfase 2        -> etiqueta, goto, if
//   subfase 3        -> param, call, return
//   subfase 4 (ESTA) -> print, read, literales de cadena y caracter
//   subfase 5        -> new, newarray, campo, indice (heap)
//   subfase 6        -> dispatch por _class_id
//   subfase 7        -> flotantes y cadenas
// Lo no implementado todavia se emite como comentario, para no romper el .s.
public final class TraductorCuadruplaRiscV {

    private final FrameFuncion frame;

    // Nombre de la funcion que se esta traduciendo. Se usa para armar la etiqueta del
    // epilogo (nombre_fin), destino de cada "return".
    private final String nombreFuncion;

    // Argumentos pendientes de un call. Cada "param v" agrega v aqui; el "call" los
    // consume (los apila en la pila) y limpia la lista. Es un buffer porque en el C3D
    // los param vienen ANTES del call, uno por cuadrupla, y hay que juntarlos.
    private final java.util.List<String> paramsPendientes = new java.util.ArrayList<>();

    // Literales de cadena del programa (seccion .data). Compartida por todo el .s.
    private final TablaCadenas cadenas;

    // Prefijo para las etiquetas de salto (L0, L1...). Es el nombre del archivo, porque
    // en un .s que junta varios archivos dos de ellos pueden tener su propio L0.
    private final String prefijoEtiquetas;

    public TraductorCuadruplaRiscV(FrameFuncion frame, String nombreFuncion,
                                   TablaCadenas cadenas, String prefijoEtiquetas) {
        this.frame = frame;
        this.nombreFuncion = nombreFuncion;
        this.cadenas = cadenas;
        this.prefijoEtiquetas = (prefijoEtiquetas == null || prefijoEtiquetas.isEmpty())
                ? "" : prefijoEtiquetas + "_";
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

            case CuadruplaParam q -> traducirParam(q, sb);
            case CuadruplaCall q -> traducirCall(q, sb);
            case CuadruplaReturn q -> traducirReturn(q, sb);

            case CuadruplaPrint q -> traducirPrint(q, sb);
            case CuadruplaRead q -> traducirRead(q, sb);

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

    // ===============================================================
    // Control de flujo (subfase 2)
    // ===============================================================
    //
    // Las etiquetas del C3D (L0, L1, ...) son unicas en todo el archivo (el contador
    // es global), asi que se usan tal cual como etiquetas del .s sin riesgo de colision
    // entre funciones.

    // Etiqueta: solo se imprime como destino de salto.
    private void traducirEtiqueta(CuadruplaEtiqueta q, StringBuilder sb) {
        sb.append(etiqueta(q.etiqueta())).append(":\n");
    }

    // Nombre final de una etiqueta del C3D en el .s: prefijo del archivo + etiqueta.
    private String etiqueta(String etiquetaC3D) {
        return prefijoEtiquetas + etiquetaC3D;
    }

    // goto L: salto incondicional.
    private void traducirGoto(CuadruplaGoto q, StringBuilder sb) {
        sb.append("    j ").append(etiqueta(q.etiqueta())).append("\n");
    }

    // if_true cond goto L: carga la condicion y salta si NO es cero (bnez).
    // La condicion es un 0/1 que dejo una comparacion previa (slt, seqz, ...).
    private void traducirIfTrue(CuadruplaIfTrue q, StringBuilder sb) {
        cargar(q.condicion(), "t0", sb);
        sb.append("    bnez t0, ").append(etiqueta(q.etiqueta())).append("\n");
    }

    // if_false cond goto L: carga la condicion y salta si es cero (beqz).
    private void traducirIfFalse(CuadruplaIfFalse q, StringBuilder sb) {
        cargar(q.condicion(), "t0", sb);
        sb.append("    beqz t0, ").append(etiqueta(q.etiqueta())).append("\n");
    }

    // ===============================================================
    // Funciones: param, call, return (subfase 3)
    // ===============================================================

    // param v: solo recuerda el argumento. No emite nada todavia; se apilan todos
    // juntos cuando llega el call, porque hay que saber cuantos son para mover sp una
    // sola vez.
    private void traducirParam(CuadruplaParam q, StringBuilder sb) {
        paramsPendientes.add(q.valor());
    }

    // call f, n -> destino
    //   1) reservar en la pila n palabras para los argumentos (bajar sp)
    //   2) escribir cada argumento en su posicion: arg i en i*4(sp)
    //   3) jal f  (salta a f y guarda en ra la direccion de retorno)
    //   4) subir sp para quitar los argumentos
    //   5) si hay destino, copiar el valor de retorno (a0) a su slot
    //
    // OJO con el orden: los argumentos se cargan a un registro (t0) y se escriben a la
    // pila DESPUES de haber bajado sp, para que i*4(sp) apunte a la zona reservada.
    private void traducirCall(CuadruplaCall q, StringBuilder sb) {
        int n = paramsPendientes.size();

        if (n > 0) {
            // Reservar espacio para los argumentos.
            sb.append("    addi sp, sp, -").append(n * 4).append("\n");
            // Escribir cada argumento. Se carga a t0 y se guarda en i*4(sp).
            for (int i = 0; i < n; i++) {
                cargar(paramsPendientes.get(i), "t0", sb);
                sb.append("    sw t0, ").append(i * 4).append("(sp)\n");
            }
        }

        // Llamada. jal deja la direccion de la instruccion siguiente en ra.
        sb.append("    jal ").append(q.funcion()).append("\n");

        if (n > 0) {
            // Liberar el espacio de los argumentos.
            sb.append("    addi sp, sp, ").append(n * 4).append("\n");
        }

        // Recoger el valor de retorno, si la llamada lo usa.
        if (q.destino() != null) {
            guardar(ConvencionLlamada.REG_RETORNO, q.destino(), sb);
        }

        paramsPendientes.clear();
    }

    // return v: carga v en a0 (registro de retorno) y salta al epilogo de la funcion.
    // El epilogo (nombre_fin) restaura ra/fp, libera el frame y hace ret.
    private void traducirReturn(CuadruplaReturn q, StringBuilder sb) {
        if (q.valor() != null) {
            cargar(q.valor(), ConvencionLlamada.REG_RETORNO, sb);
        }
        sb.append("    j ").append(nombreFuncion).append("_fin\n");
    }

    // ===============================================================
    // Entrada y salida (subfase 4) - convencion de Venus
    // ===============================================================
    //
    // En Venus una llamada al sistema se hace asi (verificado en su codigo fuente):
    //     a0 = codigo del servicio
    //     a1 = argumento
    //     ecall
    // Servicios que se usan:
    //     1  -> imprimir el entero que esta en a1
    //     4  -> imprimir la cadena cuya DIRECCION esta en a1 (termina en el byte 0)
    //     11 -> imprimir el caracter cuyo codigo ASCII esta en a1
    //     10 -> terminar el programa
    // OJO: es al reves que en RARS (alli el codigo va en a7 y el argumento en a0).
    //
    // a0 y a1 se pisan en cada impresion, pero no importa: ningun dato vive en
    // registros entre cuadruplas (todo esta en la pila), y el valor de retorno de un
    // call ya se guardo en su slot justo despues del jal.

    // print v / println v
    // El tipo decide el servicio. Si la cuadrupla no trae tipo, se deduce del literal;
    // un nombre sin tipo conocido se imprime como entero.
    private void traducirPrint(CuadruplaPrint q, StringBuilder sb) {
        String tipo = tipoParaImprimir(q);
        switch (tipo) {
            case "cadena" -> {
                cargar(q.valor(), "a1", sb);        // a1 = direccion de la cadena
                sb.append("    li a0, 4\n");
                sb.append("    ecall\n");
            }
            case "caracter" -> {
                cargar(q.valor(), "a1", sb);        // a1 = codigo ASCII
                sb.append("    li a0, 11\n");
                sb.append("    ecall\n");
            }
            case "flotante" -> {
                // Los flotantes usan otros registros e instrucciones: subfase 7.
                sb.append("    # pendiente subfase 7: imprimir flotante\n");
            }
            default -> {                            // entero, bool (0/1) y desconocido
                cargar(q.valor(), "a1", sb);
                sb.append("    li a0, 1\n");
                sb.append("    ecall\n");
            }
        }
        if (q.nuevaLinea()) {
            sb.append("    li a1, 10\n");          // 10 = codigo ASCII del salto de linea
            sb.append("    li a0, 11\n");
            sb.append("    ecall\n");
        }
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

    // read destino
    // Venus no tiene ninguna llamada al sistema para leer del teclado (solo imprime,
    // reserva heap y termina). Por eso la lectura deja 0 en el destino, para que el
    // programa pueda seguir. Con otro emulador (RARS) aqui iria su ecall de lectura.
    private void traducirRead(CuadruplaRead q, StringBuilder sb) {
        sb.append("    # Venus no permite leer del teclado: se guarda 0\n");
        sb.append("    li t0, 0\n");
        guardar("t0", q.destino(), sb);
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
        // Literal de cadena: se guarda en .data y se carga su DIRECCION (la = load address).
        if (operando.startsWith("\"")) {
            sb.append("    la ").append(reg).append(", ").append(cadenas.etiquetaDe(operando)).append("\n");
            return;
        }
        // Literal de caracter: se carga su codigo ASCII como un entero.
        if (operando.startsWith("'")) {
            sb.append("    li ").append(reg).append(", ").append(codigoCaracter(operando)).append("\n");
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

    // Codigo ASCII de un literal de caracter, por ejemplo 'a' o '\n'.
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
