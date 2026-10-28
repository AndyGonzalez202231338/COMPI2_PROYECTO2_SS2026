package com.proyecto1.codigo.riscv;

import com.proyecto1.semantico.ast.cuadruplas.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa el stack frame (marco de pila) de UNA funcion para la traduccion a RISC-V.
 * Idea central del manejo de memoria del proyecto: NADA vive permanentemente en registros. Cada parametro, variable
 * local y temporal tiene un espacio reservado en la pila, y se accede siempre relativo a fp (frame pointer), que es la
 * base fija e inamovible de la funcion activa. Los registros t0, t1, t2 solo se usan como area de trabajo momentanea
 * para evaluar una operacion y enseguida el resultado se guarda de vuelta en la pila.
 *
 * RECURSION: cada llamada tiene su propio frame, asi que un parametro usado despues de un call sigue intacto.
 *
 * Layout del frame (crece hacia direcciones menores):
 *      fp - 4   -> ra, direccion de retorno (guardada en el prologo)
 *      fp - 8   -> fp del llamador (guardado en el prologo)
 *      fp - 12  -> slot 0  (primer parametro, o primer local/temporal si no hay params)
 *      fp - 16  -> slot 1
 * El acceso a un nombre es entonces: offsetEnBytes(nombre) = -(12 + slot*4). Los 12 bytes iniciales reservan ra (fp-4)
 * y el fp viejo (fp-8); los datos empiezan debajo, en fp-12, para no pisarlos.
 */
public final class FrameFuncion {

    /** Bytes fijos al inicio del frame reservados para ra y fp del llamador, que el prologo guarda en fp-4 (ra) y fp-8
     * (fp viejo). Los slots de datos empiezan prologo guarda en fp-4 (ra) y fp-8 (fp viejo). Los slots de datos empiezan
     * DEBAJO de esos dos, en fp-12, para no pisarlos. Por eso la cabecera es 12.*/
    private static final int BYTES_CABECERA = 12;
    private static final int BYTES_POR_SLOT = 4;

    private final String nombreFuncion;

    // nombre (parametro, variable o temporal) -> numero de slot (0, 1, 2, ...). LinkedHashMap para conservar el orden de asignacion, util al depurar el .s.
    private final Map<String, Integer> slots = new LinkedHashMap<>();

    private FrameFuncion(String nombreFuncion) {
        this.nombreFuncion = nombreFuncion;
    }

    /** Construye el frame de una funcion a partir de su begin_func y su cuerpo.
     *      nombresParametros: los parametros en orden (incluye "this" en metodos de Z).
     *      cuerpo: las cuadruplas entre begin_func y end_func. */
    public static FrameFuncion construir(String nombreFuncion,
                                         List<String> nombresParametros,
                                         List<Cuadrupla> cuerpo) {
        FrameFuncion f = new FrameFuncion(nombreFuncion);

        // 1) Los parametros ocupan los primeros slots, en orden.
        for (String p : nombresParametros) {
            f.reservar(p);
        }

        // 2) Recorrer el cuerpo y reservar un slot para cada nombre nuevo que aparezca como destino o como operando. Los literales y las etiquetas se ignoran.
        for (Cuadrupla c : cuerpo) {
            for (String nombre : nombresUsados(c)) {
                if (esAlmacenable(nombre)) {
                    f.reservar(nombre);
                }
            }
        }
        return f;
    }

    private void reservar(String nombre) {
        slots.putIfAbsent(nombre, slots.size());
    }

    // Offset en bytes respecto a fp para acceder a este nombre. Siempre negativo porque el frame crece hacia abajo desde fp.
    public int offsetDe(String nombre) {
        Integer slot = slots.get(nombre);
        if (slot == null) {
            throw new IllegalArgumentException(
                    "El nombre '" + nombre + "' no tiene slot en el frame de " + nombreFuncion);
        }
        return -(BYTES_CABECERA + slot * BYTES_POR_SLOT);
    }

    public boolean tiene(String nombre) {
        return slots.containsKey(nombre);
    }

    // Tamano total del frame en bytes, redondeado a multiplo de 16 porque el ABI de RISC-V exige que sp quede alineado a 16 bytes.
    public int tamanoBytes() {
        int bruto = BYTES_CABECERA + slots.size() * BYTES_POR_SLOT;
        return (bruto + 15) / 16 * 16;
    }

    public int cantidadSlots() {
        return slots.size();
    }

    public String getNombreFuncion() {
        return nombreFuncion;
    }

    /**  Un nombre va a la pila solo si es una variable o temporal. Se excluyen:
     *      - null
     *      - literales numericos (123, 3.14)
     *      - literales de cadena ("texto")
     *      - literales de caracter ('a')
     *      - literales de caracter ('a')
     *   Las etiquetas (L0, L1) nunca llegan aqui porque no se consultan como operandos.*/
    private static boolean esAlmacenable(String nombre) {
        if (nombre == null || nombre.isEmpty()) return false;
        char c0 = nombre.charAt(0);
        if (c0 == '"' || c0 == '\'') return false;         // cadena o caracter literal
        if (Character.isDigit(c0) || c0 == '-' || c0 == '+') return false; // numero
        if (nombre.equals("true") || nombre.equals("false")) return false;
        if (nombre.equals("null")) return false;
        // "this" ya se reserva como parametro; no volver a tratarlo.
        return true;
    }

    // Devuelve los operandos y destinos "nombrables" de una cuadrupla, para decidir
    // cuales necesitan slot. Se listan todos; esAlmacenable filtra los literales.
    private static List<String> nombresUsados(Cuadrupla c) {
        return switch (c) {
            case CuadruplaBinaria q -> List.of(seguro(q.a()), seguro(q.b()), seguro(q.t()));
            case CuadruplaUnaria q -> List.of(seguro(q.a()), seguro(q.t()));
            case CuadruplaAsignacion q -> List.of(seguro(q.valor()), seguro(q.destino()));
            case CuadruplaIfTrue q -> List.of(seguro(q.condicion()));
            case CuadruplaIfFalse q -> List.of(seguro(q.condicion()));
            case CuadruplaPrint q -> List.of(seguro(q.valor()));
            case CuadruplaRead q -> List.of(seguro(q.destino()));
            case CuadruplaParam q -> List.of(seguro(q.valor()));
            case CuadruplaReturn q -> List.of(seguro(q.valor()));
            case CuadruplaCall q -> List.of(seguro(q.destino()));
            case CuadruplaIndiceCarga q -> List.of(seguro(q.arreglo()), seguro(q.indice()), seguro(q.destino()));
            case CuadruplaIndiceGuarda q -> List.of(seguro(q.arreglo()), seguro(q.indice()), seguro(q.valor()));
            case CuadruplaCampoCarga q -> List.of(seguro(q.objeto()), seguro(q.destino()));
            case CuadruplaCampoGuarda q -> List.of(seguro(q.objeto()), seguro(q.valor()));
            case CuadruplaNew q -> List.of(seguro(q.destino()));
            case CuadruplaNewArray q -> {
                java.util.List<String> ns = new java.util.ArrayList<>();
                ns.add(seguro(q.destino()));
                for (String t : q.tamanos()) ns.add(seguro(t));
                yield ns;
            }
            case CuadruplaConcat q -> List.of(seguro(q.a()), seguro(q.b()), seguro(q.t()));
            case CuadruplaCompCadena q -> List.of(seguro(q.a()), seguro(q.b()), seguro(q.t()));
            case CuadruplaConversion q -> List.of(seguro(q.valor()), seguro(q.destino()));
            // begin_func, end_func, goto, label no aportan nombres de datos.
            default -> List.of();
        };
    }

    // Convierte null en "" para que List.of no falle (no acepta nulls).
    private static String seguro(String s) {
        return (s == null) ? "" : s;
    }
}
