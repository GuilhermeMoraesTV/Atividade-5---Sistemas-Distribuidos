@echo off
rem Define a página de código para UTF-8 para suportar acentuação.
chcp 65001 > nul

echo --- PASSO 1: A garantir que o projeto esta compilado ---
rem O comando START /WAIT abre uma nova janela, executa o comando e espera que a janela seja fechada.
START "Compilando o Projeto..." /WAIT COMPILAR.bat

rem Verifica se a compilação foi bem-sucedida antes de continuar.
if not exist "bin\monitoramento\Simulador.class" (
    echo.
    echo ***** FALHA NA COMPILACAO! Impossivel continuar. *****
    echo Verifique as mensagens de erro na janela de compilacao.
    pause
    exit /b
)

echo.
echo --- PASSO 2: A iniciar os Nos do Sistema (Simulador) numa nova janela ---
START "Sistema de Monitoramento (Nos)" EXECUTAR_NOS.bat

echo.
echo --- A aguardar 5 segundos para o cliente iniciar... ---
rem Este atraso é importante para dar tempo ao líder de iniciar seu servidor de autenticação.
timeout /t 5 /nobreak > nul

echo.
echo --- PASSO 3: A iniciar o Cliente de Monitorizacao numa nova janela ---
START "Cliente de Monitorizacao Resiliente" EXECUTAR_CLIENTE_AUTENTICADO.bat

echo.
echo --- Todas as janelas foram iniciadas! ---
pause