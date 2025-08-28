package controlecolaborativo.comum;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Representa o recurso crítico compartilhado (o documento de texto).
 * Implementa Serializable para ser enviado pela rede e salvo em checkpoints.
 */
public class Documento implements Serializable {
    // Identificador para garantir a compatibilidade durante a serialização.
    private static final long serialVersionUID = 1L;
    private final List<String> linhas;

    public Documento() {
        this.linhas = new ArrayList<>();
        this.linhas.add("Linha inicial do documento.");
    }

    /**
     * Adiciona uma nova linha ao documento.
     * O método é 'synchronized' para garantir a segurança em ambiente com múltiplas threads (thread-safe).
     * @param linha A linha de texto a ser adicionada.
     */
    public synchronized void adicionarLinha(String linha) {
        this.linhas.add(linha);
    }

    /**
     * Remove uma linha do documento pelo seu índice.
     * @param indice A posição da linha a ser removida.
     */
    public synchronized void removerLinha(int indice) {
        if (indice >= 0 && indice < this.linhas.size()) {
            this.linhas.remove(indice);
        }
    }

    /**
     * Retorna o conteúdo completo do documento como uma única String.
     * @return O conteúdo do documento.
     */
    public synchronized String obterConteudo() {
        return this.linhas.stream().collect(Collectors.joining("\n"));
    }

    /**
     * Cria uma cópia profunda (clone) do objeto Documento.
     * Essencial para evitar que o estado seja modificado acidentalmente
     * ao passar o documento entre diferentes partes do sistema.
     * @return Um novo objeto Documento com o mesmo conteúdo.
     */
    public synchronized Documento clonar() {
        Documento novoDoc = new Documento();
        novoDoc.linhas.clear(); // Limpa a linha inicial padrão
        novoDoc.linhas.addAll(this.linhas);
        return novoDoc;
    }
}