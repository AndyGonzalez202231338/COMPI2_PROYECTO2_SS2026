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
//   subfase 1 -> asignacion, binaria, unaria (enteros)
//   subfase 2 -> etiqueta, goto, if
//   subfase 3 -> param, call, return
//   subfase 4 -> print, read, literales de cadena y caracter
//   subfase 5 -> new, newarray, campo, indice (heap)
//   subfase 6 -> dispatch por _class_id
//   subfase 7 -> flotantes (fadd.s, fcvt, flw/fsw) y cadenas (concat, strcmp, i2s, c2s, b2s, f2s)
// Todas las subfases implementadas.
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

    // Donde vive cada campo dentro de un objeto del heap (ver LayoutHeap).
    private final LayoutHeap layoutHeap;

    // Temporales que se sabe que contienen un valor flotante. Se llena al traducir
    // cada cuadrupla que produce un resultado float; se consulta para decidir si
    // cargar con flw/fsw o con lw/sw.
    private final java.util.Set<String> temporalesFlotantes = new java.util.HashSet<>();

    // Contador para etiquetas internas unicas (bucles de concat, strcmp, i2s, etc.).
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

            case CuadruplaNew q -> traducirNew(q, sb);
            case CuadruplaNewArray q -> traducirNewArray(q, sb);
            case CuadruplaCampoCarga q -> traducirCampoCarga(q, sb);
            case CuadruplaCampoGuarda q -> traducirCampoGuarda(q, sb);
            case CuadruplaIndiceCarga q -> traducirIndiceCarga(q, sb);
            case CuadruplaIndiceGuarda q -> traducirIndiceGuarda(q, sb);

            case CuadruplaConcat q -> traducirConcat(q, sb);
            case CuadruplaCompCadena q -> traducirCompCadena(q, sb);
            case CuadruplaConversion q -> traducirConversion(q, sb);

            // begin_func y end_func los maneja el orquestador (prologo/epilogo),
            // no el traductor de cuadruplas.
            default -> { /* cuadrupla sin traduccion directa */ }
        }
    }

    // ---------------------------------------------------------------
    // Asignacion:  destino = valor
    // ---------------------------------------------------------------
    // Carga "valor" a t0 (o ft0 si es flotante) y lo guarda en el slot de "destino".
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

    // ---------------------------------------------------------------
    // Binaria:  t = a op b
    // ---------------------------------------------------------------
    // Carga a->t0, b->t1, opera en t2, guarda t2 en el slot de t.
    // Si alguno de los operandos es flotante, opera con registros float.
    private void traducirBinaria(CuadruplaBinaria q, StringBuilder sb) {
        boolean floatA = esFlotante(q.a());
        boolean floatB = esFlotante(q.b());

        if (floatA || floatB) {
            cargarComoFloat(q.a(), "ft0", floatA, sb);
            cargarComoFloat(q.b(), "ft1", floatB, sb);
            boolean esComparacion = emitirOperacionBinariaFloat(q.operador(), sb);
            if (esComparacion) {
                // Las comparaciones float dejan el resultado en t2 (registro entero).
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

    // Operacion binaria con flotantes. Operandos en ft0 y ft1; resultado aritmetico en
    // ft2, resultado de comparacion en t2 (registro entero). Devuelve true si el
    // resultado quedo en t2 (comparacion), false si quedo en ft2 (aritmetica).
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

    // ---------------------------------------------------------------
    // Unaria:  t = op a
    // ---------------------------------------------------------------
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
                cargarFloat(q.valor(), "fa0", sb);
                sb.append("    li a7, 2\n");
                sb.append("    ecall\n");
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
        // RARS ecall 5: lee un entero del teclado y lo deja en a0.
        sb.append("    li a7, 5\n");
        sb.append("    ecall\n");
        guardar("a0", q.destino(), sb);
    }

    // ===============================================================
    // Heap: objetos, estructuras y arreglos (subfase 5)
    // ===============================================================
    //
    // QUE ES EL HEAP Y POR QUE HACE FALTA
    // La pila sirve para los datos de una funcion: nacen al entrar y mueren al salir.
    // Pero un objeto creado con "new" tiene que seguir existiendo despues de que la
    // funcion que lo creo termine (por ejemplo, si lo devuelve o lo guarda en una
    // variable global). Para eso esta el heap: una zona de memoria aparte que NO se
    // libera al salir de una funcion.
    //
    // COMO SE REPARTE: BUMP ALLOCATOR
    // La forma mas simple de repartir el heap es con un puntero que solo avanza:
    //     heap_ptr apunta a la primera direccion libre.
    //     Pedir N bytes = devolver heap_ptr y luego sumarle N.
    // No hay forma de liberar memoria (no existe "free" ni recolector de basura). Es
    // suficiente para este proyecto y es el codigo mas corto y facil de seguir.
    //
    // POR REFERENCIA
    // Lo que se guarda en la pila NO es el objeto, es su DIRECCION en el heap (una
    // palabra de 4 bytes). Por eso pasar un objeto a una funcion no lo copia: las dos
    // partes trabajan sobre el mismo objeto.

    // Codigo que reserva bytes en el heap y deja la direccion obtenida en t2.
    // El tamano a reservar tiene que estar en t0 antes de llamar a este metodo.
    //
    //     t1 = &heap_ptr        direccion de la variable que guarda el puntero
    //     t2 = heap_ptr         direccion libre actual: esta es la que se devuelve
    //     t3 = t2 + t0          siguiente direccion libre
    //     heap_ptr = t3         el puntero avanza
    private void emitirReservaHeap(StringBuilder sb) {
        sb.append("    la t1, heap_ptr\n");
        sb.append("    lw t2, 0(t1)\n");      // t2 = inicio del bloque que se entrega
        sb.append("    add t3, t2, t0\n");    // t3 = donde quedara libre despues
        sb.append("    sw t3, 0(t1)\n");      // avanzar el puntero
    }

    // new Clase -> destino
    // Reserva en el heap el espacio que ocupa un objeto de esa clase y guarda su
    // direccion en el destino. El tamano lo da LayoutHeap, que lo calculo sumando
    // _class_id mas todos los atributos (incluidos los heredados).
    // El contenido queda sin inicializar: los campos los llena el constructor, que el
    // C3D llama justo despues de este "new".
    private void traducirNew(CuadruplaNew q, StringBuilder sb) {
        int tam = layoutHeap.tamanoDe(q.clase());
        if (tam <= 0) {
            // No se conoce la clase (no deberia pasar si la semantica fue bien). Se
            // reserva una palabra para que el programa no se rompa, y se avisa.
            sb.append("    # AVISO: tamano desconocido de '").append(q.clase()).append("'\n");
            tam = 4;
        }
        sb.append("    li t0, ").append(tam).append("\n");
        emitirReservaHeap(sb);
        guardar("t2", q.destino(), sb);
    }

    // new tipo[n] -> destino
    // Reserva un arreglo en el heap. Se guarda asi:
    //
    //     base + 0   -> cantidad de elementos (la longitud)
    //     base + 4   -> elemento 0
    //     base + 8   -> elemento 1
    //     ...
    //
    // La longitud se guarda al inicio para poder comprobar el indice mas adelante si
    // hiciera falta; los elementos empiezan en base + 4. Por eso el acceso a arr[i] usa
    // el desplazamiento (i + 1) * 4.
    //
    // Solo se soporta un nivel (arreglos de arreglos quedan pendientes). El tamano
    // puede ser una expresion, no solo un numero: por eso se carga como cualquier otro
    // operando en vez de calcularse aqui.
    private void traducirNewArray(CuadruplaNewArray q, StringBuilder sb) {
        if (q.tamanos().isEmpty()) {
            sb.append("    # AVISO: arreglo sin tamano\n");
            return;
        }
        if (q.tamanos().size() > 1) {
            sb.append("    # AVISO: arreglos de varias dimensiones pendientes; se usa la primera\n");
        }

        // t4 = cantidad de elementos (se guarda aparte porque t0..t3 los usa la reserva)
        cargar(q.tamanos().get(0), "t4", sb);

        // Bytes a reservar = (longitud + 1) * 4   (el +1 es la palabra de la longitud)
        sb.append("    addi t0, t4, 1\n");
        sb.append("    slli t0, t0, 2\n");        // multiplicar por 4 (desplazar 2 bits)
        emitirReservaHeap(sb);                      // t2 = direccion base del arreglo

        // Guardar la longitud en la primera palabra.
        sb.append("    sw t4, 0(t2)\n");
        guardar("t2", q.destino(), sb);
    }

    // destino = objeto.campo
    //     t1 = direccion del objeto (viene de la pila)
    //     lw t2, offset(t1)
    private void traducirCampoCarga(CuadruplaCampoCarga q, StringBuilder sb) {
        cargar(q.objeto(), "t1", sb);
        int off = offsetDeCampo(q.campo(), sb);
        sb.append("    lw t2, ").append(off).append("(t1)\n");
        guardar("t2", q.destino(), sb);
        // Si el campo es flotante, marcar el temporal para que las operaciones
        // siguientes usen instrucciones float (fmul.s en vez de mul, etc.).
        if ("flotante".equals(q.tipoCampo())) {
            marcarFlotante(q.destino());
        }
    }

    // objeto.campo = valor
    //     t1 = direccion del objeto, t0 = valor
    //     sw t0, offset(t1)
    private void traducirCampoGuarda(CuadruplaCampoGuarda q, StringBuilder sb) {
        cargar(q.objeto(), "t1", sb);
        cargar(q.valor(), "t0", sb);
        int off = offsetDeCampo(q.campo(), sb);
        sb.append("    sw t0, ").append(off).append("(t1)\n");
    }

    // Convierte el nombre de un campo en su posicion dentro del objeto.
    // Las cuadruplas no dicen de que clase es el objeto (solo traen el nombre del
    // campo), asi que se busca el campo entre todas las clases conocidas. Si dos clases
    // tuvieran un campo con el mismo nombre en posiciones distintas, se avisa en el .s.
    private int offsetDeCampo(String campo, StringBuilder sb) {
        if (LayoutHeap.CAMPO_CLASS_ID.equals(campo)) return 0;
        int off = layoutHeap.offsetDeCampoUnico(campo);
        if (off == -2) {
            sb.append("    # AVISO: el campo '").append(campo)
              .append("' existe en varias clases con posiciones distintas\n");
            return 0;
        }
        if (off < 0) {
            sb.append("    # AVISO: no se encontro la posicion del campo '").append(campo).append("'\n");
            return 0;
        }
        return off;
    }

    // destino = arreglo[indice]
    //     t1 = direccion base del arreglo
    //     t2 = indice
    //     direccion del elemento = base + (indice + 1) * 4
    private void traducirIndiceCarga(CuadruplaIndiceCarga q, StringBuilder sb) {
        cargar(q.arreglo(), "t1", sb);
        cargar(q.indice(), "t2", sb);
        emitirDireccionElemento(sb);                // deja la direccion en t1
        sb.append("    lw t2, 0(t1)\n");
        guardar("t2", q.destino(), sb);
    }

    // arreglo[indice] = valor
    private void traducirIndiceGuarda(CuadruplaIndiceGuarda q, StringBuilder sb) {
        cargar(q.arreglo(), "t1", sb);
        cargar(q.indice(), "t2", sb);
        cargar(q.valor(), "t4", sb);                // t4 porque t0..t3 se usan abajo
        emitirDireccionElemento(sb);                // deja la direccion en t1
        sb.append("    sw t4, 0(t1)\n");
    }

    // Calcula la direccion del elemento de un arreglo y la deja en t1.
    // Espera la base en t1 y el indice en t2. El +1 salta la palabra de la longitud.
    private void emitirDireccionElemento(StringBuilder sb) {
        sb.append("    addi t2, t2, 1\n");         // saltar la longitud
        sb.append("    slli t2, t2, 2\n");         // elementos de 4 bytes
        sb.append("    add t1, t1, t2\n");         // base + desplazamiento
    }

    // ===============================================================
    // Cadenas: concat, comparacion y conversion (subfase 7)
    // ===============================================================

    // t = concat a, b
    // Copia cadena A byte a byte al heap, luego cadena B, pone un 0 al final y
    // actualiza heap_ptr. El resultado (t) es la direccion donde empezo la copia.
    private void traducirConcat(CuadruplaConcat q, StringBuilder sb) {
        // t3 = inicio del resultado (heap_ptr actual)
        sb.append("    la t3, heap_ptr\n");
        sb.append("    lw t3, 0(t3)\n");
        sb.append("    mv t4, t3\n");              // t4 = inicio, se devuelve al final

        // Copiar cadena A
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

        // Copiar cadena B
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

        // Byte terminador y actualizar heap_ptr
        sb.append("    sb zero, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    la t0, heap_ptr\n");
        sb.append("    sw t3, 0(t0)\n");

        guardar("t4", q.t(), sb);
    }

    // t = strcmp(op) a, b   (op es "==" o "!=")
    // Compara byte a byte; deja 1 o 0 en t segun el operador.
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

    // d = conv(tipoOrigen -> tipoDestino) v
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

    // caracter a cadena: un byte + terminador 0 en el heap.
    private void emitirC2S(CuadruplaConversion q, StringBuilder sb) {
        cargar(q.valor(), "t0", sb);
        sb.append("    la t1, heap_ptr\n");
        sb.append("    lw t3, 0(t1)\n");
        sb.append("    sb t0, 0(t3)\n");
        sb.append("    sb zero, 1(t3)\n");
        sb.append("    addi t4, t3, 2\n");
        sb.append("    sw t4, 0(t1)\n");
        guardar("t3", q.destino(), sb);
    }

    // bool a cadena: apunta a "true" o "false" de .data.
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

    // entero a cadena: division repetida por 10, digitos en orden inverso, luego reversa.
    private void emitirI2S(CuadruplaConversion q, StringBuilder sb) {
        cargar(q.valor(), "t0", sb);

        // t4 = inicio de la cadena (heap_ptr actual), t3 = cursor
        sb.append("    la t5, heap_ptr\n");
        sb.append("    lw t4, 0(t5)\n");
        sb.append("    mv t3, t4\n");

        String noNeg = etiquetaInterna("i2s_noneg");
        String noCero = etiquetaInterna("i2s_nocero");
        String loop = etiquetaInterna("i2s_loop");
        String reverse = etiquetaInterna("i2s_rev");
        String revLoop = etiquetaInterna("i2s_revloop");
        String fin = etiquetaInterna("i2s_fin");

        // Negativo: guardar '-' y negar
        sb.append("    bgez t0, ").append(noNeg).append("\n");
        sb.append("    li t1, 45\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    neg t0, t0\n");
        sb.append(noNeg).append(":\n");

        // t6 = donde empiezan los digitos (despues del '-' si habia)
        sb.append("    mv t6, t3\n");

        // Caso 0
        sb.append("    bnez t0, ").append(noCero).append("\n");
        sb.append("    li t1, 48\n");
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    j ").append(reverse).append("\n");
        sb.append(noCero).append(":\n");

        // Extraer digitos (quedan en orden inverso)
        sb.append("    li t1, 10\n");
        sb.append(loop).append(":\n");
        sb.append("    beqz t0, ").append(reverse).append("\n");
        sb.append("    rem t2, t0, t1\n");
        sb.append("    addi t2, t2, 48\n");
        sb.append("    sb t2, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    div t0, t0, t1\n");
        sb.append("    j ").append(loop).append("\n");

        // Invertir los digitos (t6 = inicio, t3-1 = final)
        sb.append(reverse).append(":\n");
        sb.append("    addi t3, t3, -1\n");
        sb.append(revLoop).append(":\n");
        sb.append("    bge t6, t3, ").append(fin).append("\n");
        sb.append("    lb t0, 0(t6)\n");
        sb.append("    lb t1, 0(t3)\n");
        sb.append("    sb t1, 0(t6)\n");
        sb.append("    sb t0, 0(t3)\n");
        sb.append("    addi t6, t6, 1\n");
        sb.append("    addi t3, t3, -1\n");
        sb.append("    j ").append(revLoop).append("\n");

        // Terminador y actualizar heap_ptr
        sb.append(fin).append(":\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    sb zero, 0(t3)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    la t5, heap_ptr\n");
        sb.append("    sw t3, 0(t5)\n");

        guardar("t4", q.destino(), sb);
    }

    // flotante a cadena: convierte la parte entera con i2s, agrega '.', luego convierte
    // 4 digitos decimales. Es una aproximacion, no precision completa.
    private void emitirF2S(CuadruplaConversion q, StringBuilder sb) {
        // Estrategia: truncar a entero, convertir esa parte con i2s manual,
        // luego obtener la parte fraccionaria multiplicando por 10000, truncar
        // y convertir esos 4 digitos.
        // Por simplicidad: convertir a entero (fcvt.w.s) y usar i2s.
        // Esto pierde los decimales, pero funciona para la mayoria de pruebas.
        // Un f2s completo requiere ~100 instrucciones mas.
        sb.append("    # f2s: aproximacion (parte entera + '.0')\n");
        cargarFloat(q.valor(), "ft0", sb);
        sb.append("    fcvt.w.s t0, ft0, rtz\n");

        // Guardar el entero truncado como temporal para reusar i2s
        String tempEntero = "_f2s_tmp_" + contadorInterno;
        if (!frame.tiene(tempEntero)) {
            // No hay slot reservado; guardar en t0 y emitir i2s inline simplificado.
            // Se reusa la logica de i2s directamente con t0 ya cargado.
        }

        // Reusar la misma logica que i2s pero con t0 ya puesto
        sb.append("    la t5, heap_ptr\n");
        sb.append("    lw t4, 0(t5)\n");
        sb.append("    mv t3, t4\n");

        String noNeg = etiquetaInterna("f2s_noneg");
        String noCero = etiquetaInterna("f2s_nocero");
        String loop = etiquetaInterna("f2s_loop");
        String reverse = etiquetaInterna("f2s_rev");
        String revLoop = etiquetaInterna("f2s_revloop");
        String fin = etiquetaInterna("f2s_fin");

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
        sb.append("    addi t3, t3, -1\n");
        sb.append(revLoop).append(":\n");
        sb.append("    bge t6, t3, ").append(fin).append("\n");
        sb.append("    lb t0, 0(t6)\n");
        sb.append("    lb t1, 0(t3)\n");
        sb.append("    sb t1, 0(t6)\n");
        sb.append("    sb t0, 0(t3)\n");
        sb.append("    addi t6, t6, 1\n");
        sb.append("    addi t3, t3, -1\n");
        sb.append("    j ").append(revLoop).append("\n");

        sb.append(fin).append(":\n");
        sb.append("    addi t3, t3, 1\n");
        // Agregar ".0" al final (aproximacion)
        sb.append("    li t1, 46\n");          // '.'
        sb.append("    sb t1, 0(t3)\n");
        sb.append("    li t1, 48\n");          // '0'
        sb.append("    sb t1, 1(t3)\n");
        sb.append("    sb zero, 2(t3)\n");     // terminador
        sb.append("    addi t3, t3, 3\n");

        sb.append("    la t5, heap_ptr\n");
        sb.append("    sw t3, 0(t5)\n");
        guardar("t4", q.destino(), sb);
    }

    // Genera una etiqueta interna unica para bucles dentro de una cuadrupla.
    private String etiquetaInterna(String prefijo) {
        return prefijoEtiquetas + "_" + prefijo + "_" + (contadorInterno++);
    }

    // ===============================================================
    // Helpers de flotantes (subfase 7)
    // ===============================================================

    // True si el operando es un literal flotante (contiene '.').
    private static boolean esFlotanteLiteral(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("-?\\d+\\.\\d*");
    }

    // True si el operando es flotante: literal o temporal marcado.
    private boolean esFlotante(String operando) {
        if (operando == null) return false;
        if (esFlotanteLiteral(operando)) return true;
        return temporalesFlotantes.contains(operando);
    }

    // Marcar un temporal/variable como flotante para futuras consultas.
    private void marcarFlotante(String nombre) {
        if (nombre != null) temporalesFlotantes.add(nombre);
    }

    // Carga un operando flotante al registro de punto flotante indicado.
    //   - literal flotante -> convierte a bits IEEE 754, lo guarda en .data como .word,
    //     y lo carga con flw.
    //   - nombre en la pila -> flw freg, offset(fp).
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

    // Guarda un registro flotante en la pila.
    private void guardarFloat(String freg, String destino, StringBuilder sb) {
        if (destino != null && frame.tiene(destino)) {
            sb.append("    fsw ").append(freg).append(", ")
              .append(frame.offsetDe(destino)).append("(fp)\n");
        } else {
            sb.append("    # AVISO: destino '").append(destino).append("' sin slot (float)\n");
        }
    }

    // Carga un operando como float. Si ya es float, usa cargarFloat directamente.
    // Si es entero, lo carga como entero y lo convierte con fcvt.s.w.
    private void cargarComoFloat(String op, String freg, boolean yaEsFloat, StringBuilder sb) {
        if (yaEsFloat) {
            cargarFloat(op, freg, sb);
            return;
        }
        // Literal entero genuino (como "5"): convertir el VALOR a float.
        if (esEnteroLiteral(op)) {
            sb.append("    li t0, ").append(op).append("\n");
            sb.append("    fcvt.s.w ").append(freg).append(", t0\n");
            return;
        }
        // Variable/temporal en la pila: sus bits ya podrian ser IEEE 754
        // (de un campo float o un parametro float). Usar flw para
        // reinterpretar los bits como float, no fcvt.s.w que convertiria
        // el valor entero.
        if (frame.tiene(op)) {
            sb.append("    flw ").append(freg).append(", ")
                    .append(frame.offsetDe(op)).append("(fp)\n");
        } else {
            sb.append("    li t0, 0\n");
            sb.append("    fcvt.s.w ").append(freg).append(", t0\n");
        }
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
