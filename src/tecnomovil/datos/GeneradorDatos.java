package tecnomovil.datos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import tecnomovil.modelo.Accion;
import tecnomovil.modelo.Registro;

/**
 * Generador determinista de registros simulados del sistema de transporte de TecnoValle.
 *
 * <p>No usa {@code java.util.Random} (que guarda estado mutable). Cada valor "aleatorio" se
 * obtiene con una función hash pura de (semilla, usuario, viaje, campo): la misma entrada
 * produce siempre el mismo valor. Así la generación es reproducible, no tiene efectos
 * secundarios y puede ejecutarse en paralelo sin condiciones de carrera.</p>
 */
public final class GeneradorDatos {

    private GeneradorDatos() {
    }

    /** Ruta simulada: código, peso relativo de demanda y estaciones en orden de recorrido. */
    private record Ruta(String codigo, int peso, List<String> estaciones) {
    }

    /** Trayecto de un viaje: ruta y posición de las estaciones de origen y destino. */
    private record Trayecto(Ruta ruta, int origen, int destino) {
        Trayecto invertido() {
            return new Trayecto(ruta, destino, origen);
        }

        int saltos() {
            return Math.abs(destino - origen);
        }
    }

    private static final List<Ruta> RUTAS = List.of(
            new Ruta("T1", 30, List.of("Portal Norte", "Calle 100", "Estación Central",
                    "Parque Bolívar", "Hospital", "Universidad", "Portal Sur")),
            new Ruta("T2", 25, List.of("Portal Oriente", "Mercado", "Estación Central",
                    "Plaza Mayor", "Terminal", "Portal Occidente")),
            new Ruta("C3", 15, List.of("Estación Central", "Catedral", "Parque Bolívar",
                    "Plaza Mayor", "Mercado")),
            new Ruta("A1", 12, List.of("Portal Norte", "Los Pinos", "Villa Verde", "El Bosque")),
            new Ruta("A2", 10, List.of("Portal Sur", "San José", "La Esperanza", "Altos del Sur")),
            new Ruta("E1", 8, List.of("Estación Central", "Zona Franca", "Aeropuerto")));

    /** Primera hora de operación (05:00). Los pesos cubren de 05:00 a 22:00. */
    private static final int HORA_INICIAL = 5;
    /** Peso de demanda por hora: picos en la mañana (06-07) y en la tarde (17-18). */
    private static final List<Integer> PESO_HORA =
            List.of(3, 9, 12, 8, 4, 3, 3, 5, 5, 3, 3, 5, 11, 12, 7, 4, 2, 1);
    /** Probabilidad (%) de que un usuario haga 1, 2, 3 o 4 viajes en el día. */
    private static final List<Integer> PESO_VIAJES = List.of(15, 55, 20, 10);

    private static final List<Integer> ACUM_HORA = acumulado(PESO_HORA);
    private static final List<Integer> ACUM_VIAJES = acumulado(PESO_VIAJES);
    private static final List<Integer> ACUM_RUTA =
            acumulado(RUTAS.stream().map(Ruta::peso).toList());

    /**
     * Genera los registros de un día para la cantidad de usuarios indicada.
     * La lista resultante es no modificable y está ordenada por fecha y hora,
     * como llegarían los eventos desde los validadores.
     */
    public static List<Registro> generar(int usuarios, long semilla, LocalDate fecha) {
        return IntStream.range(0, usuarios)
                .parallel()
                .boxed()
                .flatMap(u -> registrosDeUsuario(u, semilla, fecha))
                .sorted(Comparator.comparing(Registro::timestamp)
                        .thenComparing(Registro::idUsuario))
                .toList();
    }

    /** Todos los registros (entrada y salida de cada viaje) de un usuario en el día. */
    static Stream<Registro> registrosDeUsuario(int u, long semilla, LocalDate fecha) {
        String id = String.format("U%07d", u + 1);
        int viajes = 1 + indicePonderado(ACUM_VIAJES, hash(semilla, u, 0));
        // Horas distintas para cada viaje (evaluación perezosa: se generan solo las necesarias).
        List<Integer> horas = IntStream.range(0, 64)
                .map(i -> HORA_INICIAL + indicePonderado(ACUM_HORA, hash(semilla, u, 1, i)))
                .distinct()
                .limit(viajes)
                .sorted()
                .boxed()
                .toList();
        return IntStream.range(0, horas.size())
                .boxed()
                .flatMap(k -> viaje(id, u, k, horas.get(k), semilla, fecha));
    }

    /** Un viaje genera dos registros: ENTRADA en el origen y SALIDA en el destino. */
    static Stream<Registro> viaje(String id, int u, int k, int hora, long semilla,
                                  LocalDate fecha) {
        Trayecto t = trayecto(u, k, semilla);
        LocalDateTime entrada = fecha.atTime(hora,
                entero(hash(semilla, u, 5, k), 26),            // minuto 0 a 25
                entero(hash(semilla, u, 6, k), 60));           // segundo 0 a 59
        long segundosPorEstacion = 150 + entero(hash(semilla, u, 7, k), 90);  // 2,5 a 4 min
        long espera = 60 + entero(hash(semilla, u, 8, k), 120);               // 1 a 3 min
        long duracion = Math.round((t.saltos() * segundosPorEstacion + espera)
                * factorCongestion(hora));
        List<String> estaciones = t.ruta().estaciones();
        String ruta = t.ruta().codigo();
        return Stream.of(
                new Registro(id, ruta, estaciones.get(t.origen()), Accion.ENTRADA, entrada),
                new Registro(id, ruta, estaciones.get(t.destino()), Accion.SALIDA,
                        entrada.plusSeconds(duracion)));
    }

    /** El segundo viaje del día es el regreso del primero (patrón casa-trabajo-casa). */
    static Trayecto trayecto(int u, int k, long semilla) {
        if (k == 1) {
            return trayecto(u, 0, semilla).invertido();
        }
        Ruta ruta = RUTAS.get(indicePonderado(ACUM_RUTA, hash(semilla, u, 2, k)));
        int n = ruta.estaciones().size();
        int origen = entero(hash(semilla, u, 3, k), n);
        int destino = (origen + 1 + entero(hash(semilla, u, 4, k), n - 1)) % n;
        return new Trayecto(ruta, origen, destino);
    }

    /** En horas pico los viajes tardan un 20 % más. */
    static double factorCongestion(int hora) {
        return (hora == 6 || hora == 7 || hora == 17 || hora == 18) ? 1.2 : 1.0;
    }

    // ------------------------------------------------------------------------------------
    // Funciones puras de apoyo
    // ------------------------------------------------------------------------------------

    /** Sumas acumuladas de una lista de pesos: [3, 9, 12] -> [3, 12, 24]. */
    static List<Integer> acumulado(List<Integer> pesos) {
        return IntStream.rangeClosed(1, pesos.size())
                .map(i -> pesos.subList(0, i).stream().mapToInt(Integer::intValue).sum())
                .boxed()
                .toList();
    }

    /** Elige un índice según los pesos acumulados (selección ponderada). */
    static int indicePonderado(List<Integer> acumulado, long hash) {
        int r = entero(hash, acumulado.get(acumulado.size() - 1));
        return IntStream.range(0, acumulado.size())
                .filter(i -> acumulado.get(i) > r)
                .findFirst()
                .orElseThrow();
    }

    /** Entero en el rango [0, limite). */
    static int entero(long hash, int limite) {
        return (int) Math.floorMod(hash, (long) limite);
    }

    /** Combina la semilla con las claves mediante reduce: misma entrada, mismo resultado. */
    static long hash(long semilla, long... claves) {
        return LongStream.of(claves)
                .reduce(mezclar(semilla), (acum, clave) -> mezclar(acum ^ mezclar(clave)));
    }

    /** Función de mezcla SplitMix64 (pura, sin estado). */
    static long mezclar(long x) {
        long z = x + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
