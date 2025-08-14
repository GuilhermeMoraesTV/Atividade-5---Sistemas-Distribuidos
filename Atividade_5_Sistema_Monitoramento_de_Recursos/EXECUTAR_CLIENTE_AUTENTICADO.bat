@echo off
echo "== Iniciando Cliente Autenticado =="
set CLASSPATH=./bin

java -cp "%CLASSPATH%" monitoramento.ClienteAutenticado

pause