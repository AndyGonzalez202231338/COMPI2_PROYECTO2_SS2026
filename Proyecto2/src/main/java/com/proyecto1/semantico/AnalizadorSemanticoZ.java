package com.proyecto1.semantico;

import com.proyecto1.semantico.ast.z.*;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.AmbitoClase;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

public class AnalizadorSemanticoZ {

    public ManejadorErrores analizar(Clase clase) {
        return analizar(clase, new AmbitoGlobal());
    }

    public ManejadorErrores analizar(Clase clase, AmbitoGlobal global) {
        ManejadorErrores errores = new ManejadorErrores();

        Simbolo sClase = registrarFirma(clase, global, errores);
        if (sClase == null) {
            errores.imprimir();
            return errores;
        }
        AmbitoClase ambClase = registrarMiembros(clase, sClase, global, errores);

        // ---- SEGUNDA PASADA: verificar cuerpos ----
        for (Atributo a : clase.getAtributos()) a.verificar(ambClase, errores);
        for (Metodo m : clase.getMetodos())       m.verificar(ambClase, errores);
        for (Constructor c : clase.getConstructores()) c.verificar(ambClase, errores);

        errores.imprimir();
        return errores;
    }

    public Simbolo registrarFirma(Clase clase, AmbitoGlobal global, ManejadorErrores errores) {
        Simbolo sClase = new Simbolo(clase.getNombre(), CategoriaSimbolo.CLASE,
                null, clase.getLinea(), clase.getColumna());
        if (!global.declarar(sClase)) {
            errores.reportar(clase.getLinea(), clase.getColumna(),
                    "Clase duplicada: '" + clase.getNombre() + "'");
            return null;
        }
        return sClase;
    }

    public AmbitoClase registrarMiembros(Clase clase, Simbolo sClase,
                                         AmbitoGlobal global, ManejadorErrores errores) {
        AmbitoClase ambClase = new AmbitoClase(global, sClase);

        // === ATRIBUTOS ===
        for (Atributo a : clase.getAtributos()) {
            Simbolo sa = new Simbolo(a.getNombre(), CategoriaSimbolo.ATRIBUTO,
                    a.getTipo().resolver(global, errores),
                    a.getLinea(), a.getColumna());
            if (!ambClase.declararMiembro(sa)) {
                errores.reportar(a.getLinea(), a.getColumna(),
                        "Atributo duplicado: '" + a.getNombre() + "'");
            }
        }

        // === MÉTODOS ===
        for (Metodo m : clase.getMetodos()) {
            Tipo tRet = m.esVoid() ? TipoPrimitivo.VOID
                    : m.getTipoRetorno().resolver(global, errores);
            Simbolo sm = new Simbolo(m.getNombre(), CategoriaSimbolo.METODO,
                    tRet, m.getLinea(), m.getColumna());
            registrarParametros(sm, m.getParametros(), global, errores);

            // Clave ESPECÍFICA: nombre#aridad#Tipo1#Tipo2...
            String claveEsp = construirClaveConTipos(m.getNombre(), sm.getParametros());
            if (!ambClase.declararMiembroConClave(claveEsp, sm)) {
                errores.reportar(m.getLinea(), m.getColumna(),
                        "Método duplicado: '" + m.getNombre() + "' con "
                                + m.getParametros().size() + " parámetros");
            }
            // Clave GENÉRICA (nombre#aridad): primer método con esa aridad "gana".
            // Sirve solo como fallback para emitir "argumento incompatible" con la
            // firma más parecida cuando el match exacto por tipos falle.
            ambClase.declararMiembroConClave(m.getNombre() + "#" + m.getParametros().size(), sm);
        }

        // === CONSTRUCTORES ===
        String nombreClase = clase.getNombre();
        for (Constructor c : clase.getConstructores()) {

            // ERROR 3: el constructor DEBE llamarse igual que la clase (ÚNICO sitio).
            if (!c.getNombre().equals(nombreClase)) {
                errores.reportar(c.getLinea(), c.getColumna(),
                        "El constructor debe llamarse '" + nombreClase
                                + "', no '" + c.getNombre() + "'");
            }

            // Se registra bajo el NOMBRE DE LA CLASE (no el declarado) para que
            // "new NombreClase(...)" resuelva aunque el usuario se equivoque al nombrar.
            Simbolo sc = new Simbolo(nombreClase, CategoriaSimbolo.CONSTRUCTOR,
                    null, c.getLinea(), c.getColumna());
            registrarParametros(sc, c.getParametros(), global, errores);

            String claveEsp = construirClaveConTipos(nombreClase, sc.getParametros());
            if (!ambClase.declararMiembroConClave(claveEsp, sc)) {
                errores.reportar(c.getLinea(), c.getColumna(),
                        "Constructor duplicado: '" + nombreClase + "' con "
                                + c.getParametros().size() + " parámetros");
            }
            ambClase.declararMiembroConClave(nombreClase + "#" + c.getParametros().size(), sc);
        }

        return ambClase;
    }

    /** nombre#aridad#Tipo1#Tipo2...  ("int[]" o "Persona" se usan tal cual, vía Tipo.nombre()). */
    private static String construirClaveConTipos(String nombreBase, java.util.List<Simbolo> params) {
        StringBuilder sb = new StringBuilder(nombreBase).append("#").append(params.size());
        for (Simbolo p : params) sb.append("#").append(p.getTipo().nombre());
        return sb.toString();
    }

    private void registrarParametros(Simbolo simbolo, java.util.List<Parametro> parametros,
                                     AmbitoGlobal global, ManejadorErrores errores) {
        for (Parametro p : parametros) {
            Tipo t = p.resolverTipo(global, errores);
            simbolo.agregarParametro(new Simbolo(p.getNombre(), CategoriaSimbolo.PARAMETRO,
                    t, p.getLinea(), p.getColumna()));
        }
    }
}