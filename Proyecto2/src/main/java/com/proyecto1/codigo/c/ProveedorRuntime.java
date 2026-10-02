package com.proyecto1.codigo.c;

/**
 * Abstracción del runtime que cada backend inyecta al principio del archivo
 * generado. C inyecta RuntimeC.codigo(); RISC-V tendrá el suyo.
 *
 * El orquestador NO inspecciona el contenido: solo lo concatena al inicio
 * del archivo de salida. El string devuelto debe ser el bloque de runtime
 * COMPLETO (con sus #include si aplica, comentarios, funciones, etc.).
 */
public interface ProveedorRuntime {

    // Devuelve el bloque de runtime completo, listo para inyectar.
    String codigo();
}