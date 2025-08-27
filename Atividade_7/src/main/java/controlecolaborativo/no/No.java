package controlecolaborativo.no;

import controlecolaborativo.comum.Documento;
import controlecolaborativo.comum.Mensagem;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Representa um nó (cliente) que participa no sistema de controle colaborativo.
 */
public class No {
    private int id; // Será atribuído pelo Coordenador (simulado no Simulador)
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // Réplica local do documento
    private Documento documentoLocal = new Documento();
    private final AtomicInteger relogioLamport = new AtomicInteger(0);
    private final AtomicBoolean temPermissao = new AtomicBoolean(false);

    public No(int id) {
        this.id = id;
        
    }

    public void iniciar(String host, int porta) {
        try {
            socket = new Socket(host, porta);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Inicia uma thread para escutar mensagens do coordenador
            Thread ouvinte = new Thread(new OuvinteCoordenador());
            ouvinte.start();

            // Inicia o loop de simulação de atividade
            simularAtividade();

        } catch (Exception e) {
            System.err.printf("[NÓ P%d] Não foi possível conectar ao coordenador: %s%n", id, e.getMessage());
        }
    }

    private void simularAtividade() {
        Random random = new Random();
        while (true) {
            try {
                // Espera um tempo aleatório antes de tentar editar
                Thread.sleep(5000 + random.nextInt(10000));

                System.out.printf("[NÓ P%d] Quero editar o documento. Solicitando acesso...%n", id);
                solicitarSecaoCritica();

                // Espera até receber a permissão
                while(!temPermissao.get()) {
                    Thread.sleep(100);
                }

                // --- SEÇÃO CRÍTICA ---
                System.out.printf("[NÓ P%d] Permissão recebida! Entrando na seção crítica.%n", id);
                System.out.printf("  Conteúdo ANTES da edição por P%d:%n%s%n", id, documentoLocal.obterConteudo());

                // Simula a edição do documento
                Thread.sleep(2000 + random.nextInt(3000));
                documentoLocal.adicionarLinha("Nova linha adicionada por P" + id);
                System.out.printf("  Conteúdo DEPOIS da edição por P%d:%n%s%n", id, documentoLocal.obterConteudo());

                System.out.printf("[NÓ P%d] Saindo da seção crítica. Liberando o recurso...%n", id);
                liberarSecaoCritica();
                // --- FIM DA SEÇÃO CRÍTICA ---

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void solicitarSecaoCritica() {
        relogioLamport.incrementAndGet();
        enviarMensagem(new Mensagem(Mensagem.Tipo.REQUISICAO_SC, id, relogioLamport.get(), null));
    }

    private void liberarSecaoCritica() {
        temPermissao.set(false);
        relogioLamport.incrementAndGet();
        // Envia a versão atualizada do documento ao liberar
        enviarMensagem(new Mensagem(Mensagem.Tipo.LIBERACAO_SC, id, relogioLamport.get(), documentoLocal.clonar()));
    }

    private synchronized void enviarMensagem(Mensagem msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (Exception e) {
            System.err.printf("[NÓ P%d] Erro ao enviar mensagem: %s%n", id, e.getMessage());
        }
    }

    /**
     * Thread interna que fica escutando por mensagens vindas do Coordenador.
     */
    private class OuvinteCoordenador implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Mensagem msg = (Mensagem) in.readObject();
                    relogioLamport.set(Math.max(relogioLamport.get(), msg.getRelogioLamport()) + 1);

                    switch (msg.getTipo()) {
                        case PERMISSAO_SC:
                            temPermissao.set(true);
                            break;
                        case ATUALIZACAO_DOCUMENTO:
                            documentoLocal = (Documento) msg.getConteudo();
                            System.out.printf("[NÓ P%d] Réplica do documento atualizada. Relógio: %d.%n", id, relogioLamport.get());
                            break;
                    }
                }
            } catch (Exception e) {
                System.err.printf("[NÓ P%d] Conexão com o coordenador perdida. Encerrando.%n", id);
            }
        }
    }
}