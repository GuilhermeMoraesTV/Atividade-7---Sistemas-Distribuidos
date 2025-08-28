package controlecolaborativo.coordenador;

import controlecolaborativo.comum.Documento;
import controlecolaborativo.comum.Logger;
import controlecolaborativo.comum.Mensagem;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Representa uma thread dedicada a gerenciar a comunicação com um único nó cliente.
 * Cada instância desta classe é responsável por ouvir as mensagens de um nó específico
 * e delegar o processamento para a instância principal do ServicoCoordenador.
 */
public class TratadorNo implements Runnable {
    private final Socket socketNo;
    private int idNo; // O ID do nó cliente conectado a esta thread.
    private final ServicoCoordenador coordenador;
    private final int idCoordenador;
    private ObjectOutputStream out;

    public TratadorNo(Socket socketNo, ServicoCoordenador coordenador, int idCoordenador) {
        this.socketNo = socketNo;
        this.coordenador = coordenador;
        this.idCoordenador = idCoordenador;
        this.idNo = -1; // Inicia como "não identificado".
    }

    /**
     * Método principal da thread. Estabelece os fluxos de comunicação,
     * identifica o nó e entra em um loop para processar mensagens.
     */
    @Override
    public void run() {
        try {
            this.out = new ObjectOutputStream(socketNo.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socketNo.getInputStream());

            // A primeira mensagem é usada para o "handshake", identificando o nó.
            Mensagem primeiraMensagem = (Mensagem) in.readObject();
            this.idNo = primeiraMensagem.getIdRemetente();

            // Uma vez identificado, o nó é registrado oficialmente no coordenador.
            coordenador.registrarNo(this.idNo, out);
            processarMensagem(primeiraMensagem);

            // Loop infinito para receber e processar as mensagens subsequentes.
            while (true) {
                Mensagem msg = (Mensagem) in.readObject();
                processarMensagem(msg);
            }

        } catch (Exception e) {
            // Se ocorrer qualquer exceção (ex: desconexão do cliente),
            // remove o nó do sistema para manter a consistência.
            if (idNo != -1) {
                coordenador.removerNo(idNo);
            }
        }
    }

    /**
     * Delega a ação apropriada para o coordenador com base no tipo da mensagem recebida.
     * @param msg A mensagem recebida do nó cliente.
     */
    private void processarMensagem(Mensagem msg) {
        Logger.logCoordenador(idCoordenador, "Mensagem recebida de P" + this.idNo + ": " + msg.getTipo());

        switch (msg.getTipo()) {
            case REQUISICAO_SC:
                coordenador.solicitarAcesso(this.idNo, msg.getRelogioLamport());
                break;
            case LIBERACAO_SC:
                Documento docAtualizado = (Documento) msg.getConteudo();
                coordenador.liberarRecurso(this.idNo, docAtualizado);
                break;
            default:
                Logger.logCoordenador(idCoordenador, "AVISO: Mensagem de tipo inesperado recebida: " + msg.getTipo());
        }
    }
}