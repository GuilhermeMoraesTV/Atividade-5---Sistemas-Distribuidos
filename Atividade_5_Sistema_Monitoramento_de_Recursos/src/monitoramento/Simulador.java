package monitoramento;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe principal que simula o ambiente distribuído.
 */
public class Simulador {
    public static void main(String[] args) {
        final int NUM_NOS = 5;
        List<No> nos = new ArrayList<>();
        Map<Integer, Integer> portasHeartbeat = new HashMap<>();
        List<Integer> todosPids = new ArrayList<>();

        for (int i = 1; i <= NUM_NOS; i++) {
            todosPids.add(i);
            portasHeartbeat.put(i, 1100 + i - 1);
        }

        try {
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");
            Registry registry = LocateRegistry.createRegistry(1099);
            System.out.println("======================================================");
            System.out.println("[INFO] Servico de Registro RMI iniciado na porta 1099.");
            System.out.println("======================================================");

            for (int i = 1; i <= NUM_NOS; i++) {
                No no = new No(i, todosPids, portasHeartbeat);
                registry.bind("No" + i, no.getServidorRMI());
                nos.add(no);
            }

            System.out.println("\n--- [INFO] Simulacao Iniciada: Todos os nos estao ativos ---");
            System.out.println("--- [INFO] O lider inicial (P5) comecara a coletar dados em breve ---\n");


            // 1. O cliente iniciar e autenticar-se.
            // 2. O líder P5 fazer pelo menos um ciclo de coleta e enviar um relatório.
            System.out.println("[INFO] Aguardando 25 segundos para o sistema estabilizar e o cliente se conectar...");
            Thread.sleep(25000);

            No noParaFalhar = nos.get(4); // Pega o Nó 5 (índice 4)
            System.out.printf("%n#############################################################%n");
            System.out.printf(">>> [ACAO] SIMULANDO A FALHA DO COORDENADOR (No %d) <<< %n", noParaFalhar.getId());
            System.out.printf("#############################################################%n%n");

            noParaFalhar.setAtivo(false); // Isto agora também encerra os sockets do nó
            registry.unbind("No" + noParaFalhar.getId());

            System.out.println("[INFO] >>> Aguardando detecao de falha e nova eleicao... <<<");
            System.out.println("[INFO] >>> Apos a eleicao, o novo lider (P4) assumira a coleta de estado global. <<<\n");

            // Mantém a simulação a correr por mais 30 segundos para o novo líder trabalhar.
            Thread.sleep(30000);

            System.out.println("\n--- [INFO] Simulacao Finalizada ---");
            System.exit(0);

        } catch (Exception e) {
            System.err.println("[ERRO] Erro fatal no Simulador: " + e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }
}