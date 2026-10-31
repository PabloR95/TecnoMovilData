@echo off
REM Compila y ejecuta TecnoMovil Data en Windows (requiere JDK 17 o superior).
REM Uso: compilar_y_ejecutar.bat [usuarios]      ejemplo: compilar_y_ejecutar.bat 450000
chcp 65001 >nul
cd /d "%~dp0"

if exist out rmdir /s /q out
if exist fuentes.txt del fuentes.txt

REM Rutas relativas: funciona aunque la carpeta tenga espacios (ej. "Semestre 4").
REM -sourcepath hace que javac compile todas las clases que usan Main y las pruebas.
javac --release 17 -encoding UTF-8 -d out -sourcepath "src;test" src\tecnomovil\app\Main.java test\tecnomovil\pruebas\PruebasAnalizador.java || goto :error

echo == Pruebas ==
java -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -cp out tecnomovil.pruebas.PruebasAnalizador || goto :error

echo.
echo == Procesamiento diario ==
java -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -cp out tecnomovil.app.Main %*
echo.
pause
goto :eof

:error
echo.
echo Ocurrio un error. Verifique que tiene instalado el JDK 17 o superior (javac -version).
pause
exit /b 1
