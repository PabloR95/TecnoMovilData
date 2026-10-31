package tecnomovil.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro inmutable de un evento del sistema de transporte de TecnoValle.
 *
 * <p>Al ser un {@code record}, todos sus campos son {@code private final}, no tiene setters
 * y Java genera equals, hashCode y toString. Una vez creado, un registro no puede cambiar,
 * por lo que puede compartirse sin riesgo entre hilos (parallelStream).</p>
 */
public record Registro(String idUsuario, String ruta, String estacion, Accion accion,
                       LocalDateTime timestamp) {

    /** Constructor compacto: valida que ningún campo sea nulo. */
    public Registro {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(ruta, "ruta");
        Objects.requireNonNull(estacion, "estacion");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public boolean esEntrada() {
        return accion == Accion.ENTRADA;
    }

    public boolean esSalida() {
        return accion == Accion.SALIDA;
    }

    /** Función pura: convierte una línea CSV (idUsuario,ruta,estacion,accion,timestamp). */
    public static Registro desdeCsv(String linea) {
        String[] c = linea.split(",");
        return new Registro(c[0].trim(), c[1].trim(), c[2].trim(),
                Accion.desdeTexto(c[3]), LocalDateTime.parse(c[4].trim()));
    }

    /** Función pura: representación CSV del registro. */
    public String aCsv() {
        return String.join(",", idUsuario, ruta, estacion, accion.texto(), timestamp.toString());
    }
}
