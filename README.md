# TecnoMovil Data — Procesamiento funcional de datos de transporte urbano

Módulo en **Java puro (JDK 17+, sin frameworks externos)** que procesa los registros diarios del sistema de
transporte de la ciudad de TecnoValle usando programación funcional: **funciones puras, inmutabilidad,
funciones de orden superior, lambdas y Streams**, con soporte de `parallelStream()`.

> Evidencia de Aprendizaje 2 (EA2) — Creación de repositorio · Institución Universitaria Digital de Antioquia
> Autor: Pablo José Ramírez Acevedo ([@PabloR95](https://github.com/PabloR95)) · Docente: Leonardo Montes Gutierrez · 2026

## Tareas implementadas

| Requerimiento | Método en `AnalizadorTransporte` |
|---|---|
| a) Afluencia por estación | `afluenciaPorEstacion`, `usuariosUnicosPorEstacion` |
| b) Horas pico | `flujoPorHora`, `horasPico` |
| c) Rutas más utilizadas | `usoPorRuta`, `rutasMasUtilizadas` |
| d) Patrones de viaje por usuario | `patronesDeViaje` |
| e) Tiempo promedio entre estaciones | `tramos`, `tiempoEntreEstaciones` |
| f) Detección de sobrecarga | `detectarSobrecarga` (marca la ruta como `CRÍTICA`) |

Cada registro tiene `idUsuario`, `ruta`, `estacion`, `accion` (`entrada`/`salida`) y `timestamp`.

## Estructura

```
TecnoMovilData/
├── src/tecnomovil/
│   ├── modelo/     Registro, Accion, Tramo, EstadisticaTiempos, EstadoRuta (records inmutables)
│   ├── datos/      GeneradorDatos (generador determinista y paralelo), LectorCsv
│   ├── analisis/   AnalizadorTransporte (funciones puras a–f), ResultadoAnalisis
│   └── app/        Main (capa imperativa: carga, mide e imprime)
├── test/tecnomovil/pruebas/PruebasAnalizador.java   21 pruebas sin JUnit
├── datos/muestra_registros.csv                      22 registros verificables a mano
├── docs/                                            informe PDF (APA 7)
├── compilar_y_ejecutar.bat                          Windows
└── compilar_y_ejecutar.sh                           Linux / macOS
```

## Cómo ejecutar

Requiere el **JDK 17 o superior** (`javac -version`).

**Windows:** doble clic en `compilar_y_ejecutar.bat`, o desde la consola:

```bat
compilar_y_ejecutar.bat            REM 450.000 usuarios (~2 millones de registros)
compilar_y_ejecutar.bat 1000000    REM escala a ~4,5 millones de registros
```

**Linux / macOS:**

```bash
./compilar_y_ejecutar.sh
```

**Manual (Windows, cmd):**

```bat
javac --release 17 -encoding UTF-8 -d out -sourcepath "src;test" src\tecnomovil\app\Main.java test\tecnomovil\pruebas\PruebasAnalizador.java
java -cp out tecnomovil.app.Main 450000
```

**Manual (Linux / macOS):**

```bash
javac --release 17 -encoding UTF-8 -d out $(find src test -name "*.java")
java -cp out tecnomovil.pruebas.PruebasAnalizador
java -cp out tecnomovil.app.Main 450000
java -cp out tecnomovil.app.Main --csv datos/muestra_registros.csv
```

Para escenarios de 6 a 12 millones de registros, aumente la memoria: `java -Xmx4g -cp out tecnomovil.app.Main 2000000`.

**IntelliJ IDEA / NetBeans / VS Code:** abra la carpeta, marque `src` y `test` como carpetas de código fuente
y ejecute `tecnomovil.app.Main`. Las pruebas leen `datos/muestra_registros.csv`, así que el directorio de trabajo
debe ser la raíz del proyecto.

## Conceptos de programación funcional aplicados

- **Funciones puras:** todo el procesamiento está en `AnalizadorTransporte`; no hay estado compartido, ni E/S, ni
  modificación de parámetros. La E/S quedó aislada en `Main` y `LectorCsv`.
- **Inmutabilidad:** los datos son `record`; las listas se crean con `toList()` y los mapas se envuelven con
  `Collections.unmodifiable…` mediante `collectingAndThen`. El generador no usa `Random` (estado mutable) sino un hash puro.
- **Funciones de orden superior:** `contarPor(registros, Predicate, Function)`, `detectarSobrecarga(…, Function<String,Integer> capacidad, …)`, `medir(Supplier)`.
- **Lambdas y referencias a métodos:** `Registro::estacion`, `e -> e.getValue()`, composición `Function.andThen`.
- **Streams:** `filter`, `map`, `flatMap`, `groupingBy`, `mapping`, `counting`, `averagingLong`, `sorted`, `limit`, `distinct`, `reduce`.
- **Paralelismo:** cada operación recibe `paralelo` y usa `parallelStream()`; el programa verifica que el resultado
  secuencial y el paralelo sean idénticos.
