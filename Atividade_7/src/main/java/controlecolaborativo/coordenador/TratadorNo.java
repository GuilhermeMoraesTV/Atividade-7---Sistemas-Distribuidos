package controlecolaborativo.coordenador;

import controlecolaborativo.comum.Documento;
import controlecolaborativo.comum.Mensagem;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class TratadorNo implements Runnable {
    private final Socket socketNo;
    private int idNo; // A identidade real do nó nesta conexão
    private final Coordenador coordenador;
    private ObjectOutputStream out;

    public TratadorNo(Socket socketNo, Coordenador coordenador) {
        this.socketNo = socketNo;
        this.coordenador = coordenador;
        this.idNo = -1; // -1 significa "não identificado"
    }

    @Override
    public void run() {
        try {
            this.out = new ObjectOutputStream(socketNo.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socketNo.getInputStream());

            // A primeira mensagem de um nó serve para identificá-lo
            Mensagem primeiraMensagem = (Mensagem) in.readObject();
            this.idNo = primeiraMensagem.getIdRemetente(); // Descobrimos o ID real!

            // Agora que sabemos quem é, registramos no coordenador
            coordenador.registrarNo(this.idNo, out);

            // E processamos a primeira mensagem que já lemos
            processarMensagem(primeiraMensagem);

            // Loop para as mensagens seguintes
            while (true) {
                Mensagem msg = (Mensagem) in.readObject();
                processarMensagem(msg);
            }

        } catch (Exception e) {
            // Se der erro, removemos o nó (se ele já tiver sido identificado)
            if (idNo != -1) {
                coordenador.removerNo(idNo);
            }
        }
    }

    private void processarMensagem(Mensagem msg) {
        System.out.printf("[COORDENADOR] Mensagem recebida de P%d: %s%n", this.idNo, msg.getTipo());

        switch (msg.getTipo()) {
            case REQUISICAO_SC:
                coordenador.solicitarAcesso(this.idNo, msg.getRelogioLamport());
                break;
            case LIBERACAO_SC:
                Documento docAtualizado = (Documento) msg.getConteudo();
                coordenador.liberarRecurso(this.idNo, docAtualizado);
                break;
            default:
                System.err.println("[AVISO] Coordenador recebeu mensagem de tipo inesperado: " + msg.getTipo());
        }
    }
}