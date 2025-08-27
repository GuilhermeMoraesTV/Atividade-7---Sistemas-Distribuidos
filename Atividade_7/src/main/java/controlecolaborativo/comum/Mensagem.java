package controlecolaborativo.comum;

import java.io.Serializable;

public class Mensagem implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tipo {
        REQUISICAO_SC, // Requisição para entrar na Seção Crítica
        PERMISSAO_SC,  // Permissão para entrar
        LIBERACAO_SC,  // Liberação da Seção Crítica
        ATUALIZACAO_DOCUMENTO // Nova versão do documento para as réplicas
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