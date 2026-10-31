#!/usr/bin/env bash
# Compila y ejecuta TecnoMovil Data (requiere JDK 17 o superior, sin dependencias externas).
# Uso: ./compilar_y_ejecutar.sh [usuarios]      ejemplo: ./compilar_y_ejecutar.sh 450000
set -e
cd "$(dirname "$0")"

rm -rf out
javac --release 17 -encoding UTF-8 -d out $(find src test -name "*.java")

echo "== Pruebas =="
java -Dstdout.encoding=UTF-8 -cp out tecnomovil.pruebas.PruebasAnalizador

echo
echo "== Procesamiento diario =="
java -Dstdout.encoding=UTF-8 -cp out tecnomovil.app.Main "$@"
