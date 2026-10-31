package tecnomovil.analisis;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import tecnomovil.modelo.EstadisticaTiempos;
import tecnomovil.modelo.EstadoRuta;

/**
 * Resultado inmutable del procesamiento diario (tareas a-f).
 * Al ser un record, dos resultados son iguales (equals) si todos sus componentes lo son;
 * esto permite comprobar que la ejecución secuencial y la paralela coinciden.
 */
public record ResultadoAnalisis(
        Map<String, Long> afluenciaPorEstacion,
        Map<String, Integer> usuariosUnicosPorEstacion,
        SortedMap<Integer, Long> flujoPorHora,
        List<Map.Entry<Integer, Long>> horasPico,
        List<Map.Entry<String, Long>> rutasMasUtilizadas,
        Map<String, List<String>> patronesDeViaje,
        EstadisticaTiempos tiempos,
        List<EstadoRuta> sobrecarga) {
}
