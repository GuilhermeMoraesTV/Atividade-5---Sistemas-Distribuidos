package monitoramento;

import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Representa um nó no sistema distribuído.
 */
public class No {
    // Atributos principais
    private final int id;
    private final ServicoNo servidorRMI;
    private final AtomicBoolean ativo = new AtomicBoolean(true);
    private final Map<Integer, NoInfo> nosDaRede = new ConcurrentHashMap<>();
    private final int portaHeartbeat;
    private int coordenadorId;
    private final List<Integer> todosPids;

    // Atributos para Eleição
    private AtomicBoolean emEleicao = new AtomicBoolean(false);
    private AtomicBoolean respondeuOk = new AtomicBoolean(false);

    // Outros componentes
    private final AtomicInteger relogioLamport = new AtomicInteger(0);
    private final EmissorMulticast emissor = new EmissorMulticast();
    private final AtomicBoolean clienteAutenticadoPresente = new AtomicBoolean(false);

    private ServerSocket servidorSocketAuth;
    private ServerSocket servidorSocketHeartbeat;

    public No(int id, List<Integer> todosPids, Map<Integer, Integer> portasHeartbeat) throws RemoteException {
        this.id = id;
        this.todosPids = todosPids;
        this.portaHeartbeat = portasHeartbeat.get(id);
        this.coordenadorId = todosPids.stream().max(Integer::compareTo).orElse(this.id);
        for (Map.Entry<Integer, Integer> entry : portasHeartbeat.entrySet()) {
            nosDaRede.put(entry.getKey(), new NoInfo(entry.getKey(), entry.getValue()));
        }
        this.servidorRMI = new NoServidor(this);
        System.out.printf("[INFO] No %d iniciado. Coordenador inicial: P%d.%n", id, this.coordenadorId);
        iniciarServicosHeartbeat();
        iniciarTarefaCoordenador();
    }

    /**
     * Metodo Chamado para forçar o encerramento dos sockets dos servidores.
     * Isso interrompe imediatamente as threads que estão bloqueadas em 'accept()'.
     */
    private void pararServicos() {
        try {
            if (servidorSocketHeartbeat != null && !servidorSocketHeartbeat.isClosed()) {
                servidorSocketHeartbeat.close();
            }
            if (servidorSocketAuth != null && !servidorSocketAuth.isClosed()) {
                servidorSocketAuth.close();
            }
        } catch (Exception e) {
            // Este erro é menor, apenas logamos para depuração.
            System.err.printf("[ERRO] No %d: Erro ao fechar sockets do servidor: %s%n", id, e.getMessage());
        }
    }

    /**
     * SetAtivo agora também chama pararServicos para um encerramento limpo.
     */
    public void setAtivo(boolean status) {
        this.ativo.set(status);
        if (!status) {
            pararServicos(); // Força o encerramento imediato dos servidores!
        }
    }

    /**
     * Passa um callback para que o nó possa obter a referência do ServerSocket.
     */
    private void iniciarServicosHeartbeat() {
        new Thread(new HeartbeatServidor(this, this.portaHeartbeat, (socket) -> this.servidorSocketHeartbeat = socket)).start();
        new Thread(new HeartbeatGestor(this)).start();
    }

    /**
     * Passa um callback para o ServidorAutenticacao.
     */
    private void iniciarTarefaCoordenador() {
        new Thread(() -> {
            Thread servidorAuthThread = null;
            while (ativo.get()) {
                try {
                    Thread.sleep(10000);
                    if (id == coordenadorId && ativo.get()) {
                        if (servidorAuthThread == null || !servidorAuthThread.isAlive()) {
                            servidorAuthThread = new Thread(new ServidorAutenticacao(this, (socket) -> this.servidorSocketAuth = socket));
                            servidorAuthThread.start();
                        }
                        System.out.printf("%n================ [LIDER P%d] INICIANDO COLETA DE ESTADO GLOBAL ===============%n", id);
                        coletarEstadoGlobal();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    private void coletarEstadoGlobal() {
        relogioLamport.incrementAndGet();
        System.out.printf("[LIDER P%d] Relogio Logico antes da coleta: %d%n", id, relogioLamport.get());

        List<Recurso> snapshot = new ArrayList<>();
        snapshot.add(this.getStatusLocal());

        for (int pid : todosPids) {
            if (pid != this.id && nosDaRede.get(pid).isAtivo()) {
                try {
                    Registry registry = LocateRegistry.getRegistry("127.0.0.1", 1099);
                    ServicoNo stub = (ServicoNo) registry.lookup("No" + pid);
                    Recurso recursoRemoto = stub.getStatus(this.relogioLamport.get());
                    snapshot.add(recursoRemoto);
                } catch (Exception e) {
                    System.err.printf("[FALHA] Lider P%d nao conseguiu coletar estado do No %d.%n", id, pid);
                }
            }
        }

        System.out.println("\n--- [LIDER P" + id + "] SNAPSHOT GLOBAL COLETADO ---");
        snapshot.forEach(r -> { if (r != null) System.out.println("  -> " + r); });
        System.out.println("----------------------------------------\n");

        if (clienteAutenticadoPresente.get()) {
            emissor.enviar(this.id, snapshot);
            System.out.printf("[LIDER P%d] Snapshot enviado via multicast para clientes autenticados.%n", id);
        } else {
            System.out.printf("[LIDER P%d] Nenhum cliente autenticado. Snapshot nao sera enviado via multicast.%n", id);
        }
        System.out.printf("======================= [LIDER P%d] FIM DA COLETA ======================%n%n", id);
    }

    public void registrarClienteAutenticado() {
        this.clienteAutenticadoPresente.set(true);
    }

    // --- Getters e Lógica de Eleição ---
    public int getId() { return id; }
    public boolean isAtivo() { return ativo.get(); }
    public Map<Integer, NoInfo> getNosDaRede() { return nosDaRede; }
    public ServicoNo getServidorRMI() { return servidorRMI; }
    public int getCoordenadorId() { return coordenadorId; }

    public Recurso getStatusLocal() {
        if (!ativo.get()) return null;
        int timestampAtual = relogioLamport.incrementAndGet();
        return new Recurso(this.id, timestampAtual);
    }

    public void iniciarEleicao() {
        if (!emEleicao.compareAndSet(false, true)) return;
        System.out.printf("%n*************************************************%n");
        System.out.printf("[ELEICAO] No %d iniciou uma ELEICAO (Bully).%n", id);
        System.out.printf("*************************************************%n");
        this.respondeuOk.set(false);
        List<Integer> pidsMaiores = todosPids.stream().filter(p -> p > this.id).collect(Collectors.toList());
        boolean algumMaiorContactado = false;
        for (int pidMaior : pidsMaiores) {
            if (nosDaRede.get(pidMaior).isAtivo()) {
                System.out.printf("[ELEICAO] No %d enviando mensagem de eleicao para P%d.%n", id, pidMaior);
                enviarMensagemEleicao(pidMaior);
                algumMaiorContactado = true;
            }
        }
        if (!algumMaiorContactado) {
            anunciarCoordenador();
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (!this.respondeuOk.get()) {
                    anunciarCoordenador();
                } else {
                    System.out.printf("[ELEICAO] No %d encerrando eleicao. Um no maior assumira.%n", id);
                    emEleicao.set(false);
                }
            } catch (InterruptedException e) {}
        }).start();
    }

    private void anunciarCoordenador() {
        System.out.printf("%n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!%n");
        System.out.printf("[ELEICAO] No %d: *** EU SOU O NOVO COORDENADOR! ***%n", id);
        System.out.printf("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!%n%n");
        this.coordenadorId = this.id;
        this.emEleicao.set(false);
        this.respondeuOk.set(false);
        for (int pid : todosPids) {
            if (pid != this.id && nosDaRede.get(pid).isAtivo()) {
                enviarMensagemCoordenador(pid);
            }
        }
    }

    private void enviarMensagemRMI(int idDestino, RmiAction action) {
        if (!ativo.get() || idDestino == this.id) return;
        try {
            Registry registry = LocateRegistry.getRegistry("127.0.0.1", 1099);
            ServicoNo stub = (ServicoNo) registry.lookup("No" + idDestino);
            action.execute(stub);
        } catch (Exception e) {
            if (nosDaRede.get(idDestino).isAtivo()) {
                System.err.printf("[FALHA] No %d nao conseguiu contatar No %d via RMI. Marcando como inativo.%n", id, idDestino);
                nosDaRede.get(idDestino).setAtivo(false);
            }
        }
    }

    private void enviarMensagemEleicao(int idDestino) { enviarMensagemRMI(idDestino, (stub) -> stub.receberMensagemEleicao(this.id)); }
    private void enviarMensagemOk(int idDestino) { enviarMensagemRMI(idDestino, (stub) -> stub.receberMensagemOk(this.id)); }
    private void enviarMensagemCoordenador(int idDestino) { enviarMensagemRMI(idDestino, (stub) -> stub.receberMensagemCoordenador(this.id)); }

    @FunctionalInterface
    interface RmiAction { void execute(ServicoNo stub) throws RemoteException; }

    private class NoServidor extends UnicastRemoteObject implements ServicoNo {
        private final No noPai;
        public NoServidor(No noPai) throws RemoteException { super(); this.noPai = noPai; }
        @Override
        public Recurso getStatus(int relogioRemetente) throws RemoteException {
            int novoRelogio = Math.max(noPai.relogioLamport.get(), relogioRemetente) + 1;
            noPai.relogioLamport.set(novoRelogio);
            System.out.printf("[INFO] No %d recebeu solicitacao de status. Relogio Logico atualizado para %d.%n", noPai.id, novoRelogio);
            return noPai.getStatusLocal();
        }
        @Override
        public void receberMensagemEleicao(int idRemetente) throws RemoteException {
            System.out.printf("[ELEICAO] No %d recebeu mensagem de ELEICAO de P%d.%n", noPai.id, idRemetente);
            if (noPai.id > idRemetente) {
                noPai.enviarMensagemOk(idRemetente);
                noPai.iniciarEleicao();
            }
        }
        @Override
        public void receberMensagemOk(int idRemetente) throws RemoteException {
            System.out.printf("[ELEICAO] No %d recebeu OK de P%d.%n", noPai.id, idRemetente);
            noPai.respondeuOk.set(true);
        }
        @Override
        public void receberMensagemCoordenador(int novoCoordenadorId) throws RemoteException {
            System.out.printf("[INFO] No %d recebeu anuncio: P%d e o novo COORDENADOR.%n", noPai.id, novoCoordenadorId);
            noPai.coordenadorId = novoCoordenadorId;
            noPai.emEleicao.set(false);
        }
    }
}