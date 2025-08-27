@echo off
chcp 65001 > nul

echo --- Iniciando a Simulação Completa do Sistema ---
echo.

rem O nome do .jar é definido no ficheiro pom.xml (<artifactId>-<version>-jar-with-dependencies.jar)
set JAR_FILE=target/controle-colaborativo-a7-1.0-SNAPSHOT-jar-with-dependencies.jar

if not exist "%JAR_FILE%" (
    echo ***** ERRO: O ficheiro %JAR_FILE% nao foi encontrado! *****
    echo Por favor, execute o COMPILAR.bat primeiro.
    pause
    exit
)

rem Executa o .jar
java -jar %JAR_FILE%

pause