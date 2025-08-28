// src/main/java/controlecolaborativo/comum/Mensagem.java

package controlecolaborativo.comum;

import java.io.Serializable;

public class Mensagem implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tipo {
        REQUISICAO_SC,
        PERMISSAO_SC,
        LIBERACAO_SC,
        ATUALIZACAO_DOCUMENTO,

        // Mensagens para o Algoritmo de Eleição (Bully)
        ELECTION, // Um nó inicia uma eleição
        OK,       // Uma resposta para uma mensagem de eleição
        VICTORY   // O vencedor se anuncia como o novo coordenador
    }

    private final Tipo tipo;
    private final int idRemetente;
    private final int relogioLamport;
    private final Object conteudo;

    public Mensagem(Tipo tipo, int idRemetente, int relogioLamport, Object conteudo) {
        this.tipo = tipo;
        this.idRemetente = idRemetente;
        this.relogioLamport = relogioLamport;
        this.conteudo = conteudo;
    }

    // Getters
    public Tipo getTipo() { return tipo; }
    public int getIdRemetente() { return idRemetente; }
    public int getRelogioLamport() { return relogioLamport; }
    public Object getConteudo() { return conteudo; }

    @Override
    public String toString() {
        return String.format("Msg(Tipo: %s, De: P%d, Relógio: %d)", tipo, idRemetente, relogioLamport);
    }
}