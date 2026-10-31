package tecnomovil.modelo;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Resultado inmutable del cálculo de tiempos entre estaciones consecutivas.
 *
 * @param totalTramos       número de tramos analizados
 * @param promedioMinutos   tiempo promedio general en minutos
 * @param promedioPorTramo  tiempo promedio (minutos) de cada tramo "Origen -> Destino"
 */
public record EstadisticaTiempos(long totalTramos, double promedioMinutos,
                                 Map<String, Double> promedioPorTramo) {

    /** Copia defensiva: el mapa recibido se copia y se expone como no modificable. */
    public EstadisticaTiempos {
        promedioPorTramo = Collections.unmodifiableSortedMap(new TreeMap<>(promedioPorTramo));
    }
}
