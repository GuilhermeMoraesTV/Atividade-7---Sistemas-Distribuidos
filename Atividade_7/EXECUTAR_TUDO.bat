@echo off
chcp 65001 > nul

echo --- PASSO 1: Compilando e empacotando o projeto... ---
rem O /WAIT garante que este script espera que a janela do compilador feche
START "Compilando o Projeto..." /WAIT COMPILAR.bat

set JAR_FILE=target/controle-colaborativo-a7-1.0-SNAPSHOT-jar-with-dependencies.jar

if not exist "%JAR_FILE%" (
    echo.
    echo ***** FALHA NA COMPILACAO! Impossivel continuar. *****
    pause
    exit /b
)

echo.
echo --- PASSO 2: Iniciando a Simulacao do Sistema numa nova janela... ---
START "Simulação - Controle Colaborativo" EXECUTAR_SISTEMA.bat

echo.
echo --- Todas as janelas foram iniciadas! ---