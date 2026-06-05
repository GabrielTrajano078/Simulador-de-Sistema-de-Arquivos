package filesystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe abstrata base para arquivos e diretórios no simulador.
 */
public abstract class FileSystemNode {

    protected String name;
    protected Directory parent;

    public FileSystemNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Directory getParent() {
        return parent;
    }

    public void setParent(Directory parent) {
        this.parent = parent;
    }

    public abstract boolean isDirectory();

    /**
     * Retorna o caminho absoluto do nó a partir da raiz.
     */
    public String getAbsolutePath() {
        if (parent == null) {
            return "/";
        }
        String parentPath = parent.getAbsolutePath();
        if ("/".equals(parentPath)) {
            return "/" + name;
        }
        return parentPath + "/" + name;
    }

    /**
     * Coleta todos os descendentes deste nó (incluindo ele mesmo se for diretório).
     */
    public List<FileSystemNode> collectDescendants() {
        List<FileSystemNode> nodes = new ArrayList<>();
        collectDescendants(nodes);
        return nodes;
    }

    protected void collectDescendants(List<FileSystemNode> nodes) {
        nodes.add(this);
    }
}
