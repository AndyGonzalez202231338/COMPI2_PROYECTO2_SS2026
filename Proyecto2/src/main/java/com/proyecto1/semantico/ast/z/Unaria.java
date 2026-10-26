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
                if (operando instanceof AccesoCampo ac) {
                    ResultadoC3D base = ac.getObjeto().generarC3D(generador);
                    String viejoC = generador.nuevoTemporal();
                    Tipo tc = ac.getTipoCampo() != null ? ac.getTipoCampo() : TipoPrimitivo.DESCONOCIDO;
                    String tipoStr = tc != TipoPrimitivo.DESCONOCIDO ? tc.nombre() : null;
                    boolean esPuntero = (tc != TipoPrimitivo.DESCONOCIDO && tc.esArreglo());
                    generador.emitirCargaCampo(base.getLugar(), ac.getCampo(), viejoC, tipoStr, esPuntero, ac.getClaseObjeto());

                    String nuevoC = generador.nuevoTemporal();
                    generador.emitirBinaria(operador.equals("++") ? "+" : "-", viejoC, "1", nuevoC);
                    generador.emitirGuardarCampo(base.getLugar(), ac.getCampo(), nuevoC, ac.getClaseObjeto());

                    return ResultadoC3D.temporal(prefijo ? nuevoC : viejoC, tc);
                }

                if (operando instanceof Indice ix) {
                    ResultadoC3D baseA = ix.getArreglo().generarC3D(generador);
                    ResultadoC3D idxA  = ix.getIndice().generarC3D(generador);
                    String viejoI = generador.nuevoTemporal();

                    generador.emitirCargaIndice(baseA.getLugar(), idxA.getLugar(), viejoI);

                    String nuevoI = generador.nuevoTemporal();
                    generador.emitirBinaria(operador.equals("++") ? "+" : "-", viejoI, "1", nuevoI);
                    generador.emitirGuardarIndice(baseA.getLugar(), idxA.getLugar(), nuevoI);

                    Tipo ti = ix.getTipoElemento() != null ? ix.getTipoElemento() : TipoPrimitivo.DESCONOCIDO;
                    return ResultadoC3D.temporal(prefijo ? nuevoI : viejoI, ti);
                }

                if (operando instanceof Identificador id) {
                    Ambito ambito = generador.getAmbito();
                    if (ambito != null) {
                        Simbolo s = ambito.resolver(id.getNombre());
                        if (s != null && s.getCategoria() == CategoriaSimbolo.ATRIBUTO) {
                            String viejoC = generador.nuevoTemporal();
                            Tipo tc2 = s.getTipo() != null ? s.getTipo() : TipoPrimitivo.DESCONOCIDO;
                            String tipoStr2 = tc2 != TipoPrimitivo.DESCONOCIDO ? tc2.nombre() : null;
                            boolean esPuntero2 = (tc2 != TipoPrimitivo.DESCONOCIDO && tc2.esArreglo());

                            generador.emitirCargaCampo("this", id.getNombre(), viejoC, tipoStr2, esPuntero2, generador.getClaseActual());

                            String nuevoC = generador.nuevoTemporal();
                            generador.emitirBinaria(operador.equals("++") ? "+" : "-", viejoC, "1", nuevoC);
                            generador.emitirGuardarCampo("this", id.getNombre(), nuevoC, generador.getClaseActual());

                            return ResultadoC3D.temporal(prefijo ? nuevoC : viejoC, tc2);
                        }
                    }
                }

                ResultadoC3D o = operando.generarC3D(generador);
                String lugar = o.getLugar();

                if (!prefijo) {
                    String viejo = generador.nuevoTemporal();
                    generador.emitirBinaria("+", lugar, "0", viejo);
                    String nuevo = generador.nuevoTemporal();
                    generador.emitirBinaria(operador.equals("++") ? "+" : "-", lugar, "1", nuevo);
                    generador.emitirAsignacion(nuevo, lugar);
                    return ResultadoC3D.temporal(viejo, o.getTipo());
                }

                String nuevo = generador.nuevoTemporal();
                generador.emitirBinaria(operador.equals("++") ? "+" : "-", lugar, "1", nuevo);
                generador.emitirAsignacion(nuevo, lugar);
                return ResultadoC3D.temporal(nuevo, o.getTipo());

            }
            default:
                throw new UnsupportedOperationException("Operador unario no soportado: " + operador);
        }
    }
}