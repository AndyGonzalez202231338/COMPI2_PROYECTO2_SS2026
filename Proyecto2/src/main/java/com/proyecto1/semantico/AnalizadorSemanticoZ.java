package com.proyecto1.semantico;

import com.proyecto1.semantico.ast.z.*;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Acceso;
import com.proyecto1.semantico.tabla.AmbitoClase;
import com.proyecto1.semantico.tabla.AmbitoGlobal;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.ModificadorAcceso;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoClase;
import com.proyecto1.semantico.tipos.TipoPrimitivo;

public class AnalizadorSemanticoZ {

    public ManejadorErrores analizar(Clase clase) {
        return analizar(clase, new AmbitoGlobal());
    }

    /**
     * Orden del analisis de una clase:
     *      1) firma: el nombre de la clase en el ambito global
     *      2) miembros: atributos, metodos y constructores propios
     *      3) herencia: enlazar la clase padre. Despues se reintentan los enlaces pendientes
     *         de las clases hermanas, porque alguna puede heredar justo de esta clase (que
     *         se registra al final, despues de todas las hermanas)
     *      4) validaciones de herencia/polimorfismo: modificador de la clase, atributos que
     *         ocultan a los del padre, @Override, y construccion de la tabla virtual
     *      5) cuerpos de atributos, metodos y constructores
     * @param clase
     * @param global
     * @return
     */
    public ManejadorErrores analizar(Clase clase, AmbitoGlobal global) {
        ManejadorErrores errores = new ManejadorErrores();

        Simbolo sClase = registrarFirma(clase, global, errores);
        if (sClase == null) {
            errores.imprimir();
            return errores;
        }
        AmbitoClase ambClase = registrarMiembros(clase, sClase, global, errores);

        enlazarHerencia(sClase, global, errores);
        enlazarPendientes(global);

        validarModificadorDeClase(clase, errores);
        validarAtributosOcultos(clase, sClase, errores);
        validarSobrescrituras(clase, sClase, errores);
        sClase.getTablaVirtual(); // deja los indices virtuales calculados para el C3D

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
        sClase.setModificador(clase.getModificador());
        sClase.setNombreClasePadre(clase.getClasePadre());
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
            sa.setModificador(a.getModificador());
            sa.setClaseDuena(sClase);
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
            sm.setModificador(m.getModificador());
            sm.setClaseDuena(sClase);
            registrarParametros(sm, m.getParametros(), global, errores);
            m.setSimbolo(sm);

            // Clave ESPECÍFICA: nombre#aridad#Tipo1#Tipo2...
            String claveEsp = construirClaveConTipos(m.getNombre(), sm.getParametros());
            if (!ambClase.declararMiembroConClave(claveEsp, sm)) {
                errores.reportar(m.getLinea(), m.getColumna(),
                        "Método duplicado: '" + m.getNombre() + "' con "
                                + m.getParametros().size() + " parámetros");
            } else {
                sClase.getMetodosDeclarados().add(sm);
            }
            /**
             * Clave GENÉRICA (nombre#aridad): primer método con esa aridad "gana".
             * Sirve solo como fallback para emitir "argumento incompatible" con la
             * firma más parecida cuando el match exacto por tipos falle.
             */
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
            sc.setModificador(c.getModificador());
            sc.setClaseDuena(sClase);
            registrarParametros(sc, c.getParametros(), global, errores);

            String claveEsp = construirClaveConTipos(nombreClase, sc.getParametros());
            if (!ambClase.declararMiembroConClave(claveEsp, sc)) {
                errores.reportar(c.getLinea(), c.getColumna(),
                        "Constructor duplicado: '" + nombreClase + "' con "
                                + c.getParametros().size() + " parámetros");
            } else {
                sClase.getConstructoresDeclarados().add(sc);
            }
            ambClase.declararMiembroConClave(nombreClase + "#" + c.getParametros().size(), sc);
        }

        return ambClase;
    }


    // HERENCIA

    /**
     * Enlaza la clase con su padre ("extends X"). Errores posibles:
     *  - la clase padre no existe (o no es una clase)
     *  - la clase hereda de si misma
     *  - herencia ciclica (A extends B, B extends A)
     * Si hay error no se enlaza: la clase se analiza como si no heredara, para no
     * generar errores en cascada ni ciclos infinitos al buscar miembros.
     * @param sClase
     * @param global
     * @param errores
     * @return
     */
    public boolean enlazarHerencia(Simbolo sClase, AmbitoGlobal global, ManejadorErrores errores) {
        String nombrePadre = sClase.getNombreClasePadre();
        if (nombrePadre == null) return true;

        int linea = sClase.getLinea();
        int columna = sClase.getColumna();

        if (nombrePadre.equals(sClase.getNombre())) {
            errores.reportar(linea, columna,
                    "La clase '" + sClase.getNombre() + "' no puede heredar de si misma");
            return false;
        }

        Simbolo padre = global.resolver(nombrePadre);
        if (padre == null || padre.getCategoria() != CategoriaSimbolo.CLASE) {
            errores.reportar(linea, columna,
                    "La clase padre '" + nombrePadre + "' de '" + sClase.getNombre() + "' no existe");
            return false;
        }
        /**
         * Ciclo: se sube desde el padre siguiendo los NOMBRES declarados en "extends" (no
         * los enlaces), porque alguna clase de la cadena puede tener su enlace pendiente
         * todavia. Si se vuelve a esta clase, enlazar cerraria el ciclo. El conjunto de
         * visitados evita quedarse girando en un ciclo que no incluya a esta clase.
         */
        java.util.Set<String> visitados = new java.util.HashSet<>();
        Simbolo c = padre;
        while (c != null && visitados.add(c.getNombre())) {
            if (c.getNombre().equals(sClase.getNombre())) {
                errores.reportar(linea, columna,
                        "Herencia ciclica: la cadena de herencia de '" + sClase.getNombre()
                                + "' vuelve a '" + sClase.getNombre() + "'");
                return false;
            }
            String siguiente = c.getNombreClasePadre();
            c = (siguiente == null) ? null : global.resolver(siguiente);
        }

        sClase.setClasePadre(padre);
        return true;
    }

    /**
     * Reintenta el enlace de las clases del ambito global que quedaron con la herencia
     * pendiente. Los errores se descartan: son problemas de otros archivos, que se
     * reportan cuando se analiza cada uno de ellos.
     * @param global
     */
    public void enlazarPendientes(AmbitoGlobal global) {
        ManejadorErrores descartable = new ManejadorErrores();
        for (Simbolo s : global.simbolosLocales()) {
            if (s.getCategoria() == CategoriaSimbolo.CLASE && s.tieneHerenciaPendiente()) {
                enlazarHerencia(s, global, descartable);
            }
        }
    }

    // Una clase de nivel superior solo puede ser public o sin modificador (igual que Java).
    private void validarModificadorDeClase(Clase clase, ManejadorErrores errores) {
        ModificadorAcceso m = clase.getModificador();
        if (m == ModificadorAcceso.PRIVATE || m == ModificadorAcceso.PROTECTED) {
            errores.reportar(clase.getLinea(), clase.getColumna(),
                    "La clase '" + clase.getNombre() + "' no puede ser " + m
                            + "; una clase solo puede ser public o sin modificador");
        }
    }

    /**
     * Un atributo con el mismo nombre que uno heredado se reporta como error.
     * Java lo permite (ocultamiento), pero aqui los campos se acceden por nombre en el C3D
     * ("this.edad"), asi que dos campos con el mismo nombre dentro del mismo objeto serian ambiguos para el backend.
     * @param clase
     * @param sClase
     * @param errores
     */
    private void validarAtributosOcultos(Clase clase, Simbolo sClase, ManejadorErrores errores) {
        Simbolo padre = sClase.getClasePadre();
        if (padre == null) return;
        for (Atributo a : clase.getAtributos()) {
            Simbolo heredado = padre.buscarMiembro(a.getNombre());
            if (heredado != null && heredado.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                errores.reportar(a.getLinea(), a.getColumna(),
                        "El atributo '" + a.getNombre() + "' ya existe en la clase padre '"
                                + heredado.getClaseDuena().getNombre() + "'");
            }
        }
    }

    /**
     * Reglas de polimorfismo para cada metodo propio:
     *  Con @Override:
     *      - la clase debe heredar de alguna
     *      - debe existir en la cadena de herencia un metodo con la misma firma (nombre + tipos de parametros)
     *      - ese metodo no puede ser private (no se hereda, no se puede sobrescribir)
     *      - el tipo de retorno debe ser el mismo (o una subclase, retorno covariante)
     *      - no puede reducir la visibilidad (public -> private, por ejemplo)
     *  Sin @Override:
     *      - si el metodo sobrescribe a uno heredado es error: el enunciado pide la
     *      anotacion como obligatoria para definir polimorfismo
     * @param clase
     * @param sClase
     * @param errores
     */
    private void validarSobrescrituras(Clase clase, Simbolo sClase, ManejadorErrores errores) {
        Simbolo padre = sClase.getClasePadre();

        for (Metodo m : clase.getMetodos()) {
            Simbolo propio = m.getSimbolo();
            if (propio == null) continue;
            boolean anotadoOverride = m.esOverride();

            Simbolo original = (padre == null) ? null : padre.buscarMiembro(propio.firma());
            if (original != null && original.getCategoria() != CategoriaSimbolo.METODO) original = null;

            if (!anotadoOverride) {
                if (original != null && original.getModificador() != ModificadorAcceso.PRIVATE) {
                    errores.reportar(m.getLinea(), m.getColumna(),
                            "El metodo '" + propio.firmaLegible() + "' sobrescribe al de '"
                                    + original.getClaseDuena().getNombre()
                                    + "' y debe llevar la anotacion @Override");
                }
                continue;
            }

            if (padre == null) {
                // Declaro extends pero el padre no se pudo enlazar: ese error ya se reporto
                // y no se agrega otro en cascada por cada @Override.
                if (sClase.getNombreClasePadre() != null) continue;
                errores.reportar(m.getLinea(), m.getColumna(),
                        "El metodo '" + propio.firmaLegible() + "' tiene @Override pero la clase '"
                                + sClase.getNombre() + "' no hereda de ninguna clase");
                continue;
            }
            if (original == null) {
                errores.reportar(m.getLinea(), m.getColumna(),
                        "El metodo '" + propio.firmaLegible() + "' tiene @Override pero no existe un metodo "
                                + "con esa firma en la clase padre '" + padre.getNombre() + "'");
                continue;
            }
            if (original.getModificador() == ModificadorAcceso.PRIVATE) {
                errores.reportar(m.getLinea(), m.getColumna(),
                        "El metodo '" + propio.firmaLegible() + "' no puede sobrescribir al de '"
                                + original.getClaseDuena().getNombre() + "' porque es private");
                continue;
            }
            if (!retornoCompatible(original.getTipo(), propio.getTipo())) {
                errores.reportar(m.getLinea(), m.getColumna(),
                        "El metodo '" + propio.firmaLegible() + "' debe retornar "
                                + nombreTipo(original.getTipo()) + " como en '"
                                + original.getClaseDuena().getNombre() + "', no "
                                + nombreTipo(propio.getTipo()));
            }
            if (Acceso.rango(propio.getModificador()) < Acceso.rango(original.getModificador())) {
                errores.reportar(m.getLinea(), m.getColumna(),
                        "El metodo '" + propio.firmaLegible() + "' no puede ser " + propio.getModificador()
                                + " porque sobrescribe a uno " + original.getModificador() + " de '"
                                + original.getClaseDuena().getNombre() + "'");
            }
        }
    }

    // Mismo tipo, o una subclase del tipo original (Animal crear() -> Perro crear()).
    private static boolean retornoCompatible(Tipo original, Tipo nuevo) {
        if (original == null || nuevo == null) return true;
        if (original.esDesconocido() || nuevo.esDesconocido()) return true;
        if (original.equals(nuevo)) return true;
        return original instanceof TipoClase o && nuevo instanceof TipoClase n && n.esSubtipoDe(o);
    }

    private static String nombreTipo(Tipo t) {
        return (t == null) ? "?" : t.nombre();
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
