@echo off
rem Define a página de código para UTF-8 para suportar acentuação e caracteres especiais.
chcp 65001 > nul

echo --- Limpando compilacoes antigas...
if exist bin rmdir /s /q bin
mkdir bin
echo.
echo --- Compilando todo o projeto...

rem Cria uma lista de todos os ficheiros .java e passa-a para o compilador.
dir /s /b src\*.java > sources.txt
javac -d bin -encoding UTF-8 @sources.txt 2>&1
del sources.txt

echo.
rem Verifica se o ficheiro principal da simulação foi compilado com sucesso.
if exist "bin\monitoramento\Simulador.class" (
    echo Compilacao finalizada com SUCESSO!
    echo Esta janela fechara em 3 segundos...
    rem O comando 'timeout' cria a pausa de 3 segundos.
    timeout /t 3 > nul
) else (
    echo ***** FALHA NA COMPILACAO! *****
    echo Verifique as mensagens de erro acima.
    rem Se a compilação falhar, o script pausa para que você possa ler o erro.
    pause
)

rem O comando 'exit' fecha a janela do Prompt de Comando.
exit