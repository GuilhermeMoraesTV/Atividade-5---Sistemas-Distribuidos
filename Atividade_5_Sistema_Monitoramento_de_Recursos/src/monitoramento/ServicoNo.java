package monitoramento;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Define a interface remota para um Nó.
 * Qualquer método aqui declarado pode ser chamado remotamente por outros nós.
 * Estende a interface java.rmi.Remote.
 */
public interface ServicoNo extends Remote {

    /**
     * Obtém o estado atual (recursos) do nó.
     * @param relogioRemetente O valor do relógio de Lamport do nó que está fazendo a chamada.
     * @return Um objeto Recurso com as métricas do nó.
     * @throws RemoteException Se ocorrer um erro de comunicação.
     */
    Recurso getStatus(int relogioRemetente) throws RemoteException;

    // --- Métodos para o Algoritmo de Eleição (Bully) ---

    /**
     * Recebe uma mensagem de eleição de um nó com ID menor.
     * @param idRemetente O ID do nó que iniciou a eleição.
     * @throws RemoteException
     */
    void receberMensagemEleicao(int idRemetente) throws RemoteException;

    /**
     * Recebe uma mensagem "OK" de um nó com ID maior em resposta a uma mensagem de eleição.
     * @param idRemetente O ID do nó que está respondendo.
     * @throws RemoteException
     */
    void receberMensagemOk(int idRemetente) throws RemoteException;

    /**
     * Recebe o anúncio de que um novo coordenador foi eleito.
     * @param novoCoordenadorId O ID do novo nó coordenador.
     * @throws RemoteException
     */
    void receberMensagemCoordenador(int novoCoordenadorId) throws RemoteException;
}