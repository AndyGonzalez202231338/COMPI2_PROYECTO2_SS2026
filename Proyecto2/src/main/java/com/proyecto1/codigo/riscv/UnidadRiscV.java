package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.cuadruplas.Cuadrupla;
import com.proyecto1.semantico.tabla.AmbitoGlobal;

import java.util.List;
import java.util.Map;

/** El C3D de UN archivo fuente (.y, .z o .pig), listo para traducirse a RISC-V.
 *
 * nombre: nombre del archivo con su extension (Saludo_y, Hola_z, HolaMundo_pig). Se usa como prefijo de las etiquetas
 * de salto (L0, L1...) porque el contador de etiquetas es por archivo: dos archivos distintos pueden tener ambos un
 * L0, y en un mismo .s chocarian. Con el prefijo quedan Saludo_y_L0 y HolaMundo_pig_L0. La extension se incluye para
 * que no choquen tampoco un Control.y y un Control.pig.
 *
 * ambitoGlobal: la tabla de simbolos de ese archivo. Se guarda porque el traductor necesita saber el layout del heap
 * (en que posicion vive cada campo de cada clase y cuanto ocupa un objeto), y esa informacion esta en los simbolos,
 * no en las cuadruplas. Puede ser null si el archivo no exporta tipos.
 */
public record UnidadRiscV(String nombre, List<Cuadrupla> cuadruplas,
                          Map<String, GeneradorC3D.Firma> firmas,
                          AmbitoGlobal ambitoGlobal) {

    // Arma la unidad a partir del generador de C3D de un archivo. El nombre se limpia
    // para que sea una etiqueta valida (solo letras, digitos y _): Control.y -> Control_y.
    public static UnidadRiscV de(String nombreArchivo, GeneradorC3D generador,
                                 AmbitoGlobal ambitoGlobal) {
        String base = nombreArchivo.replaceAll("[^A-Za-z0-9_]", "_");
        return new UnidadRiscV(base, generador.getCuadruplas(), generador.getFirmas(), ambitoGlobal);
    }
}
