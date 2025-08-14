package monitoramento;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Servidor que roda apenas no nó líder para autenticar clientes.
 */
public class ServidorAutenticacao implements Runnable {
    private final No noPai;
    private static final int PORTA_AUTENTICACAO = 9090;
    // Callback para devolver a instância do ServerSocket.
    private final Consumer<ServerSocket> socketCallback;

    /**
     * Construtor
     */
    public ServidorAutenticacao(No noPai, Consumer<ServerSocket> socketCallback) {
        this.noPai = noPai;
        this.socketCallback = socketCallback;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(PORTA_AUTENTICACAO)) {
            // Devolve a referência do socket para o Nó.
            socketCallback.accept(serverSocket);

            System.out.printf("[AUTH] Lider P%d: Servidor de Autenticacao iniciado na porta %d.%n", noPai.getId(), PORTA_AUTENTICACAO);

            while (noPai.isAtivo() && noPai.getId() == noPai.getCoordenadorId()) {
                try (Socket clientSocket = serverSocket.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                    String credenciais = in.readLine();
                    if ("admin;admin".equals(credenciais)) {
                        String token = UUID.randomUUID().toString();
                        out.println(token);
                        System.out.printf("[AUTH] Lider P%d: Token gerado para cliente autenticado: %s%n", noPai.getId(), token);
                        noPai.registrarClienteAutenticado();
                    } else {
                        out.println("ERRO: Credenciais invalidas");
                    }
                } catch (Exception e) {
                    // Ignora erros de socket fechado, que são esperados quando o nó é desativado.
                }
            }
        } catch (Exception e) {
            // Ignora erro de "Address already in use" que pode acontecer durante uma eleição rápida.
        }
        System.out.printf("[AUTH] Lider P%d: Servidor de Autenticacao encerrado.%n", noPai.getId());
    }
}