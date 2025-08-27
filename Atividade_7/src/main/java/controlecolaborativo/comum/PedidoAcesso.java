package controlecolaborativo.comum;

// Usado para ordenar os pedidos na fila de prioridade do Coordenador
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

    @Override
    public int compareTo(PedidoAcesso outro) {
        // Compara primeiro pelo relógio de Lamport
        if (this.relogioLamport != outro.relogioLamport) {
            return Integer.compare(this.relogioLamport, outro.relogioLamport);
        }
        // Se os relógios forem iguais, usa o ID do nó como critério de desempate
        return Integer.compare(this.idNo, outro.idNo);
    }

    @Override
    public String toString() {
        return String.format("P%d (Relógio: %d)", idNo, relogioLamport);
    }
}