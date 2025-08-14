package monitoramento;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class EmissorMulticast {
    private static final String ENDERECO_MULTICAST = "239.0.0.1";
    private static final int PORTA_MULTICAST = 12345;

    public void enviar(int idLider, List<Recurso> snapshot) {
        try (MulticastSocket socket = new MulticastSocket()) {

            InetAddress localHost = InetAddress.getByName("127.0.0.1");
            socket.setNetworkInterface(NetworkInterface.getByInetAddress(localHost));

            // --- NOVO FORMATO DE RELATÓRIO ---
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("========== RELATÓRIO DE ESTADO DA REDE (Líder: P%d) ==========%n", idLider));
            sb.append(String.format("| %-4s | %-8s | %-12s | %-11s | %-15s | %-15s%n", "NÓ", "CPU", "MEMÓRIA", "CARGA (1m)", "PROCESSADORES", " "));
            sb.append(String.format("--------------------------------------------------------------------------------%n"));

            int nosAtivos = 0;
            for (Recurso r : snapshot) {
                if (r != null) {
                    sb.append(r.paraLinhaRelatorio()).append("\n");
                    nosAtivos++;
                }
            }

            sb.append(String.format("--------------------------------------------------------------------------------%n"));
            sb.append(String.format("Relatório gerado em: %s | Nós ativos: %d%n", dtf.format(now), nosAtivos));
            sb.append(String.format("================================================================================%n%n"));

            byte[] dados = sb.toString().getBytes();
            InetAddress grupo = InetAddress.getByName(ENDERECO_MULTICAST);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, grupo, PORTA_MULTICAST);

            socket.send(pacote);

        } catch (Exception e) {
            System.err.println("[LÍDER] Erro ao enviar snapshot via multicast: " + e.getMessage());
        }
    }
}