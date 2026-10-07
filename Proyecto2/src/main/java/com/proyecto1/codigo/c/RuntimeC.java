package com.proyecto1.codigo.c;

/**
 * Runtime C mínimo del compilador. Se inyecta al principio del archivo .c generado
 * (típicamente por el OrquestadorC3DaC) para cubrir operaciones que el C3D expone
 * pero C puro no tiene:
 * t_read_string()}: lee una línea completa de stdin (incluidos espacios)
 *       y devuelve un char* nuevo en heap. Es lo que usa Z para readln()
 *       y lo que usa Y/PigLatin para read sobre char*.
 * rt_concat(a, b): concatena dos strings en un buffer nuevo. Es lo que
 *       usa el traductor cuando traduce a + b sobre char* (en C puro + no concatena cadenas).
 * rt_strcmp(a, b): compara dos strings con manejo de NULL
 *       (nuestro lenguaje admite null, strcmp de la libc no).
 *       Es lo que usa el traductor cuando traduce == / != sobre
 *       char*.
 * rt_int_to_string(n) y rt_double_to_string(d): convierten un
 *       valor numérico a un char* nuevo en heap. Se usan cuando una
 *       concatenación a + b mezcla un string con un número: en vez de pasarle
 *       el número crudo a rt_concat (que espera const char*), el
 *       traductor envuelve el operando numérico en su conversión.
 * rt_print_int, rt_print_double, rt_print_string y sus
 *       variantes rt_println_*: impresión tipada sin formato. El orquestador
 *       las usa para traducir las llamadas rt_print / rt_println
 *       que emite Z (que no llevan formato explícito).
 *
 *Todas las funciones del runtime son static para no ensuciar el namespace
 * global del C generado. Si el programa del usuario declara una función con el mismo
 * nombre (poco probable dado el prefijo rt_}, el linker se queja, lo cual es
 * deseable.
 */
public final class RuntimeC implements ProveedorRuntime {

    // Instancia singleton para pasar al orquestador sin crear objetos extra.
    public static final RuntimeC INSTANCIA = new RuntimeC();

    private RuntimeC() {}

    /**
     * Devuelve el código C completo del runtime, listo para inyectarse tras los
     * #include estándar. Incluye <stdio.h>, <stdlib.h> y <string.h> por seguridad (por si el orquestador se olvida).
     */
    @Override
    public String codigo() {
        return """
                /* ==== Runtime C generado ==== */
                #include <stdio.h>
                #include <stdlib.h>
                #include <string.h>
                #include <stdbool.h>

                /* Lee una línea completa de stdin, sin el '\\n'. Devuelve un char* en heap. */
                static char* rt_read_string(void) {
                    char buf[4096];
                    if (fgets(buf, sizeof(buf), stdin) == NULL) return NULL;
                    size_t n = strlen(buf);
                    if (n > 0 && buf[n-1] == '\\n') buf[n-1] = '\\0';
                    char* r = (char*)malloc(n + 1);
                    if (r) memcpy(r, buf, n + 1);
                    return r;
                }

                /* Concatena dos strings en un buffer nuevo. Trata NULL como "". */
                static char* rt_concat(const char* a, const char* b) {
                    size_t la = a ? strlen(a) : 0;
                    size_t lb = b ? strlen(b) : 0;
                    char* r = (char*)malloc(la + lb + 1);
                    if (!r) return NULL;
                    if (la) memcpy(r, a, la);
                    if (lb) memcpy(r + la, b, lb);
                    r[la + lb] = '\\0';
                    return r;
                }

                /* Compara strings con manejo de NULL. Devuelve <0, 0, >0. */
                static int rt_strcmp(const char* a, const char* b) {
                    if (a == NULL && b == NULL) return 0;
                    if (a == NULL) return -1;
                    if (b == NULL) return  1;
                    return strcmp(a, b);
                }

                /* ==== Conversión de numéricos a string (para concatenaciones mixtas) ==== */
                /* Convierte un int a un char* nuevo en heap (llamador libera o ignora). */
                static char* rt_int_to_string(int n) {
                    char buf[32];
                    snprintf(buf, sizeof(buf), "%d", n);
                    size_t len = strlen(buf);
                    char* r = (char*)malloc(len + 1);
                    if (r) memcpy(r, buf, len + 1);
                    return r;
                }

                /* Convierte un double a un char* nuevo en heap (formato %g, sin ceros extra). */
                static char* rt_double_to_string(double d) {
                    char buf[64];
                    snprintf(buf, sizeof(buf), "%g", d);
                    size_t len = strlen(buf);
                    char* r = (char*)malloc(len + 1);
                    if (r) memcpy(r, buf, len + 1);
                    return r;
                }
                
                static char* rt_char_to_string(char c) {
                    char* r = (char*)malloc(2);
                    if (r) { r[0] = c; r[1] = '\\0'; }
                    return r;
                }

                /* ==== Impresión tipada (para las llamadas rt_print* de Z) ==== */
                static void rt_print_int(int x)             { printf("%d",  x); }
                static void rt_print_double(double x)       { printf("%lf", x); }
                static void rt_print_string(const char* s)  { printf("%s",  s ? s : "(null)"); }
                static void rt_print_char(char c)           { printf("%c",  c); }
                static void rt_print_bool(int b)            { printf("%d",  b); }

                static void rt_println_int(int x)           { printf("%d\\n",  x); }
                static void rt_println_double(double x)     { printf("%lf\\n", x); }
                static void rt_println_string(const char* s){ printf("%s\\n",  s ? s : "(null)"); }
                static void rt_println_char(char c)         { printf("%c\\n",  c); }
                static void rt_println_bool(int b)          { printf("%d\\n",  b); }

                /* ==== Fin del runtime ==== */
                """;
    }
}