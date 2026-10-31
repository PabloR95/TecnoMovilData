package tecnomovil.analisis;

import static java.util.stream.Collectors.averagingLong;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import tecnomovil.modelo.EstadisticaTiempos;
import tecnomovil.modelo.EstadoRuta;
import tecnomovil.modelo.Registro;
import tecnomovil.modelo.Tramo;

/**
 * Núcleo funcional de TecnoMovil Data.
 *
 * <p>Todas las operaciones son <b>funciones puras</b>: su resultado depende solo de sus
 * parámetros, no leen ni escriben estado compartido, no hacen entrada/salida y nunca
 * modifican la lista recibida. Cada una devuelve colecciones nuevas y no modificables.
 * Por eso pueden ejecutarse con {@code stream()} o con {@code parallelStream()} y producir
 * exactamente el mismo resultado (parámetro {@code paralelo}).</p>
 */
public final class AnalizadorTransporte {

    private AnalizadorTransporte() {
        // Clase de utilidades: no se instancia.
    }

    /** Composición de funciones: Registro -> LocalDateTime -> hora del día (0-23). */
    public static final Function<Registro, Integer> HORA_DEL_DIA =
            ((Function<Registro, LocalDateTime>) Registro::timestamp)
                    .andThen(LocalDateTime::getHour);

    /** Predicado reutilizable: el registro corresponde a un ingreso a la estación. */
    public static final Predicate<Registro> ES_ENTRADA = Registro::esEntrada;

    // ------------------------------------------------------------------------------------
    // Utilidades de orden superior
    // ------------------------------------------------------------------------------------

    /** Crea el flujo secuencial o paralelo sobre cualquier colección, sin modificarla. */
    static <T> Stream<T> flujo(Collection<T> datos, boolean paralelo) {
        return paralelo ? datos.parallelStream() : datos.stream();
    }

    /**
     * Función de orden superior: recibe un predicado (qué registros contar) y una función
     * clasificadora (por qué clave agrupar) y devuelve el conteo por clave.
     */
    public static <K> Map<K, Long> contarPor(List<Registro> registros,
                                            Predicate<? super Registro> filtro,
                                            Function<? super Registro, ? extends K> clave,
                                            boolean paralelo) {
        return flujo(registros, paralelo)
                .filter(filtro)
                .collect(collectingAndThen(groupingBy(clave, counting()),
                        Collections::unmodifiableMap));
    }

    /** Ranking descendente (top N); en empate ordena por clave para ser determinista. */
    public static <K extends Comparable<? super K>, V extends Comparable<? super V>>
            List<Map.Entry<K, V>> topN(Map<K, V> valores, int n) {
        return valores.entrySet().stream()
                .sorted(Map.Entry.<K, V>comparingByValue().reversed()
                        .thenComparing(Map.Entry.<K, V>comparingByKey()))
                .limit(n)
                .map(e -> Map.entry(e.getKey(), e.getValue()))
                .toList();
    }

    // ------------------------------------------------------------------------------------
    // a) Afluencia por estación
    // ------------------------------------------------------------------------------------

    /** Cantidad de ingresos (registros de ENTRADA) por estación durante el día. */
    public static Map<String, Long> afluenciaPorEstacion(List<Registro> registros,
                                                         boolean paralelo) {
        return contarPor(registros, ES_ENTRADA, Registro::estacion, paralelo);
    }

    /** Cantidad de usuarios distintos que ingresaron a cada estación. */
    public static Map<String, Integer> usuariosUnicosPorEstacion(List<Registro> registros,
                                                                 boolean paralelo) {
        return flujo(registros, paralelo)
                .filter(ES_ENTRADA)
                .collect(collectingAndThen(
                        groupingBy(Registro::estacion,
                                collectingAndThen(mapping(Registro::idUsuario, toSet()),
                                        Set::size)),
                        Collections::unmodifiableMap));
    }

    // ------------------------------------------------------------------------------------
    // b) Horas pico
    // ------------------------------------------------------------------------------------

    /** Flujo total (entradas + salidas) agrupado por hora del día, ordenado por hora. */
    public static SortedMap<Integer, Long> flujoPorHora(List<Registro> registros,
                                                        boolean paralelo) {
        return flujo(registros, paralelo)
                .collect(collectingAndThen(
                        groupingBy(HORA_DEL_DIA, TreeMap::new, counting()),
                        Collections::unmodifiableSortedMap));
    }

    /** Las n horas con mayor flujo de registros. */
    public static List<Map.Entry<Integer, Long>> horasPico(List<Registro> registros, int n,
                                                           boolean paralelo) {
        return topN(flujoPorHora(registros, paralelo), n);
    }

    // ------------------------------------------------------------------------------------
    // c) Rutas más utilizadas
    // ------------------------------------------------------------------------------------

    /** Volumen de uso por ruta: número de viajes iniciados (entradas) en cada ruta. */
    public static Map<String, Long> usoPorRuta(List<Registro> registros, boolean paralelo) {
        return contarPor(registros, ES_ENTRADA, Registro::ruta, paralelo);
    }

    /** Las n rutas con mayor volumen de uso. */
    public static List<Map.Entry<String, Long>> rutasMasUtilizadas(List<Registro> registros,
                                                                   int n, boolean paralelo) {
        return topN(usoPorRuta(registros, paralelo), n);
    }

    // ------------------------------------------------------------------------------------
    // d) Patrones de viaje por usuario
    // ------------------------------------------------------------------------------------

    /**
     * Para cada usuario, las estaciones visitadas en el orden en que aparecen en los registros.
     * groupingBy conserva el orden de encuentro dentro de cada grupo incluso en paralelo.
     */
    public static Map<String, List<String>> patronesDeViaje(List<Registro> registros,
                                                            boolean paralelo) {
        return flujo(registros, paralelo)
                .collect(collectingAndThen(
                        groupingBy(Registro::idUsuario, TreeMap::new,
                                mapping(Registro::estacion, toUnmodifiableList())),
                        Collections::unmodifiableSortedMap));
    }

    // ------------------------------------------------------------------------------------
    // e) Tiempo promedio entre estaciones
    // ------------------------------------------------------------------------------------

    /** Todos los tramos (entrada en A seguida de salida en B) de todos los usuarios. */
    public static List<Tramo> tramos(List<Registro> registros, boolean paralelo) {
        Map<String, List<Registro>> porUsuario = flujo(registros, paralelo)
                .collect(groupingBy(Registro::idUsuario));
        return flujo(porUsuario.values(), paralelo)
                .flatMap(AnalizadorTransporte::tramosDeUsuario)
                .toList();
    }

    /**
     * Función pura: ordena (en una lista nueva) los eventos de un usuario por fecha y hora,
     * y forma pares consecutivos (i, i+1). Solo los pares que son viaje generan un Tramo.
     */
    static Stream<Tramo> tramosDeUsuario(List<Registro> eventos) {
        List<Registro> ordenados = eventos.stream()
                .sorted(Comparator.comparing(Registro::timestamp))
                .toList();
        return IntStream.range(0, ordenados.size() - 1)
                .mapToObj(i -> Tramo.entre(ordenados.get(i), ordenados.get(i + 1)))
                .flatMap(Optional::stream);
    }

    /** Promedio general y promedio por tramo, en minutos. */
    public static EstadisticaTiempos tiempoEntreEstaciones(List<Registro> registros,
                                                           boolean paralelo) {
        List<Tramo> tramos = tramos(registros, paralelo);
        LongSummaryStatistics general = flujo(tramos, paralelo)
                .mapToLong(Tramo::segundos)
                .summaryStatistics();
        Map<String, Double> porTramo = flujo(tramos, paralelo)
                .collect(groupingBy(Tramo::nombre,
                        collectingAndThen(averagingLong(Tramo::segundos), seg -> seg / 60.0)));
        return new EstadisticaTiempos(general.getCount(), general.getAverage() / 60.0, porTramo);
    }

    // ------------------------------------------------------------------------------------
    // f) Detección de sobrecarga en rutas
    // ------------------------------------------------------------------------------------

    /**
     * Función de orden superior: recibe la función que entrega la capacidad horaria simulada
     * de cada ruta y marca como crítica toda ruta cuya ocupación en su hora pico supere
     * el umbral.
     *
     * @param capacidadHoraria ruta -> pasajeros por hora que la ruta puede transportar
     * @param umbral           ocupación máxima aceptada (0.90 = 90 %)
     */
    public static List<EstadoRuta> detectarSobrecarga(List<Registro> registros,
                                                      Function<String, Integer> capacidadHoraria,
                                                      double umbral,
                                                      boolean paralelo) {
        Map<String, Map<Integer, Long>> ingresosPorRutaYHora = flujo(registros, paralelo)
                .filter(ES_ENTRADA)
                .collect(groupingBy(Registro::ruta, groupingBy(HORA_DEL_DIA, counting())));
        return ingresosPorRutaYHora.entrySet().stream()
                .map(e -> evaluarRuta(e.getKey(), e.getValue(),
                        capacidadHoraria.apply(e.getKey()), umbral))
                .sorted(Comparator.comparingDouble(EstadoRuta::ocupacion).reversed()
                        .thenComparing(EstadoRuta::ruta))
                .toList();
    }

    /** Función pura: calcula la hora pico, la ocupación y el estado de una ruta. */
    static EstadoRuta evaluarRuta(String ruta, Map<Integer, Long> ingresosPorHora,
                                  int capacidad, double umbral) {
        Comparator<Map.Entry<Integer, Long>> porIngresos = Map.Entry.comparingByValue();
        Map.Entry<Integer, Long> pico = ingresosPorHora.entrySet().stream()
                .max(porIngresos.thenComparing(e -> -e.getKey())) // empate: hora más temprana
                .orElseThrow();
        long totalDia = ingresosPorHora.values().stream().mapToLong(Long::longValue).sum();
        double ocupacion = (double) pico.getValue() / capacidad;
        return new EstadoRuta(ruta, totalDia, pico.getKey(), pico.getValue(), capacidad,
                ocupacion, ocupacion > umbral);
    }

    // ------------------------------------------------------------------------------------
    // Ejecución completa del módulo
    // ------------------------------------------------------------------------------------

    /** Ejecuta las seis tareas (a-f) y agrupa los resultados en un solo valor inmutable. */
    public static ResultadoAnalisis analizar(List<Registro> registros,
                                             Function<String, Integer> capacidadHoraria,
                                             double umbral,
                                             boolean paralelo) {
        return new ResultadoAnalisis(
                afluenciaPorEstacion(registros, paralelo),
                usuariosUnicosPorEstacion(registros, paralelo),
                flujoPorHora(registros, paralelo),
                horasPico(registros, 3, paralelo),
                rutasMasUtilizadas(registros, Integer.MAX_VALUE, paralelo),
                patronesDeViaje(registros, paralelo),
                tiempoEntreEstaciones(registros, paralelo),
                detectarSobrecarga(registros, capacidadHoraria, umbral, paralelo));
    }
}
