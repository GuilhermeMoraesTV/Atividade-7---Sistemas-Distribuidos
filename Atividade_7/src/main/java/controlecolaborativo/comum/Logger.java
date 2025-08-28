package controlecolaborativo.comum;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Classe utilitária para centralizar e formatar a saída de logs do sistema.
 * Ela adiciona timestamps, formatação colorida e prefixos padronizados para
 * facilitar a depuração e a visualização da simulação em tempo real.
 */
public class Logger {
    // Constantes com códigos de escape ANSI para formatação de texto no console.
    public static final String ANSI_RESET = "\u001B[0m"; // Reseta formatação
    public static final String ANSI_RED = "\u001B[31m";   // Vermelho para eleições
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";  // Azul para o coordenador
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m"; // Branco para o simulador
    public static final String ANSI_BOLD = "\u001B[1m";   // Negrito

    // Formato padrão para o timestamp, incluindo milissegundos para maior precisão.
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    // Array de cores para diferenciar a saída de cada nó.
    private static final String[] CORES_NOS = {ANSI_CYAN, ANSI_GREEN, ANSI_YELLOW, ANSI_PURPLE};

    /**
     * Seleciona uma cor para um nó com base em seu ID.
     * O operador de módulo (%) garante que, mesmo que haja mais nós que cores,
     * uma cor válida será selecionada.
     *
     * @param id O ID do nó.
     * @return O código de cor ANSI correspondente.
     */
    private static String getCorNo(int id) {
        return CORES_NOS[(id - 1) % CORES_NOS.length];
    }

    /**
     * Imprime uma mensagem de log formatada para um Nó.
     *
     * @param id      O ID do nó que está logando.
     * @param message A mensagem a ser impressa.
     */
    public static void logNo(int id, String message) {
        String timestamp = sdf.format(new Date());
        System.out.printf("%s%s[NÓ P%d %s] %s%s%n",
                getCorNo(id), ANSI_BOLD, id, timestamp, message, ANSI_RESET);
    }

    /**
     * Imprime uma mensagem de log formatada para o Coordenador.
     *
     * @param id      O ID do nó que está atuando como coordenador.
     * @param message A mensagem a ser impressa.
     */
    public static void logCoordenador(int id, String message) {
        String timestamp = sdf.format(new Date());
        System.out.printf("%s%s[COORDENADOR P%d %s] %s%s%n",
                ANSI_BLUE, ANSI_BOLD, id, timestamp, message, ANSI_RESET);
    }

    /**
     * Imprime uma mensagem de log formatada para eventos de Eleição.
     *
     * @param id      O ID do nó envolvido no processo de eleição.
     * @param message A mensagem a ser impressa.
     */
    public static void logEleicao(int id, String message) {
        String timestamp = sdf.format(new Date());
        System.out.printf("%s%s[ELEIÇÃO P%d %s] %s%s%n",
                ANSI_RED, ANSI_BOLD, id, timestamp, message, ANSI_RESET);
    }

    /**
     * Imprime uma mensagem de log formatada para o Simulador.
     *
     * @param message A mensagem a ser impressa.
     */
    public static void logSimulador(String message) {
        String timestamp = sdf.format(new Date());
        System.out.printf("%s%s[SIMULADOR %s] %s%s%n",
                ANSI_WHITE, ANSI_BOLD, timestamp, message, ANSI_RESET);
    }
}