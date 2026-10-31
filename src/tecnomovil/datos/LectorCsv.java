package tecnomovil.datos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import tecnomovil.modelo.Registro;

/**
 * Lectura declarativa de registros desde un archivo CSV con encabezado:
 * idUsuario,ruta,estacion,accion,timestamp
 *
 * <p>La lectura del archivo es el único efecto (entrada/salida) y queda aislada aquí;
 * la conversión de cada línea es la función pura {@link Registro#desdeCsv(String)}.</p>
 */
public final class LectorCsv {

    private LectorCsv() {
    }

    public static List<Registro> leer(Path archivo) {
        try (Stream<String> lineas = Files.lines(archivo, StandardCharsets.UTF_8)) {
            return lineas.skip(1)                    // encabezado
                    .filter(linea -> !linea.isBlank())
                    .map(Registro::desdeCsv)
                    .toList();                       // lista no modificable
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }
}
