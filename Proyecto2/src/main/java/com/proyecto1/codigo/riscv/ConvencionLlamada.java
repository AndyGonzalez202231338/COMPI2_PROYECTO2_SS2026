package com.proyecto1.codigo.riscv;

// Documenta en un solo lugar la convencion de llamada (calling convention) que usa
// el backend RISC-V. Es intencionalmente simple, no la del ABI oficial, porque el
// proyecto pide algo funcional y facil de seguir, no optimizado.
//
// ================================================================
// COMO SE PASAN LOS ARGUMENTOS: POR LA PILA
// ================================================================
//
// Antes de un "call", el llamador apila los argumentos en la pila, en orden, cada
// uno en una palabra de 4 bytes, bajando sp. El PRIMER argumento queda en la parte
// mas baja (ultimo apilado queda arriba). Asi:
//
//     # call f(arg0, arg1, arg2)
//     addi sp, sp, -12      # espacio para 3 argumentos
//     sw <arg0>, 0(sp)      # arg0 en la posicion mas baja
//     sw <arg1>, 4(sp)
//     sw <arg2>, 8(sp)
//     jal f                 # saltar a f; jal deja la direccion de retorno en ra
//     # al volver:
//     addi sp, sp, 12       # quitar los argumentos de la pila
//     # el valor de retorno de f esta en a0
//
// La funcion llamada, en su PROLOGO, copia esos argumentos desde la pila del
// llamador a los slots de parametro de su propio frame. Como el prologo ya movio sp
// para crear el frame, los argumentos del llamador quedaron JUSTO ENCIMA del frame
// nuevo. Con el frame de tamano T, el argumento i del llamador esta en:
//
//     T + i*4 (sp)      (equivalente a fp + i*4, porque fp = sp + T)
//
// y se copia al slot del parametro i, que FrameFuncion coloco en fp - 12 - i*4.
//
// ================================================================
// EL VALOR DE RETORNO: EN a0
// ================================================================
//
// "return v" carga v en a0 y salta al epilogo (etiqueta nombre_fin). El epilogo
// restaura ra/fp, libera el frame y hace ret. El llamador recoge el resultado de a0.
//
// ================================================================
// POR VALOR vs POR REFERENCIA
// ================================================================
//
// En la pila (y en a0) siempre viaja UNA palabra de 4 bytes. Para enteros, bool y
// char esa palabra ES el valor (paso por valor). Para objetos, estructuras, arreglos
// y cadenas esa palabra es la DIRECCION en el heap (paso por referencia): copiar la
// direccion no copia el contenido, asi que la funcion llamada ve el mismo objeto.
// Esto no requiere nada especial aqui: el C3D ya pasa la direccion como un valor mas.
public final class ConvencionLlamada {

    private ConvencionLlamada() {}

    // Registro donde viaja el valor de retorno.
    public static final String REG_RETORNO = "a0";

    // Bytes por argumento en la pila.
    public static final int BYTES_ARG = 4;
}
