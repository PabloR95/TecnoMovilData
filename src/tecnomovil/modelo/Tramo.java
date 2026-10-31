package tecnomovil.modelo;

import java.time.Duration;
import java.util.Optional;

/**
 * Desplazamiento de un usuario entre dos estaciones consecutivas:
 * una ENTRADA en la estación de origen seguida de una SALIDA en otra estación.
 */
public record Tramo(String origen, String destino, long segundos) {

    public String nombre() {
        return origen + " -> " + destino;
    }

    public double minutos() {
        return segundos / 60.0;
    }

    /**
     * Función pura: dados dos registros consecutivos del mismo usuario, devuelve el tramo
     * solo si forman un viaje (entrada en A y salida en B, con A distinto de B).
     * Un trasbordo (salida y entrada en la misma estación) no es un tramo.
     */
    public static Optional<Tramo> entre(Registro a, Registro b) {
        boolean esViaje = a.esEntrada() && b.esSalida() && !a.estacion().equals(b.estacion());
        return esViaje
                ? Optional.of(new Tramo(a.estacion(), b.estacion(),
                        Duration.between(a.timestamp(), b.timestamp()).toSeconds()))
                : Optional.empty();
    }
}
