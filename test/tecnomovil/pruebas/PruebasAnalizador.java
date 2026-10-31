package tecnomovil.pruebas;

import static java.util.Map.entry;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import tecnomovil.analisis.AnalizadorTransporte;
import tecnomovil.analisis.ResultadoAnalisis;
import tecnomovil.datos.GeneradorDatos;
import tecnomovil.datos.LectorCsv;
import tecnomovil.modelo.EstadisticaTiempos;
import tecnomovil.modelo.EstadoRuta;
import tecnomovil.modelo.Registro;

/**
 * Pruebas sin frameworks externos. Cada prueba es un nombre y una función (BooleanSupplier)
 * que devuelve true si el resultado coincide con el valor calculado a mano sobre
 * datos/muestra_registros.csv (22 registros, 5 usuarios, 11 viajes).
 */
public final class PruebasAnalizador {

    private record Prueba(String nombre, BooleanSupplier verificacion) {
    }

    private static final Function<String, Integer> CAPACIDAD_4 = ruta -> 4;
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 30);

    public static void main(String[] args) {
        List<Registro> muestra = LectorCsv.leer(Path.of("datos/muestra_registros.csv"));
        List<Registro> copia = new ArrayList<>(muestra);
        ResultadoAnalisis r = AnalizadorTransporte.analizar(muestra, CAPACIDAD_4, 0.90, false);
        EstadisticaTiempos t = r.tiempos();
        List<Registro> generados = GeneradorDatos.generar(20_000, 2026L, FECHA);

        List<Prueba> pruebas = List.of(
                new Prueba("Lectura CSV: 22 registros", () -> muestra.size() == 22),
                new Prueba("CSV ida y vuelta: desdeCsv(aCsv(r)) == r",
                        () -> muestra.stream()
                                .allMatch(x -> Registro.desdeCsv(x.aCsv()).equals(x))),

                new Prueba("a) Afluencia por estación",
                        () -> r.afluenciaPorEstacion().equals(Map.of(
                                "Portal Norte", 3L, "Estación Central", 3L, "Universidad", 1L,
                                "Hospital", 1L, "Portal Oriente", 1L, "Plaza Mayor", 1L,
                                "El Bosque", 1L))),
                new Prueba("a) Usuarios únicos en Portal Norte = 3",
                        () -> r.usuariosUnicosPorEstacion().get("Portal Norte") == 3),

                new Prueba("b) Flujo por hora", () -> r.flujoPorHora().equals(Map.of(
                        6, 10L, 7, 4L, 12, 2L, 17, 5L, 18, 1L))),
                new Prueba("b) Horas pico: 06, 17, 07", () -> r.horasPico().equals(List.of(
                        entry(6, 10L), entry(17, 5L), entry(7, 4L)))),

                new Prueba("c) Rutas más utilizadas", () -> r.rutasMasUtilizadas().equals(List.of(
                        entry("T1", 6L), entry("T2", 2L), entry("A1", 1L), entry("C3", 1L),
                        entry("E1", 1L)))),

                new Prueba("d) Patrón de U0000004 en orden de aparición",
                        () -> r.patronesDeViaje().get("U0000004").equals(List.of(
                                "Portal Norte", "Estación Central", "Estación Central",
                                "Plaza Mayor", "Plaza Mayor", "Portal Oriente"))),
                new Prueba("d) Patrón de U0000002 en orden de aparición",
                        () -> r.patronesDeViaje().get("U0000002").equals(List.of(
                                "Estación Central", "Universidad", "Hospital",
                                "Estación Central"))),

                new Prueba("e) 11 tramos analizados", () -> t.totalTramos() == 11),
                new Prueba("e) Promedio general = 169/11 min",
                        () -> Math.abs(t.promedioMinutos() - 169.0 / 11) < 1e-9),
                new Prueba("e) Portal Norte -> Estación Central = 15 min",
                        () -> t.promedioPorTramo()
                                .get("Portal Norte -> Estación Central") == 15.0),
                new Prueba("e) Trasbordos y salida->entrada no son tramos",
                        () -> !t.promedioPorTramo().containsKey("Universidad -> Hospital")
                                && !t.promedioPorTramo()
                                        .containsKey("Portal Norte -> Portal Norte")),

                new Prueba("f) Solo T1 es crítica (4 ingresos a las 06:00, capacidad 4)",
                        () -> r.sobrecarga().stream()
                                .filter(EstadoRuta::critica)
                                .map(EstadoRuta::ruta)
                                .toList().equals(List.of("T1"))
                                && r.sobrecarga().get(0).horaPico() == 6
                                && r.sobrecarga().get(0).ingresosHoraPico() == 4),

                new Prueba("Inmutabilidad: la lista original no cambia",
                        () -> muestra.equals(copia)),
                new Prueba("Inmutabilidad: la lista original rechaza add()",
                        () -> lanzaUnsupported(() -> muestra.add(muestra.get(0)))),
                new Prueba("Inmutabilidad: los resultados rechazan put() y add()",
                        () -> lanzaUnsupported(() -> r.afluenciaPorEstacion().put("X", 1L))
                                && lanzaUnsupported(
                                        () -> r.patronesDeViaje().get("U0000001").add("X"))),

                new Prueba("Generador determinista: misma semilla, mismos datos",
                        () -> GeneradorDatos.generar(2_000, 7L, FECHA)
                                .equals(GeneradorDatos.generar(2_000, 7L, FECHA))),
                new Prueba("Generador: semilla distinta, datos distintos",
                        () -> !GeneradorDatos.generar(2_000, 7L, FECHA)
                                .equals(GeneradorDatos.generar(2_000, 8L, FECHA))),
                new Prueba("Generador: cada viaje produce un tramo (tramos == entradas)",
                        () -> AnalizadorTransporte.tramos(generados, true).size()
                                == generados.stream().filter(Registro::esEntrada).count()),
                new Prueba("Secuencial == paralelo (20.000 usuarios generados)",
                        () -> AnalizadorTransporte.analizar(generados, ruta -> 1_000, 0.9, false)
                                .equals(AnalizadorTransporte.analizar(
                                        generados, ruta -> 1_000, 0.9, true))));

        long fallidas = pruebas.stream()
                .filter(p -> !ejecutar(p))
                .count();
        System.out.printf("%nPruebas ejecutadas: %d | correctas: %d | fallidas: %d%n",
                pruebas.size(), pruebas.size() - fallidas, fallidas);
        System.exit(fallidas == 0 ? 0 : 1);
    }

    private static boolean ejecutar(Prueba prueba) {
        boolean ok;
        try {
            ok = prueba.verificacion().getAsBoolean();
        } catch (RuntimeException e) {
            ok = false;
        }
        System.out.println((ok ? "  [OK]    " : "  [FALLA] ") + prueba.nombre());
        return ok;
    }

    private static boolean lanzaUnsupported(Runnable accion) {
        try {
            accion.run();
            return false;
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }
}
