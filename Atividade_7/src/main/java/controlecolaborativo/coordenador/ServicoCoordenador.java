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

public class ServicoCoordenador implements Runnable {

    private static final String ARQUIVO_CHECKPOINT = "checkpoint.dat";
    private final int porta;
    private final int idCoordenador;

    private Documento documentoMestre;
    private final Queue<PedidoAcesso> filaRequisicoes = new PriorityQueue<>();
    private boolean recursoOcupado = false;
    private int idNoEmSecaoCritica = -1;
    private final Map<Integer, ObjectOutputStream> nosConectados = new ConcurrentHashMap<>();
    private final AtomicInteger relogioLamport = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private volatile boolean rodando = true;
    private ServerSocket serverSocket;

    public ServicoCoordenador(int idCoordenador, int porta) {
        this.idCoordenador = idCoordenador;
        this.porta = porta;
        carregarCheckpoint();
    }

    public void parar() {
        this.rodando = false;
        scheduler.shutdownNow();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Logger.logCoordenador(idCoordenador, "Erro ao fechar o socket do servidor: " + e.getMessage());
        }
        Logger.logCoordenador(idCoordenador, "Serviço encerrado.");
    }

    @Override
    public void run() {
        Logger.logCoordenador(idCoordenador, "Iniciando o serviço na porta " + porta + "...");
        scheduler.scheduleAtFixedRate(this::salvarCheckpoint, 30, 30, TimeUnit.SECONDS);

        try {
            serverSocket = new ServerSocket(porta);
            while (rodando) {
                Socket socketNo = serverSocket.accept();
                Logger.logCoordenador(idCoordenador, "Nova conexão anônima recebida: " + socketNo.getInetAddress().getHostAddress());
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

    public synchronized void registrarNo(int idNo, ObjectOutputStream out) {
        nosConectados.put(idNo, out);
        Logger.logCoordenador(idCoordenador, "Nó P" + idNo + " registrado no sistema.");
        enviarAtualizacaoDocumento(idNo);
    }

    public synchronized void removerNo(int idNo) {
        nosConectados.remove(idNo);
        filaRequisicoes.removeIf(pedido -> pedido.getIdNo() == idNo);
        Logger.logCoordenador(idCoordenador, "Nó P" + idNo + " desconectado.");

        if (idNo == idNoEmSecaoCritica) {
            Logger.logCoordenador(idCoordenador, "[ROLLBACK] Nó P" + idNo + " caiu na seção crítica. Alteração descartada.");
            idNoEmSecaoCritica = -1;
            recursoOcupado = false;
            concederProximoAcesso();
        }
    }

    public synchronized void solicitarAcesso(int idNo, int relogioRemetente) {
        relogioLamport.set(Math.max(relogioLamport.get(), relogioRemetente) + 1);
        PedidoAcesso pedido = new PedidoAcesso(idNo, relogioRemetente);
        Logger.logCoordenador(idCoordenador, "Nó " + pedido + " solicitou acesso à seção crítica.");

        if (!recursoOcupado) {
            recursoOcupado = true;
            idNoEmSecaoCritica = idNo;
            enviarPermissao(idNo);
        } else {
            filaRequisicoes.add(pedido);
            Logger.logCoordenador(idCoordenador, "Recurso ocupado. Pedido " + pedido + " adicionado à fila. Fila: " + filaRequisicoes);
        }
    }

    public synchronized void liberarRecurso(int idNo, Documento documentoAtualizado) {
        relogioLamport.incrementAndGet();
        Logger.logCoordenador(idCoordenador, "Nó P" + idNo + " liberou a seção crítica.");

        this.documentoMestre = documentoAtualizado;
        nosConectados.keySet().forEach(this::enviarAtualizacaoDocumento);

        idNoEmSecaoCritica = -1;
        recursoOcupado = false;
        concederProximoAcesso();
    }

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

    private void enviarAtualizacaoDocumento(int idDestino) {
        try {
            ObjectOutputStream out = nosConectados.get(idDestino);
            if (out != null) {
                relogioLamport.incrementAndGet();
                Mensagem msg = new Mensagem(Mensagem.Tipo.ATUALIZACAO_DOCUMENTO, 0, relogioLamport.get(), documentoMestre.clonar());
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            Logger.logCoordenador(idCoordenador, "Falha ao enviar atualização para P" + idDestino + ". Removendo...");
            removerNo(idDestino);
        }
    }

    private synchronized void salvarCheckpoint() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ARQUIVO_CHECKPOINT))) {
            oos.writeObject(documentoMestre);
            Logger.logCoordenador(idCoordenador, "CHECKPOINT salvo com sucesso. (Relógio: " + relogioLamport.get() + ")");
        } catch (IOException e) {
            Logger.logCoordenador(idCoordenador, "ERRO: Falha ao salvar checkpoint: " + e.getMessage());
        }
    }

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