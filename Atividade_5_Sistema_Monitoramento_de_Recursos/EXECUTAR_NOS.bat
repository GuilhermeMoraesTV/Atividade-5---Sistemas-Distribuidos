@echo off
echo "== Iniciando Simulação dos Nós do Sistema de Monitoramento =="
set CLASSPATH=./bin

java -cp "%CLASSPATH%" monitoramento.Simulador

pause