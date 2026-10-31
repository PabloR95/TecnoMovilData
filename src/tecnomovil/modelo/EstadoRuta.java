package tecnomovil.modelo;

/**
 * Estado de ocupación de una ruta durante el día.
 *
 * @param ruta              código de la ruta
 * @param ingresosDia       total de ingresos (entradas) de la ruta en el día
 * @param horaPico          hora con más ingresos en la ruta
 * @param ingresosHoraPico  ingresos registrados en esa hora pico
 * @param capacidadHoraria  capacidad simulada de pasajeros por hora de la ruta
 * @param ocupacion         ingresosHoraPico / capacidadHoraria (1.0 = 100 %)
 * @param critica           true si la ocupación supera el umbral definido
 */
public record EstadoRuta(String ruta, long ingresosDia, int horaPico, long ingresosHoraPico,
                         int capacidadHoraria, double ocupacion, boolean critica) {

    public String estado() {
        return critica ? "CRÍTICA" : "NORMAL";
    }
}
