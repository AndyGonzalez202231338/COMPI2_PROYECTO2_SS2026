package com.proyecto1.semantico.tabla;

import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.Tipos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolucion de metodos y constructores sobrecargados teniendo en cuenta la herencia.
 * Antes bastaba con la clave exacta nombre#aridad#Tipos de los argumentos. Con
 * subtipos eso ya no alcanza: si existe comer(Animal) y se llama comer(perro), la
 * clave "comer#1#Perro" no existe pero la llamada es valida. Por eso, si la clave
 * exacta no aparece, se buscan todos los candidatos aplicables y se elige el mas
 * especifico (igual que Java).
 */
public final class ResolucionMiembros {

    private ResolucionMiembros() {}

    // Metodo de "clase" o de cualquiera de sus ancestros.
    public static Simbolo resolverMetodo(Simbolo clase, String nombre, List<Tipo> tiposArgs) {
        Simbolo exacto = clase.buscarMiembro(claveExacta(nombre, tiposArgs));
        if (exacto != null && exacto.getCategoria() == CategoriaSimbolo.METODO) return exacto;

        // Candidatos de toda la cadena. Si un metodo esta sobrescrito solo cuenta la
        // version mas cercana (la de la subclase), que es la primera que se ve al subir.
        List<Simbolo> candidatos = new ArrayList<>();
        Set<String> firmasVistas = new HashSet<>();
        for (Simbolo c = clase; c != null; c = c.getClasePadre()) {
            for (Simbolo m : c.getMetodosDeclarados()) {
                if (!m.getNombre().equals(nombre) || m.getParametros().size() != tiposArgs.size()) continue;
                if (!firmasVistas.add(m.firma())) continue;
                if (esAplicable(m, tiposArgs)) candidatos.add(m);
            }
        }
        Simbolo elegido = masEspecifico(candidatos);
        if (elegido != null) return elegido;

        Simbolo generico = clase.buscarMiembro(nombre + "#" + tiposArgs.size());
        return (generico != null && generico.getCategoria() == CategoriaSimbolo.METODO) ? generico : null;
    }

    // Constructor declarado en la propia clase (los constructores no se heredan).
    public static Simbolo resolverConstructor(Simbolo clase, List<Tipo> tiposArgs) {
        Simbolo exacto = clase.buscarMiembroLocal(claveExacta(clase.getNombre(), tiposArgs));
        if (exacto != null && exacto.getCategoria() == CategoriaSimbolo.CONSTRUCTOR) return exacto;

        List<Simbolo> candidatos = new ArrayList<>();
        for (Simbolo c : clase.getConstructoresDeclarados()) {
            if (c.getParametros().size() == tiposArgs.size() && esAplicable(c, tiposArgs)) {
                candidatos.add(c);
            }
        }
        Simbolo elegido = masEspecifico(candidatos);
        if (elegido != null) return elegido;

        Simbolo generico = clase.buscarMiembroLocal(clase.getNombre() + "#" + tiposArgs.size());
        return (generico != null && generico.getCategoria() == CategoriaSimbolo.CONSTRUCTOR) ? generico : null;
    }

    private static String claveExacta(String nombre, List<Tipo> tiposArgs) {
        StringBuilder sb = new StringBuilder(nombre).append("#").append(tiposArgs.size());
        for (Tipo t : tiposArgs) sb.append("#").append(t.nombre());
        return sb.toString();
    }

    private static boolean esAplicable(Simbolo m, List<Tipo> tiposArgs) {
        List<Simbolo> params = m.getParametros();
        for (int i = 0; i < tiposArgs.size(); i++) {
            if (!Tipos.esAsignable(params.get(i).getTipo(), tiposArgs.get(i))) return false;
        }
        return true;
    }

    /**
     * El candidato cuyos parametros se pueden pasar a todos los demas. Ejemplo:
     * con comer(Animal) y comer(Perro), para un Perro gana comer(Perro).
     * Si hay ambiguedad se devuelve el primero (el orden es el de declaracion).
     * @param candidatos
     * @return
     */
    private static Simbolo masEspecifico(List<Simbolo> candidatos) {
        if (candidatos.isEmpty()) return null;
        for (Simbolo a : candidatos) {
            boolean ganaATodos = true;
            for (Simbolo b : candidatos) {
                if (a != b && !parametrosAsignables(a, b)) {
                    ganaATodos = false;
                    break;
                }
            }
            if (ganaATodos) return a;
        }
        return candidatos.get(0);
    }

    private static boolean parametrosAsignables(Simbolo desde, Simbolo hacia) {
        List<Simbolo> pa = desde.getParametros();
        List<Simbolo> pb = hacia.getParametros();
        for (int i = 0; i < pa.size(); i++) {
            if (!Tipos.esAsignable(pb.get(i).getTipo(), pa.get(i).getTipo())) return false;
        }
        return true;
    }
}
