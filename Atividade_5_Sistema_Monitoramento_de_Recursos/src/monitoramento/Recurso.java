package monitoramento;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.time.Instant;

/**
 * Classe que representa os recursos monitorados de um nó em um determinado momento.
 * É serializável para poder ser transferida pela rede (via RMI).
 * Coleta dados como uso de CPU, memória, tempo de atividade, etc.
 */
public class Recurso implements Serializable {
    private final int noId;
    private final double usoCpu;
    private final double usoMemoria;
    private final long memoriaTotalGB;
    private final long tempoAtividade;
    private final int processadores;
    private final double cargaSistema;
    private final long timestampColeta;
    private final int relogioLamport;

    /**
     * Construtor que coleta as métricas do sistema operacional no momento da sua instanciação.
     * @param noId O ID do nó ao qual este recurso pertence.
     * @param relogioLamport O valor do relógio de Lamport no momento da coleta.
     */
    public Recurso(int noId, int relogioLamport) {
        this.noId = noId;
        this.relogioLamport = relogioLamport;

        // Usa a MBean específica da Sun/Oracle para obter dados detalhados do SO.
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        // Coleta o uso da CPU (multiplica por 100 para ter a porcentagem).
        this.usoCpu = Math.max(0, osBean.getCpuLoad() * 100);

        // Calcula o uso de memória.
        long totalMemoriaBytes = osBean.getTotalMemorySize();
        long memoriaLivreBytes = osBean.getFreeMemorySize();
        this.usoMemoria = (1 - (double) memoriaLivreBytes / totalMemoriaBytes) * 100;
        this.memoriaTotalGB = totalMemoriaBytes / (1024 * 1024 * 1024); // Converte para GB

        // Obtém o tempo de atividade da JVM em segundos.
        this.tempoAtividade = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        this.timestampColeta = Instant.now().getEpochSecond();

        this.processadores = osBean.getAvailableProcessors();
        this.cargaSistema = osBean.getSystemLoadAverage(); // System load average for the last minute.
    }

    public int getNoId() { return noId; }

    /**
     * Formata os dados deste recurso em uma única linha de texto para ser exibida na tabela do relatório.
     * @return Uma string formatada como uma linha de tabela.
     */
    public String paraLinhaRelatorio() {
        // Trata o caso em que a carga do sistema não está disponível (retorna -1).
        String cargaCpuFormatada = (cargaSistema < 0) ? "N/A" : String.format("%.2f", cargaSistema);

        return String.format("| P%-3d | %-12s | %-16s | %-11s | %-15s | %-15s",
                noId,
                String.format("%.2f%%", usoCpu),
                String.format("%.2f%% (~%d GB)", usoMemoria, memoriaTotalGB),
                cargaCpuFormatada,
                String.valueOf(processadores),
                String.format("%ds", tempoAtividade)
        );
    }

    /**
     * Representação textual simplificada do recurso, usada para logs internos do líder.
     * @return Uma string com as informações básicas do nó.
     */
    @Override
    public String toString() {
        return String.format("Nó %d -> [Relógio: %d] CPU: %.2f%% | Memória: %.2f%%",
                noId, relogioLamport, usoCpu, usoMemoria);
    }
}