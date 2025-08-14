package monitoramento;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Componente passivo do mecanismo de Heartbeat.
 */
public class HeartbeatServidor implements Runnable {
    private final int porta;
    private final No noPai;
    // Callback para devolver a instância do ServerSocket para a classe No.
    private final Consumer<ServerSocket> socketCallback;

    /**
     * Construtor
     */
    public HeartbeatServidor(No noPai, int porta, Consumer<ServerSocket> socketCallback) {
        this.noPai = noPai;
        this.porta = porta;
        this.socketCallback = socketCallback;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            // Devolve a referência do socket para o Nó, para que ele possa ser fechado externamente.
            socketCallback.accept(serverSocket);

            System.out.printf("[INFO] No %d: Servidor de Heartbeat iniciado na porta %d, aguardando pings.%n", noPai.getId(), porta);

            while (noPai.isAtivo()) {
                try (Socket clientSocket = serverSocket.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String msg = in.readLine();

                    if ("PING".equals(msg)) {
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        out.println("PONG");
                    }
                } catch (Exception e) {
                    // Ignora erros de socket fechado, que são esperados quando o nó é desativado.
                    if (noPai.isAtivo()) {
                        System.err.printf("[ERRO] No %d: Erro no servidor de Heartbeat: %s%n", noPai.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // Só loga o erro se o nó deveria estar ativo.
            if (noPai.isAtivo()) {
                System.err.printf("[ERRO] No %d: Nao foi possivel iniciar o servidor de Heartbeat na porta %d.%n", noPai.getId(), porta);
            }
        }
        System.out.printf("[INFO] No %d: Servidor de Heartbeat encerrado.%n", noPai.getId());
    }
}