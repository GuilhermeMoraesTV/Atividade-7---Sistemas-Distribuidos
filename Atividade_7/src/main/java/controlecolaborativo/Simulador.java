package controlecolaborativo;

import controlecolaborativo.comum.Logger;
import controlecolaborativo.no.No;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Simulador {

    private static final int NUMERO_DE_NOS = 4;
    private static final int PORTA_BASE_ELEICAO = 6000;

    public static void main(String[] args) throws InterruptedException {
        // 1. Mapear os IDs e portas de eleição para todos os nós
        Map<Integer, Integer> peers = new HashMap<>();
        for (int i = 1; i <= NUMERO_DE_NOS; i++) {
            peers.put(i, PORTA_BASE_ELEICAO + i);
        }

        // 2. Criar e iniciar todos os nós
        List<Thread> nodeThreads = new ArrayList<>();
        for (int i = 1; i <= NUMERO_DE_NOS; i++) {
            No no = new No(i, peers);
            Thread thread = new Thread(no::iniciar);
            nodeThreads.add(thread);
            thread.start();
        }

        Logger.logSimulador("Todos os nós foram iniciados.");
        Logger.logSimulador("O sistema irá operar normalmente por 45 segundos.");
        Thread.sleep(45000); // Tempo para observar a operação normal

        // 3. Simular a falha do coordenador atual (o de maior ID)
        int initialCoordinatorId = NUMERO_DE_NOS;
        Logger.logSimulador(String.format(">>> SIMULANDO A FALHA DO COORDENADOR P%d <<<", initialCoordinatorId));

        // Encontra a thread do coordenador e a interrompe
        Thread coordinatorThread = nodeThreads.get(initialCoordinatorId - 1);
        coordinatorThread.interrupt(); // Interromper a thread simula a falha

        Logger.logSimulador("A eleição deve começar em breve...");
        Logger.logSimulador("O novo coordenador será o nó com o maior ID restante (P3).");

        // --- SEÇÃO DE ENCERRAMENTO ---
        Logger.logSimulador("O sistema continuará operando sob a nova liderança por mais 60 segundos antes de encerrar.");
        Thread.sleep(60000); // 1 minuto de operação após a falha

        Logger.logSimulador("--- SIMULAÇÃO FINALIZADA ---");
        System.exit(0); // Encerra a JVM e todos os processos (nós)
    }
}