@echo off
rem Define a página de código para UTF-8 para garantir que os acentos apareçam corretamente.
chcp 65001 > nul

echo --- Limpando e empacotando o projeto com Maven... ---
echo.

rem O comando 'mvn clean package' irá:
rem 1. Limpar compilações antigas (clean).
rem 2. Compilar todo o seu código (compile).
rem 3. Empacotar tudo num .jar executável (package).
mvn clean package

rem Verifica se o comando anterior deu erro.
if %errorlevel% neq 0 (
    echo.
    echo ----------------------------------------------------
    echo ***** FALHA NA COMPILACAO! *****
    echo ----------------------------------------------------
    echo Verifique as mensagens de erro detalhadas acima.
    echo.
    pause
) else (
    echo.
    echo ------------------------------------------------------
    echo Compilacao e empacotamento finalizados com SUCESSO!
    echo ------------------------------------------------------
    echo Esta janela fechara em 3 segundos...
    timeout /t 3 > nul
)

exit