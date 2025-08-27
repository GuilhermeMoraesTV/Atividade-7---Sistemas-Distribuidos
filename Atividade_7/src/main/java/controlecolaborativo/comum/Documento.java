package controlecolaborativo.comum;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Documento implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<String> linhas;

    public Documento() {
        this.linhas = new ArrayList<>();
        this.linhas.add("Linha inicial do documento.");
    }

    public synchronized void adicionarLinha(String linha) {
        this.linhas.add(linha);
    }

    public synchronized void removerLinha(int indice) {
        if (indice >= 0 && indice < this.linhas.size()) {
            this.linhas.remove(indice);
        }
    }

    public synchronized String obterConteudo() {
        return this.linhas.stream().collect(Collectors.joining("\n"));
    }

    public synchronized Documento clonar() {
        Documento novoDoc = new Documento();
        novoDoc.linhas.clear();
        novoDoc.linhas.addAll(this.linhas);
        return novoDoc;
    }
}