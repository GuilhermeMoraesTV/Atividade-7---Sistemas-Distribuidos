package controlecolaborativo;

import controlecolaborativo.coordenador.Coordenador;
import controlecolaborativo.no.No;

/**
 * Classe principal para iniciar e orquestrar a simulação do sistema.
 */
public class Simulador {
    public static void main(String[] args) {
        final int NUMERO_DE_NOS = 4;

        // Inicia o Coordenador em uma nova thread
        Thread threadCoordenador = new Thread(() -> {
            Coordenador.main(null);
        });
        threadCoordenador.start();

        // Aguarda um pouco para garantir que o coordenador esteja pronto
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {}

        // Inicia múltiplos Nós (clientes), cada um em sua própria thread
        for (int i = 1; i <= NUMERO_DE_NOS; i++) {
            final int idNoFinal = i; // Cria uma cópia final da variável
            new Thread(() -> {
                No no = new No(idNoFinal); // Usa a cópia final
                no.iniciar("localhost", 12345);
            }).start();
        }

        System.out.println("[SIMULADOR] Simulação iniciada com 1 Coordenador e " + NUMERO_DE_NOS + " Nós.");
    }
}