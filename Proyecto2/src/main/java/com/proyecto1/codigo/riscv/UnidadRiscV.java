package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.cuadruplas.Cuadrupla;

import java.util.List;
import java.util.Map;

// El C3D de UN archivo fuente (.y, .z o .pig), listo para traducirse a RISC-V.
//
// Hace falta porque Venus ejecuta un solo archivo .s: cuando se analiza un .pig, sus
// funciones importadas de los .y y .z tienen que ir en el mismo .s que el main. Cada
// archivo aporta su unidad y el orquestador las junta.
//
//   nombre: nombre base del archivo (Saludo, Hola, HolaMundo). Se usa como prefijo de
//           las etiquetas de salto (L0, L1...) porque el contador de etiquetas es por
//           archivo: dos archivos distintos pueden tener ambos un L0, y en un mismo .s
//           chocarian. Con el prefijo quedan Saludo_L0 y HolaMundo_L0.
public record UnidadRiscV(String nombre, List<Cuadrupla> cuadruplas,
                          Map<String, GeneradorC3D.Firma> firmas) {

    // Arma la unidad a partir del generador de C3D de un archivo. El nombre se limpia
    // para que sea una etiqueta valida (solo letras, digitos y _).
    public static UnidadRiscV de(String nombreArchivo, GeneradorC3D generador) {
        String base = nombreArchivo;
        int punto = base.lastIndexOf('.');
        if (punto >= 0) base = base.substring(0, punto);
        base = base.replaceAll("[^A-Za-z0-9_]", "_");
        return new UnidadRiscV(base, generador.getCuadruplas(), generador.getFirmas());
    }
}
