package monitoramento;

/**
 * Uma classe simples para armazenar informações sobre outros nós na rede.
 * Mantém o estado de atividade e o contador de falhas de heartbeat.
 */
public class NoInfo {
    private final int id;
    private final int portaHeartbeat;
    private boolean ativo = true;
    private int contadorFalhas = 0;

    public NoInfo(int id, int portaHeartbeat) {
        this.id = id;
        this.portaHeartbeat = portaHeartbeat;
    }

    // Getters e Setters
    public int getPortaHeartbeat() { return portaHeartbeat; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public int getContadorFalhas() { return contadorFalhas; }

    // Métodos para gerenciar o estado de falha
    public void incrementarContadorFalhas() { this.contadorFalhas++; }
    public void resetarContadorFalhas() { this.contadorFalhas = 0; }
}