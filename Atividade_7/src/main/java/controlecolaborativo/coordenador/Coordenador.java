package controlecolaborativo.coordenador;

import controlecolaborativo.comum.Documento;
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

public class Coordenador {
    private static final int PORTA = 12345;
    private static final String ARQUIVO_CHECKPOINT = "checkpoint.dat";
    private Documento documentoMestre;
    private final Queue<PedidoAcesso> filaRequisicoes = new PriorityQueue<>();
    private boolean recursoOcupado = false;
    private int idNoEmSecaoCritica = -1;
    private final Map<Integer, ObjectOutputStream> nosConectados = new ConcurrentHashMap<>();
    private final AtomicInteger relogioLamport = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public Coordenador() {
        carregarCheckpoint();
    }

    public static void main(String[] args) {
        new Coordenador().iniciar();
    }

    public void iniciar() {
        System.out.println("[COORDENADOR] Iniciando o servidor...");
        scheduler.scheduleAtFixedRate(this::salvarCheckpoint, 30, 30, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            System.out.println("[COORDENADOR] Servidor iniciado. Aguardando conexões de nós na porta " + PORTA);

            while (true) {
                Socket socketNo = serverSocket.accept();
                System.out.printf("[COORDENADOR] Nova conexão anônima recebida: %s%n", socketNo.getInetAddress().getHostAddress());
                // O Tratador agora é responsável por identificar o nó
                TratadorNo tratador = new TratadorNo(socketNo, this);
                new Thread(tratador).start();
            }
        } catch (IOException e) {
            System.err.println("[ERRO] Erro fatal no Coordenador: " + e.getMessage());
        } finally {
            scheduler.shutdown();
        }
    }

    public synchronized void registrarNo(int idNo, ObjectOutputStream out) {
        nosConectados.put(idNo, out);
        System.out.printf("[COORDENADOR] Nó P%d registrado no sistema.%n", idNo);
        enviarAtualizacaoDocumento(idNo);
    }

    public synchronized void removerNo(int idNo) {
        nosConectados.remove(idNo);
        filaRequisicoes.removeIf(pedido -> pedido.getIdNo() == idNo);
        System.out.printf("[COORDENADOR] Nó P%d desconectado.%n", idNo);

        if (idNo == idNoEmSecaoCritica) {
            System.out.printf("[ROLLBACK] Nó P%d caiu enquanto estava na seção crítica. A alteração foi descartada.%n", idNo);
            idNoEmSecaoCritica = -1;
            recursoOcupado = false;
            concederProximoAcesso();
        }
    }

    public synchronized void solicitarAcesso(int idNo, int relogioRemetente) {
        relogioLamport.set(Math.max(relogioLamport.get(), relogioRemetente) + 1);
        PedidoAcesso pedido = new PedidoAcesso(idNo, relogioRemetente);
        System.out.printf("[COORDENADOR] Nó %s solicitou acesso à seção crítica.%n", pedido);

        if (!recursoOcupado) {
            recursoOcupado = true;
            idNoEmSecaoCritica = idNo;
            enviarPermissao(idNo);
        } else {
            filaRequisicoes.add(pedido);
            System.out.printf("[COORDENADOR] Recurso ocupado. Pedido %s adicionado à fila. Fila atual: %s%n", pedido, filaRequisicoes);
        }
    }

    public synchronized void liberarRecurso(int idNo, Documento documentoAtualizado) {
        relogioLamport.incrementAndGet();
        System.out.printf("[COORDENADOR] Nó P%d liberou a seção crítica.%n", idNo);

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
            System.out.printf("[COORDENADOR] Concedendo permissão ao próximo da fila: %s.%n", proximoPedido);
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
                System.out.printf("[COORDENADOR] Permissão enviada para P%d.%n", idDestino);
            }
        } catch (IOException e) {
            System.err.printf("[ERRO] Falha ao enviar permissão para P%d. Removendo...%n", idDestino);
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
            System.err.printf("[ERRO] Falha ao enviar atualização para P%d. Removendo...%n", idDestino);
            removerNo(idDestino);
        }
    }

    private synchronized void salvarCheckpoint() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ARQUIVO_CHECKPOINT))) {
            oos.writeObject(documentoMestre);
            System.out.printf("[CHECKPOINT] Estado do documento salvo com sucesso. (Relógio: %d)%n", relogioLamport.get());
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao salvar checkpoint: " + e.getMessage());
        }
    }

    private synchronized void carregarCheckpoint() {
        File f = new File(ARQUIVO_CHECKPOINT);
        if (f.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                documentoMestre = (Documento) ois.readObject();
                System.out.println("[CHECKPOINT] Estado do documento restaurado do último checkpoint.");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("[ERRO] Falha ao carregar checkpoint: " + e.getMessage());
                documentoMestre = new Documento();
            }
        } else {
            System.out.println("[CHECKPOINT] Nenhum arquivo de checkpoint encontrado. Iniciando com um documento novo.");
            documentoMestre = new Documento();
        }
    }
}