package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.cuadruplas.*;

// Traduce UNA cuadrupla a instrucciones RISC-V (RV32I) y las agrega al StringBuilder.
//
// Estado de la subfase 0: esqueleto. Cada cuadrupla se emite todavia como un
// comentario con su forma legible, para poder ver la estructura del .s (prologos,
// etiquetas, orden de las funciones) antes de traducir de verdad. En las subfases
// siguientes se iran reemplazando estos comentarios por instrucciones reales:
//   subfase 1 -> binaria, unaria, asignacion (enteros)
//   subfase 2 -> etiqueta, goto, if
//   subfase 3 -> param, call, return (ya parte va en el orquestador: prologo/epilogo)
//   subfase 4 -> print, read
//   subfase 5 -> new, newarray, campo, indice (heap)
//   subfase 6 -> dispatch por _class_id
//   subfase 7 -> flotantes y cadenas (concat, strcmp, conversion)
//
// El frame se recibe porque cada acceso a un nombre (variable/temporal) se traduce a
// un offset relativo a fp usando frame.offsetDe(nombre).
public final class TraductorCuadruplaRiscV {

    private final FrameFuncion frame;

    public TraductorCuadruplaRiscV(FrameFuncion frame) {
        this.frame = frame;
    }

    // Traduce una cuadrupla del cuerpo de la funcion. El prologo (begin_func) y el
    // epilogo (end_func) NO se traducen aqui: los arma el orquestador, porque dependen
    // del tamano total del frame.
    public void traducir(Cuadrupla c, StringBuilder sb) {
        // Por ahora: dejar constancia de la cuadrupla como comentario.
        // Esto hace legible el .s de la subfase 0 y sirve de guia al implementar
        // cada caso real en las subfases siguientes.
        sb.append("    # ").append(c.toStringLegible()).append("\n");
    }
}