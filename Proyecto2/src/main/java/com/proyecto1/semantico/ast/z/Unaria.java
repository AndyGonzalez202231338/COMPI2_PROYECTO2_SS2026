package com.proyecto1.semantico.ast.z;

import com.proyecto1.semantico.ast.GeneradorC3D;
import com.proyecto1.semantico.ast.ResultadoC3D;
import com.proyecto1.semantico.errores.ManejadorErrores;
import com.proyecto1.semantico.tabla.Ambito;
import com.proyecto1.semantico.tabla.CategoriaSimbolo;
import com.proyecto1.semantico.tabla.Simbolo;
import com.proyecto1.semantico.tipos.Tipo;
import com.proyecto1.semantico.tipos.TipoPrimitivo;
import com.proyecto1.semantico.tipos.Tipos;


public final class Unaria extends NodoZ implements ExpresionZ {

    private final String operador;
    private final ExpresionZ operando;
    private final boolean prefijo;

    public Unaria(String operador, ExpresionZ operando, boolean prefijo, int linea, int columna) {
        super(linea, columna);
        this.operador = operador;
        this.operando = operando;
        this.prefijo = prefijo;
    }

    public String getOperador() { return operador; }
    public ExpresionZ getOperando() { return operando; }
    public boolean isPrefijo() { return prefijo; }

    @Override
    public Tipo verificar(Ambito ambito, ManejadorErrores errores) {
        Tipo t = operando.verificar(ambito, errores);
        switch (operador) {
            case "!":
                if (!Tipos.esBooleano(t))
                    errores.reportar(linea, columna, "'!' requiere bool, se recibió " + t.nombre());
                return TipoPrimitivo.BOOL;
            case "-":
                if (!t.esNumerico() && !t.esDesconocido())
                    errores.reportar(linea, columna, "'-' requiere numérico, se recibió " + t.nombre());
                return t;
            case "++": case "--":
                if (!Tipos.admiteIncrementoDecremento(t))
                    errores.reportar(linea, columna,
                            "'" + operador + "' requiere numérico, se recibió " + t.nombre());
                return t;
        }
        return TipoPrimitivo.DESCONOCIDO;
    }

    @Override
    public ResultadoC3D generarC3D(GeneradorC3D generador) {
        switch (operador) {
            case "!":
            case "-": {
                ResultadoC3D o = operando.generarC3D(generador);
                String t = generador.nuevoTemporal();
                generador.emitirUnaria(operador, o.getLugar(), t);
                Tipo tipo = operador.equals("!") ? TipoPrimitivo.BOOL : o.getTipo();
                return ResultadoC3D.temporal(t, tipo);
            }
            case "++":
            case "--": {
                if (!(operando instanceof Identificador id)) {
                    throw new UnsupportedOperationException(
                            "'" + operador + "' sobre campos o arreglos: pendiente en C3D (Z)");
                }

                // Resolver categoría del identificador: ¿es un atributo (this.x)?
                boolean esAtributo = false;
                Tipo tipoVar = TipoPrimitivo.DESCONOCIDO;
                Ambito amb = generador.getAmbito();
                if (amb != null) {
                    Simbolo s = amb.resolver(id.getNombre());
                    if (s != null) {
                        tipoVar = s.getTipo();
                        esAtributo = (s.getCategoria() == CategoriaSimbolo.ATRIBUTO);
                    }
                }

                // Cargar el valor actual al "lugar del valor" desde el que se opera.
                String lugarValor;
                if (esAtributo) {
                    lugarValor = generador.nuevoTemporal();
                    generador.emitirCargaCampo("this", id.getNombre(), lugarValor);
                } else {
                    lugarValor = id.getNombre();
                }

                String opBinario = operador.equals("++") ? "+" : "-";

                if (prefijo) {
                    String t = generador.nuevoTemporal();
                    generador.emitirBinaria(opBinario, lugarValor, "1", t);
                    guardarEnDestino(generador, esAtributo, id.getNombre(), t);
                    return ResultadoC3D.temporal(t, tipoVar);
                }

                // Postfija: primero copiamos el valor VIEJO a un temporal que será
                // el resultado de la expresión.
                String viejo = generador.nuevoTemporal();
                generador.emitirAsignacion(lugarValor, viejo);

                String nuevo = generador.nuevoTemporal();
                generador.emitirBinaria(opBinario, lugarValor, "1", nuevo);
                guardarEnDestino(generador, esAtributo, id.getNombre(), nuevo);
                return ResultadoC3D.temporal(viejo, tipoVar);
            }
            default:
                throw new UnsupportedOperationException("Operador unario no soportado: " + operador);
        }
    }

    /** Escribe "v" en el destino: this.<campo> = v si es atributo, x = v si es local. */
    private static void guardarEnDestino(GeneradorC3D generador, boolean esAtributo,
                                         String nombre, String v) {
        if (esAtributo) {
            generador.emitirGuardarCampo("this", nombre, v);
        } else {
            generador.emitirAsignacion(v, nombre);
        }
    }
}