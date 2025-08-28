package controlecolaborativo.coordenador;

import controlecolaborativo.comum.Documento;
import controlecolaborativo.comum.Logger;
import controlecolaborativo.comum.Mensagem;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class TratadorNo implements Runnable {
    private final Socket socketNo;
    private int idNo;
    private final ServicoCoordenador coordenador;
    private final int idCoordenador;
    private ObjectOutputStream out;

    public TratadorNo(Socket socketNo, ServicoCoordenador coordenador, int idCoordenador) {
        this.socketNo = socketNo;
        this.coordenador = coordenador;
        this.idCoordenador = idCoordenador;
        this.idNo = -1; // "não identificado"
    }

    @Override
    public void run() {
        try {
            this.out = new ObjectOutputStream(socketNo.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socketNo.getInputStream());

            Mensagem primeiraMensagem = (Mensagem) in.readObject();
            this.idNo = primeiraMensagem.getIdRemetente();

            coordenador.registrarNo(this.idNo, out);
            processarMensagem(primeiraMensagem);

            while (true) {
                Mensagem msg = (Mensagem) in.readObject();
                processarMensagem(msg);
            }

        } catch (Exception e) {
            if (idNo != -1) {
                coordenador.removerNo(idNo);
            }
        }
    }

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