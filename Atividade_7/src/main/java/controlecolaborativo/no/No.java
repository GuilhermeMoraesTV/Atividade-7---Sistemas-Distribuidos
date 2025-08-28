package controlecolaborativo.no;

import controlecolaborativo.comum.Documento;
import controlecolaborativo.comum.Logger;
import controlecolaborativo.comum.Mensagem;
import controlecolaborativo.coordenador.ServicoCoordenador;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class No {
    private final int id;
    private final int portaEleicao;
    private final Map<Integer, Integer> peers;

    private Documento documentoLocal = new Documento();
    private final AtomicInteger relogioLamport = new AtomicInteger(0);
    private final AtomicBoolean temPermissao = new AtomicBoolean(false);

    private volatile int coordinatorId;
    private volatile boolean electionInProgress = false;
    private ServicoCoordenador servicoCoordenador;
    private Thread coordinatorThread;

    private Socket socketCoordenador;
    private ObjectOutputStream outCoordenador;
    private ObjectInputStream inCoordenador;

    public No(int id, Map<Integer, Integer> peers) {
        this.id = id;
        this.peers = peers;
        this.portaEleicao = peers.get(id);
    }

    public void iniciar() {
        new Thread(new ElectionListener()).start();
        this.coordinatorId = peers.keySet().stream().max(Integer::compareTo).orElse(this.id);
        Logger.logNo(id, "Coordenador inicial definido como P" + coordinatorId);

        if (this.id == this.coordinatorId) {
            declareVictory();
        } else {
            connectToCoordinator();
        }
        new Thread(this::simularAtividade).start();
    }

    private void connectToCoordinator() {
        if (id == coordinatorId) return;

        int tentativas = 0;
        final int MAX_TENTATIVAS = 3;

        while (tentativas < MAX_TENTATIVAS) {
            try {
                Logger.logNo(id, String.format("Tentando conectar ao coordenador P%d (Tentativa %d/%d)...", coordinatorId, tentativas + 1, MAX_TENTATIVAS));
                socketCoordenador = new Socket("localhost", 12345);
                outCoordenador = new ObjectOutputStream(socketCoordenador.getOutputStream());
                inCoordenador = new ObjectInputStream(socketCoordenador.getInputStream());
                new Thread(new OuvinteCoordenador()).start();

                relogioLamport.incrementAndGet();
                enviarMensagemCoordenador(new Mensagem(Mensagem.Tipo.REQUISICAO_SC, this.id, relogioLamport.get(), null));
                Logger.logNo(id, "Conectado com sucesso ao coordenador P" + coordinatorId);
                return;
            } catch (IOException e) {
                tentativas++;
                try {
                    Thread.sleep(2000); // Aumenta o tempo de espera
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        Logger.logEleicao(id, String.format("Não foi possível conectar ao coordenador P%d. Iniciando nova eleição.", coordinatorId));
        startElection();
    }

    private void simularAtividade() {
        Random random = new Random();
        try {
            Thread.sleep(5000 + random.nextInt(3000)); // Espera inicial maior
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Aumenta o tempo de espera entre as ações para tornar a simulação mais lenta
                Thread.sleep(8000 + random.nextInt(10000));

                Logger.logNo(id, "Deseja editar o documento. Solicitando acesso...");
                solicitarSecaoCritica();

                while (!temPermissao.get()) {
                    Thread.sleep(100);
                }

                Logger.logNo(id, "Permissão recebida! Entrando na seção crítica.");
                Logger.logNo(id, "Conteúdo ANTES da edição:\n" + documentoLocal.obterConteudo());

                documentoLocal.adicionarLinha("Nova linha adicionada por P" + id);
                Thread.sleep(3000 + random.nextInt(2000)); // Simula tempo de "digitação"

                Logger.logNo(id, "Conteúdo DEPOIS da edição:\n" + documentoLocal.obterConteudo());
                Logger.logNo(id, "Saindo da seção crítica e liberando o recurso.");

                liberarSecaoCritica();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void solicitarSecaoCritica() {
        try {
            relogioLamport.incrementAndGet();
            Mensagem msg = new Mensagem(Mensagem.Tipo.REQUISICAO_SC, this.id, relogioLamport.get(), null);
            enviarMensagemCoordenador(msg);
        } catch (IOException e) {
            Logger.logEleicao(id, "Erro ao solicitar seção crítica. Coordenador pode ter caído.");
            startElection();
        }
    }

    private void liberarSecaoCritica() {
        try {
            temPermissao.set(false);
            relogioLamport.incrementAndGet();
            Mensagem msg = new Mensagem(Mensagem.Tipo.LIBERACAO_SC, this.id, relogioLamport.get(), documentoLocal.clonar());
            enviarMensagemCoordenador(msg);
        } catch (IOException e) {
            Logger.logEleicao(id, "Erro ao liberar recurso. Coordenador pode ter caído.");
            startElection();
        }
    }

    private void enviarMensagemCoordenador(Mensagem msg) throws IOException {
        if (outCoordenador != null) {
            synchronized (outCoordenador) {
                outCoordenador.writeObject(msg);
                outCoordenador.flush();
            }
        } else if (this.id != this.coordinatorId) {
            throw new IOException("A conexão com o coordenador não está estabelecida.");
        }
    }

    private void startElection() {
        if (electionInProgress) return;
        electionInProgress = true;
        Logger.logEleicao(id, "INICIOU UMA ELEIÇÃO.");

        try {
            boolean temNosMaiores = false;
            for (int peerId : peers.keySet()) {
                if (peerId > this.id) {
                    temNosMaiores = true;
                    sendMessageToPeer(peerId, new Mensagem(Mensagem.Tipo.ELECTION, this.id, 0, null));
                }
            }

            if (!temNosMaiores) {
                declareVictory();
                return;
            }

            Thread.sleep(3000); // Aumenta o timeout da eleição

            if (electionInProgress) {
                declareVictory();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void declareVictory() {
        Logger.logEleicao(id, "*** EU SOU O NOVO COORDENADOR! ***");
        this.coordinatorId = this.id;
        this.electionInProgress = false;

        if (coordinatorThread != null && coordinatorThread.isAlive()) {
            servicoCoordenador.parar();
        }

        servicoCoordenador = new ServicoCoordenador(this.id, 12345);
        coordinatorThread = new Thread(servicoCoordenador);
        coordinatorThread.start();

        for (int peerId : peers.keySet()) {
            if (peerId != this.id) {
                sendMessageToPeer(peerId, new Mensagem(Mensagem.Tipo.VICTORY, this.id, 0, null));
            }
        }
    }

    private void sendMessageToPeer(int peerId, Mensagem msg) {
        try (Socket socket = new Socket("localhost", peers.get(peerId));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.writeObject(msg);
        } catch (IOException e) {
            // Silencia esta mensagem para não poluir o log
        }
    }

    private class ElectionListener implements Runnable {
        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(portaEleicao)) {
                while (!Thread.currentThread().isInterrupted()) {
                    try (Socket clientSocket = serverSocket.accept();
                         ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                        Mensagem msg = (Mensagem) in.readObject();
                        switch (msg.getTipo()) {
                            case ELECTION:
                                Logger.logEleicao(id, "Recebeu ELECTION de P" + msg.getIdRemetente());
                                if (msg.getIdRemetente() < id) {
                                    sendMessageToPeer(msg.getIdRemetente(), new Mensagem(Mensagem.Tipo.OK, id, 0, null));
                                }
                                if(!electionInProgress) startElection();
                                break;
                            case OK:
                                Logger.logEleicao(id, "Recebeu OK de P" + msg.getIdRemetente() + ". Perdendo a eleição.");
                                electionInProgress = false;
                                break;
                            case VICTORY:
                                Logger.logEleicao(id, "P" + msg.getIdRemetente() + " é o novo coordenador.");
                                coordinatorId = msg.getIdRemetente();
                                electionInProgress = false;
                                if (socketCoordenador != null) socketCoordenador.close();
                                connectToCoordinator();
                                break;
                        }
                    } catch (Exception e) {}
                }
            } catch (IOException e) {
                Logger.logEleicao(id, "ERRO CRÍTICO: Não foi possível iniciar o servidor de eleição.");
            }
        }
    }

    private class OuvinteCoordenador implements Runnable {
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Mensagem msg = (Mensagem) inCoordenador.readObject();
                    int relogioCoordenador = msg.getRelogioLamport();
                    relogioLamport.set(Math.max(relogioLamport.get(), relogioCoordenador) + 1);

                    switch (msg.getTipo()) {
                        case PERMISSAO_SC:
                            temPermissao.set(true);
                            break;
                        case ATUALIZACAO_DOCUMENTO:
                            documentoLocal = (Documento) msg.getConteudo();
                            Logger.logNo(id, "Réplica do documento atualizada. Relógio: " + relogioLamport.get());
                            break;
                    }
                }
            } catch (Exception e) {
                if(!Thread.currentThread().isInterrupted()) {
                    Logger.logEleicao(id, "Conexão com o coordenador P" + coordinatorId + " perdida.");
                    startElection();
                }
            }
        }
    }
}