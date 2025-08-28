package controlecolaborativo.coordenador;

import controlecolaborativo.comum.Documento;
import controlecolaborativo.comum.Logger;
import controlecolaborativo.comum.Mensagem;
import controlecolaborativo.comum.PedidoAcesso;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encapsula toda a lógica do Coordenador como um serviço executável (Runnable).
 * Isso permite que qualquer nó que vença uma eleição possa iniciar este serviço
 * e assumir o papel de coordenador.
 */
public class ServicoCoordenador implements Runnable {

    private static final String ARQUIVO_CHECKPOINT = "checkpoint.dat";
    private final int porta;
    private final int idCoordenador;

    // Estruturas de dados para gerenciar o estado do sistema.
    private Documento documentoMestre;
    private final Queue<PedidoAcesso> filaRequisicoes = new PriorityQueue<>(); // Fila ordenada de pedidos.
    private boolean recursoOcupado = false;
    private int idNoEmSecaoCritica = -1; // Rastreia qual nó está editando.
    private final Map<Integer, ObjectOutputStream> nosConectados = new ConcurrentHashMap<>(); // Mapa thread-safe de nós ativos.
    private final AtomicInteger relogioLamport = new AtomicInteger(0); // Relógio lógico do coordenador.

    // Agendador para tarefas periódicas, como salvar checkpoints.
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private volatile boolean rodando = true; // Flag para controlar o loop principal do servidor.
    private ServerSocket serverSocket;

    public ServicoCoordenador(int idCoordenador, int porta) {
        this.idCoordenador = idCoordenador;
        this.porta = porta;
        carregarCheckpoint(); // Restaura o estado anterior ao iniciar.
    }

    /**
     * Encerra o serviço do coordenador de forma limpa.
     */
    public void parar() {
        this.rodando = false;
        scheduler.shutdownNow(); // Força o encerramento de tarefas agendadas.
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Fecha o socket do servidor para liberar a porta.
            }
        } catch (IOException e) {
            Logger.logCoordenador(idCoordenador, "Erro ao fechar o socket do servidor: " + e.getMessage());
        }
        Logger.logCoordenador(idCoordenador, "Serviço encerrado.");
    }

    /**
     * Método principal do serviço. Inicia o servidor, aguarda conexões de nós
     * e agenda a tarefa de checkpoint.
     */
    @Override
    public void run() {
        Logger.logCoordenador(idCoordenador, "Iniciando o serviço na porta " + porta + "...");
        scheduler.scheduleAtFixedRate(this::salvarCheckpoint, 30, 30, TimeUnit.SECONDS);

        try {
            serverSocket = new ServerSocket(porta);
            while (rodando) {
                Socket socketNo = serverSocket.accept(); // Bloqueia até uma nova conexão ser estabelecida.
                Logger.logCoordenador(idCoordenador, "Nova conexão anônima recebida: " + socketNo.getInetAddress().getHostAddress());
                // Delega o tratamento da conexão a uma nova thread para não bloquear o loop principal.
                TratadorNo tratador = new TratadorNo(socketNo, this, idCoordenador);
                new Thread(tratador).start();
            }
        } catch (IOException e) {
            if (rodando) {
                Logger.logCoordenador(idCoordenador, "ERRO FATAL: " + e.getMessage());
            }
        } finally {
            if (!scheduler.isShutdown()) {
                scheduler.shutdown();
            }
        }
    }

    /**
     * Registra um novo nó no sistema e envia a ele a versão mais recente do documento.
     */
    public synchronized void registrarNo(int idNo, ObjectOutputStream out) {
        nosConectados.put(idNo, out);
        Logger.logCoordenador(idCoordenador, "Nó P" + idNo + " registrado no sistema.");
        enviarAtualizacaoDocumento(idNo);
    }

    /**
     * Remove um nó do sistema, geralmente devido a uma falha de conexão.
     * Também implementa a lógica de rollback.
     */
    public synchronized void removerNo(int idNo) {
        nosConectados.remove(idNo);
        // Remove quaisquer pedidos pendentes deste nó na fila.
        filaRequisicoes.removeIf(pedido -> pedido.getIdNo() == idNo);
        Logger.logCoordenador(idCoordenador, "Nó P" + idNo + " desconectado.");

        // Lógica de Rollback: verifica se o nó que caiu estava na seção crítica.
        if (idNo == idNoEmSecaoCritica) {
            Logger.logCoordenador(idCoordenador, "[ROLLBACK] Nó P" + idNo + " caiu na seção crítica. Alteração descartada.");
            idNoEmSecaoCritica = -1;
            recursoOcupado = false;
            concederProximoAcesso(); // Passa a vez para o próximo da fila.
        }
    }

    /**
     * Processa uma solicitação de acesso de um nó.
     */
    public synchronized void solicitarAcesso(int idNo, int relogioRemetente) {
        relogioLamport.set(Math.max(relogioLamport.get(), relogioRemetente) + 1);
        PedidoAcesso pedido = new PedidoAcesso(idNo, relogioRemetente);
        Logger.logCoordenador(idCoordenador, "Nó " + pedido + " solicitou acesso à seção crítica.");

        if (!recursoOcupado) {
            // Se o recurso estiver livre, concede a permissão imediatamente.
            recursoOcupado = true;
            idNoEmSecaoCritica = idNo;
            enviarPermissao(idNo);
        } else {
            // Caso contrário, adiciona o pedido à fila de prioridade.
            filaRequisicoes.add(pedido);
            Logger.logCoordenador(idCoordenador, "Recurso ocupado. Pedido " + pedido + " adicionado à fila. Fila: " + filaRequisicoes);
        }
    }

    /**
     * Processa a liberação do recurso por um nó.
     */
    public synchronized void liberarRecurso(int idNo, Documento documentoAtualizado) {
        relogioLamport.incrementAndGet();
        Logger.logCoordenador(idCoordenador, "Nó P" + idNo + " liberou a seção crítica.");

        // Atualiza a versão mestre do documento com as alterações recebidas.
        this.documentoMestre = documentoAtualizado;
        // Propaga a nova versão para todas as réplicas.
        nosConectados.keySet().forEach(this::enviarAtualizacaoDocumento);

        idNoEmSecaoCritica = -1;
        recursoOcupado = false;
        // Tenta conceder acesso ao próximo nó da fila.
        concederProximoAcesso();
    }

    /**
     * Verifica a fila e, se não estiver vazia, concede permissão ao próximo pedido.
     */
    private synchronized void concederProximoAcesso() {
        if (!filaRequisicoes.isEmpty()) {
            PedidoAcesso proximoPedido = filaRequisicoes.poll();
            int proximoNo = proximoPedido.getIdNo();
            recursoOcupado = true;
            idNoEmSecaoCritica = proximoNo;
            Logger.logCoordenador(idCoordenador, "Concedendo permissão ao próximo da fila: " + proximoPedido);
            enviarPermissao(proximoNo);
        }
    }

    /**
     * Envia a mensagem de PERMISSAO_SC para um nó específico.
     */
    private void enviarPermissao(int idDestino) {
        try {
            ObjectOutputStream out = nosConectados.get(idDestino);
            if (out != null) {
                relogioLamport.incrementAndGet();
                Mensagem msg = new Mensagem(Mensagem.Tipo.PERMISSAO_SC, 0, relogioLamport.get(), null);
                out.writeObject(msg);
                out.flush();
                Logger.logCoordenador(idCoordenador, "Permissão enviada para P" + idDestino);
            }
        } catch (IOException e) {
            Logger.logCoordenador(idCoordenador, "Falha ao enviar permissão para P" + idDestino + ". Removendo...");
            removerNo(idDestino);
        }
    }

    /**
     * Envia a mensagem de ATUALIZACAO_DOCUMENTO para um nó específico.
     */
    private void enviarAtualizacaoDocumento(int idDestino) {
        try {
            ObjectOutputStream out = nosConectados.get(idDestino);
            if (out != null) {
                relogioLamport.incrementAndGet();
                // Envia um clone do documento para evitar problemas de concorrência.
                Mensagem msg = new Mensagem(Mensagem.Tipo.ATUALIZACAO_DOCUMENTO, 0, relogioLamport.get(), documentoMestre.clonar());
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            Logger.logCoordenador(idCoordenador, "Falha ao enviar atualização para P" + idDestino + ". Removendo...");
            removerNo(idDestino);
        }
    }

    /**
     * Salva o estado atual do documento mestre em um arquivo (checkpoint).
     */
    private synchronized void salvarCheckpoint() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ARQUIVO_CHECKPOINT))) {
            oos.writeObject(documentoMestre);
            Logger.logCoordenador(idCoordenador, "CHECKPOINT salvo com sucesso. (Relógio: " + relogioLamport.get() + ")");
        } catch (IOException e) {
            Logger.logCoordenador(idCoordenador, "ERRO: Falha ao salvar checkpoint: " + e.getMessage());
        }
    }

    /**
     * Carrega o estado do documento mestre a partir de um arquivo de checkpoint, se existir.
     */
    private synchronized void carregarCheckpoint() {
        File f = new File(ARQUIVO_CHECKPOINT);
        if (f.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                documentoMestre = (Documento) ois.readObject();
                Logger.logSimulador("Estado do documento restaurado do último checkpoint.");
            } catch (IOException | ClassNotFoundException e) {
                Logger.logSimulador("ERRO: Falha ao carregar checkpoint: " + e.getMessage());
                documentoMestre = new Documento();
            }
        } else {
            Logger.logSimulador("Nenhum arquivo de checkpoint encontrado. Iniciando com um documento novo.");
            documentoMestre = new Documento();
        }
    }
}