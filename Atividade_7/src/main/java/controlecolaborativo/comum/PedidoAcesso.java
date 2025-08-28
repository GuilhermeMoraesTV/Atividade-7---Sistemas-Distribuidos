package controlecolaborativo.comum;

/**
 * Representa um pedido de acesso à seção crítica, encapsulando o ID do nó
 * e o seu timestamp de Lamport no momento da requisição.
 *
 * A classe implementa {@link Comparable} para permitir que os objetos
 * sejam ordenados automaticamente em uma {@link java.util.PriorityQueue},
 * garantindo a justiça e a ordem causal no atendimento das solicitações.
 */
public class PedidoAcesso implements Comparable<PedidoAcesso> {

    private final int idNo;
    private final int relogioLamport;

    public PedidoAcesso(int idNo, int relogioLamport) {
        this.idNo = idNo;
        this.relogioLamport = relogioLamport;
    }

    public int getIdNo() {
        return idNo;
    }

    /**
     * Define a lógica de ordenação para os pedidos na fila de prioridade.
     * A ordenação é a chave para o funcionamento correto do controle de concorrência.
     *
     * @param outro O outro objeto PedidoAcesso a ser comparado.
     * @return um valor negativo se este pedido tiver prioridade,
     * positivo se o outro tiver prioridade, ou zero se forem iguais.
     */
    @Override
    public int compareTo(PedidoAcesso outro) {
        // 1. Critério primário: o menor relógio de Lamport tem maior prioridade.
        if (this.relogioLamport != outro.relogioLamport) {
            return Integer.compare(this.relogioLamport, outro.relogioLamport);
        }
        // 2. Critério secundário (desempate): se os relógios são iguais,
        // o nó com o menor ID tem maior prioridade para evitar starvation.
        return Integer.compare(this.idNo, outro.idNo);
    }

    /**
     * Retorna uma representação em string do objeto, útil para logging.
     * @return Uma string formatada, ex: "P1 (Relógio: 5)".
     */
    @Override
    public String toString() {
        return String.format("P%d (Relógio: %d)", idNo, relogioLamport);
    }
}