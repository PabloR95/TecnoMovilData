package tecnomovil.app;

import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import tecnomovil.analisis.AnalizadorTransporte;
import tecnomovil.analisis.ResultadoAnalisis;
import tecnomovil.datos.GeneradorDatos;
import tecnomovil.datos.LectorCsv;
import tecnomovil.modelo.Registro;

/**
 * Punto de entrada de TecnoMovil Data.
 *
 * <p>Esta clase es la "capa imperativa": obtiene los datos, mide tiempos e imprime en consola
 * (efectos secundarios). Todo el procesamiento ocurre en {@link AnalizadorTransporte},
 * que es puramente funcional.</p>
 *
 * <p>Uso: {@code java -cp out tecnomovil.app.Main [usuarios]} o
 * {@code java -cp out tecnomovil.app.Main --csv datos/muestra_registros.csv}</p>
 */
public final class Main {

    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final int USUARIOS_POR_DEFECTO = 450_000;   // pasajeros diarios de TecnoValle
    private static final long SEMILLA = 2026L;
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 30);

    /** Umbral de ocupación a partir del cual una ruta es crítica (90 % de su capacidad). */
    private static final double UMBRAL_CRITICO = 0.90;

    /** Capacidad horaria simulada por ruta = buses por hora x pasajeros por bus. */
    private static final Map<String, Integer> CAPACIDAD_HORARIA = Map.of(
            "T1", 220 * 150,   // troncal norte-sur: 220 buses articulados/h x 150 pasajeros
            "T2", 220 * 150,   // troncal oriente-occidente
            "C3", 140 * 150,   // circular centro
            "A1", 140 * 90,    // alimentadora norte: buses padrón de 90 pasajeros
            "A2", 150 * 90,    // alimentadora sur
            "E1", 120 * 90);   // expresa aeropuerto

    /** Función que se entrega como parámetro al análisis de sobrecarga (orden superior). */
    private static final Function<String, Integer> CAPACIDAD =
            ruta -> CAPACIDAD_HORARIA.getOrDefault(ruta, 10_000);

    /** Resultado de una tarea junto con su tiempo de ejecución. */
    private record Medicion<T>(T valor, long ms) {
    }

    public static void main(String[] args) {
        encabezado();

        Medicion<List<Registro>> carga = medir(() -> cargarDatos(args));
        List<Registro> registros = carga.valor();
        long entradas = registros.stream().filter(Registro::esEntrada).count();
        System.out.printf(ES, "Registros cargados: %,d (entradas: %,d | salidas: %,d) en %,d ms%n",
                registros.size(), entradas, registros.size() - entradas, carga.ms());

        int hashAntes = registros.hashCode();

        // Calentamiento de la JVM (JIT) y medición: mejor de 3 ejecuciones de cada modo.
        Supplier<ResultadoAnalisis> secuencial =
                () -> AnalizadorTransporte.analizar(registros, CAPACIDAD, UMBRAL_CRITICO, false);
        Supplier<ResultadoAnalisis> paralelo =
                () -> AnalizadorTransporte.analizar(registros, CAPACIDAD, UMBRAL_CRITICO, true);
        secuencial.get();
        paralelo.get();
        Medicion<ResultadoAnalisis> sec = mejorDe(3, secuencial);
        Medicion<ResultadoAnalisis> par = mejorDe(3, paralelo);

        imprimirReporte(sec.valor());
        imprimirVerificaciones(registros, hashAntes, sec, par);
    }

    // ------------------------------------------------------------------------------------
    // Carga de datos y medición
    // ------------------------------------------------------------------------------------

    private static List<Registro> cargarDatos(String[] args) {
        if (args.length >= 2 && args[0].equals("--csv")) {
            System.out.println("Leyendo registros desde " + args[1] + " ...");
            return LectorCsv.leer(Path.of(args[1]));
        }
        int usuarios = args.length >= 1
                ? Integer.parseInt(args[0].replace("_", ""))
                : USUARIOS_POR_DEFECTO;
        System.out.printf(ES, "Generando datos simulados: %,d usuarios, semilla %d, fecha %s ...%n",
                usuarios, SEMILLA, FECHA);
        return GeneradorDatos.generar(usuarios, SEMILLA, FECHA);
    }

    /** Función de orden superior: ejecuta la tarea recibida y mide su duración. */
    private static <T> Medicion<T> medir(Supplier<T> tarea) {
        long inicio = System.nanoTime();
        T valor = tarea.get();
        return new Medicion<>(valor, (System.nanoTime() - inicio) / 1_000_000);
    }

    private static <T> Medicion<T> mejorDe(int veces, Supplier<T> tarea) {
        return IntStream.range(0, veces)
                .mapToObj(i -> medir(tarea))
                .min(Comparator.comparingLong(Medicion::ms))
                .orElseThrow();
    }

    // ------------------------------------------------------------------------------------
    // Presentación (efectos secundarios: impresión en consola)
    // ------------------------------------------------------------------------------------

    private static void encabezado() {
        linea('=');
        System.out.println(" TecnoMovil Data - Módulo funcional de procesamiento diario");
        System.out.printf(ES, " Alcaldía de TecnoValle | Java %s | %d núcleos (paralelismo %d)%n",
                Runtime.version().feature(), Runtime.getRuntime().availableProcessors(),
                ForkJoinPool.commonPool().getParallelism());
        linea('=');
    }

    private static void imprimirReporte(ResultadoAnalisis r) {
        seccion("a) Afluencia por estación (ingresos del día)");
        System.out.printf(ES, "   %-20s %12s %16s%n", "Estación", "Ingresos", "Usuarios únicos");
        AnalizadorTransporte.topN(r.afluenciaPorEstacion(), 10).forEach(e ->
                System.out.printf(ES, "   %-20s %,12d %,16d%n",
                        e.getKey(), e.getValue(), r.usuariosUnicosPorEstacion().get(e.getKey())));
        System.out.printf(ES, "   (top 10 de %d estaciones)%n", r.afluenciaPorEstacion().size());

        seccion("b) Flujo por hora del día y horas pico");
        long maximo = r.flujoPorHora().values().stream().mapToLong(Long::longValue).max().orElse(1);
        r.flujoPorHora().forEach((hora, total) ->
                System.out.printf(ES, "   %02d:00 %-40s %,10d%n",
                        hora, barra(total, maximo, 40), total));
        System.out.println("   Horas pico: " + r.horasPico().stream()
                .map(e -> String.format(ES, "%02d:00 (%,d registros)", e.getKey(), e.getValue()))
                .collect(joining(", ")));

        seccion("c) Rutas más utilizadas (viajes iniciados)");
        long totalViajes = r.rutasMasUtilizadas().stream().mapToLong(Map.Entry::getValue).sum();
        IntStream.range(0, r.rutasMasUtilizadas().size()).forEach(i -> {
            Map.Entry<String, Long> e = r.rutasMasUtilizadas().get(i);
            System.out.printf(ES, "   %d. %-4s %,10d viajes  %5.1f %%%n",
                    i + 1, e.getKey(), e.getValue(), 100.0 * e.getValue() / totalViajes);
        });

        seccion("d) Patrones de viaje por usuario (muestra)");
        r.patronesDeViaje().entrySet().stream().limit(5).forEach(e ->
                System.out.printf("   %s: %s%n", e.getKey(), String.join(" -> ", e.getValue())));
        System.out.printf(ES, "   (patrones generados para %,d usuarios)%n",
                r.patronesDeViaje().size());

        seccion("e) Tiempo promedio entre estaciones consecutivas");
        System.out.printf(ES, "   Tramos analizados: %,d | promedio general: %.1f min%n",
                r.tiempos().totalTramos(), r.tiempos().promedioMinutos());
        System.out.println("   Tramos con mayor tiempo promedio:");
        AnalizadorTransporte.topN(r.tiempos().promedioPorTramo(), 5).forEach(e ->
                System.out.printf(ES, "     %-38s %5.1f min%n", e.getKey(), e.getValue()));

        seccion(String.format(ES,
                "f) Detección de sobrecarga (umbral: %.0f %% de la capacidad horaria)",
                UMBRAL_CRITICO * 100));
        System.out.printf(ES, "   %-5s %9s %13s %10s %10s  %s%n",
                "Ruta", "Hora pico", "Ingresos/h", "Capacidad", "Ocupación", "Estado");
        r.sobrecarga().forEach(s -> System.out.printf(ES,
                "   %-5s     %02d:00 %,13d %,10d %9.1f %%  %s%n",
                s.ruta(), s.horaPico(), s.ingresosHoraPico(), s.capacidadHoraria(),
                s.ocupacion() * 100, s.estado()));
    }

    private static void imprimirVerificaciones(List<Registro> registros, int hashAntes,
                                               Medicion<ResultadoAnalisis> sec,
                                               Medicion<ResultadoAnalisis> par) {
        seccion("Rendimiento e integridad");
        System.out.printf(ES, "   Procesamiento secuencial (stream):         %,7d ms%n", sec.ms());
        System.out.printf(ES, "   Procesamiento paralelo (parallelStream):   %,7d ms  (%.2fx)%n",
                par.ms(), (double) sec.ms() / Math.max(1, par.ms()));
        System.out.println("   Resultado secuencial == paralelo:          "
                + siNo(sec.valor().equals(par.valor())));
        System.out.println("   Lista original sin cambios (hashCode):     "
                + siNo(hashAntes == registros.hashCode()));
        System.out.println("   Lista original rechaza add():              "
                + siNo(lanzaUnsupported(() -> registros.add(registros.get(0)))));
        System.out.println("   Resultados no modificables (put):          "
                + siNo(lanzaUnsupported(() -> sec.valor().afluenciaPorEstacion().put("X", 0L))));
        linea('=');
    }

    /** Función pura: barra proporcional de caracteres '#'. */
    static String barra(long valor, long maximo, int ancho) {
        return "#".repeat((int) Math.round((double) valor * ancho / maximo));
    }

    private static boolean lanzaUnsupported(Runnable accion) {
        try {
            accion.run();
            return false;
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }

    private static String siNo(boolean valor) {
        return valor ? "SÍ" : "NO";
    }

    private static void seccion(String titulo) {
        System.out.println();
        System.out.println(titulo);
        linea('-');
    }

    private static void linea(char c) {
        System.out.println(String.valueOf(c).repeat(78));
    }
}
