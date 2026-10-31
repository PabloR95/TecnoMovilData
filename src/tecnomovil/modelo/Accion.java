package tecnomovil.modelo;

import java.util.Arrays;

/**
 * Tipo de validación registrada por los validadores y sensores del sistema.
 * ENTRADA: el usuario ingresa a la estación. SALIDA: valida para salir o cambia de ruta.
 */
public enum Accion {
    ENTRADA("entrada"),
    SALIDA("salida");

    private final String texto;

    Accion(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }

    /** Función pura: traduce el texto del registro ("entrada" / "salida") al enum. */
    public static Accion desdeTexto(String valor) {
        return Arrays.stream(values())
                .filter(a -> a.texto.equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Acción desconocida: " + valor));
    }
}
