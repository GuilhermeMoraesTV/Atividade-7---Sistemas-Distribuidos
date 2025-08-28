package controlecolaborativo.comum;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    // Códigos de Cores ANSI
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";
    public static final String ANSI_BOLD = "\u001B[1m";

    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    private static final String[] CORES_NOS = {ANSI_CYAN, ANSI_GREEN, ANSI_YELLOW, ANSI_PURPLE};

    private static String getCorNo(int id) {
        return CORES_NOS[(id - 1) % CORES_NOS.length];
    }

    public static void logNo(int id, String message) {
        String timestamp = sdf.format(new Date());
        System.out.printf("%s%s[NÓ P%d %s] %s%s%n",
                getCorNo(id), ANSI_BOLD, id, timestamp, message, ANSI_RESET);
    }

    public static void logCoordenador(int id, String message) {
        String timestamp = sdf.format(new Date());
        System.out.printf("%s%s[COORDENADOR P%d %s] %s%s%n",
                ANSI_BLUE, ANSI_BOLD, id, timestamp, message, ANSI_RESET);
    }

    public static void logEleicao(int id, String message) {
        String timestamp = sdf.format(new Date());
        System.out.printf("%s%s[ELEIÇÃO P%d %s] %s%s%n",
                ANSI_RED, ANSI_BOLD, id, timestamp, message, ANSI_RESET);
    }

    public static void logSimulador(String message) {
        String timestamp = sdf.format(new Date());
        System.out.printf("%s%s[SIMULADOR %s] %s%s%n",
                ANSI_WHITE, ANSI_BOLD, timestamp, message, ANSI_RESET);
    }
}