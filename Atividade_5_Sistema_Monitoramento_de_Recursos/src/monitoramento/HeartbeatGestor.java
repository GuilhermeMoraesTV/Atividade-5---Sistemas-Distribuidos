package monitoramento;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Componente ativo do mecanismo de Heartbeat.
 * Esta classe é executada em uma thread e periodicamente envia mensagens "PING"
 * para todos os outros nós da rede para verificar se estão ativos.
 */
public class HeartbeatGestor implements Runnable {
    private final No noPai;
    // Timeout para estabelecer a conexão e para ler a resposta.
    private static final int TIMEOUT_MS = 2000;

    public HeartbeatGestor(No noPai) {
        this.noPai = noPai;
    }

    @Override
    public void run() {
        // O loop continua enquanto o nó pai estiver ativo.
        while (noPai.isAtivo()) {
            try {
                // Intervalo entre cada ciclo de verificação.
                Thread.sleep(5000);

                // Itera sobre todos os nós conhecidos na rede.
                for (Map.Entry<Integer, NoInfo> entry : noPai.getNosDaRede().entrySet()) {
                    int idAlvo = entry.getKey();
                    NoInfo noAlvo = entry.getValue();

                    // Não envia ping para si mesmo.
                    if (idAlvo == noPai.getId()) continue;

                    boolean isAlvoAtivo = false;
                    try (Socket socket = new Socket()) {
                        // Tenta se conectar ao servidor de heartbeat do nó alvo com um timeout.
                        socket.connect(new InetSocketAddress("127.0.0.1", noAlvo.getPortaHeartbeat()), TIMEOUT_MS);
                        socket.setSoTimeout(TIMEOUT_MS); // Timeout para a leitura da resposta.

                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        out.println("PING");
                        // Se receber "PONG" de volta, o nó está ativo.
                        if ("PONG".equals(in.readLine())) {
                            isAlvoAtivo = true;
                        }
                    } catch (Exception e) {
                        // Qualquer exceção (e.g., Connection Refused, Socket Timeout) significa falha na comunicação.
                    }

                    if (isAlvoAtivo) {
                        // Se o nó estava marcado como inativo e voltou, loga a reconexão.
                        if (!noAlvo.isAtivo()) {
                            System.out.printf("[INFO] Nó %d detectou: NÓ %d RECONECTADO!%n", noPai.getId(), idAlvo);
                        }
                        // Zera o contador de falhas e marca o nó como ativo.
                        noAlvo.resetarContadorFalhas();
                        noAlvo.setAtivo(true);
                    } else {
                        // Se a comunicação falhou, incrementa o contador de falhas.
                        noAlvo.incrementarContadorFalhas();
                        if (noAlvo.getContadorFalhas() == 1) {
                            System.out.printf("[AVISO] Nó %d: Primeira falha ao pingar Nó %d. Monitorando...%n", noPai.getId(), idAlvo);
                        }

                        // Se o número de falhas consecutivas atingir o limite (3), o nó é considerado falho.
                        if (noAlvo.getContadorFalhas() >= 3 && noAlvo.isAtivo()) {
                            System.err.printf("[FALHA] Nó %d detectou: NÓ %d CONSIDERADO FALHO!%n", noPai.getId(), idAlvo);
                            noAlvo.setAtivo(false);

                            // Se o nó que falhou era o coordenador, inicia uma nova eleição.
                            if (idAlvo == noPai.getCoordenadorId()) {
                                noPai.iniciarEleicao();
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}



