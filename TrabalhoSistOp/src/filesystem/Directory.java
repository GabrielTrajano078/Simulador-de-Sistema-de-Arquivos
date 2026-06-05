package filesystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa um diretório no sistema de arquivos simulado.
 */
public class Directory extends FileSystemNode {

    private final Map<String, FileSystemNode> children;

    public Directory(String name) {
        super(name);
        this.children = new LinkedHashMap<>();
    }

    public Map<String, FileSystemNode> getChildren() {
        return children;
    }

    public boolean hasChild(String name) {
        return children.containsKey(name);
    }

    public FileSystemNode getChild(String name) {
        return children.get(name);
    }

    public void addChild(FileSystemNode node) {
        children.put(node.getName(), node);
        node.setParent(this);
    }

    public FileSystemNode removeChild(String name) {
        FileSystemNode removed = children.remove(name);
        if (removed != null) {
            removed.setParent(null);
        }
        return removed;
    }

    public List<FileSystemNode> listChildren() {
        return new ArrayList<>(children.values());
    }

    public boolean isEmpty() {
        return children.isEmpty();
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    protected void collectDescendants(List<FileSystemNode> nodes) {
        nodes.add(this);
        for (FileSystemNode child : children.values()) {
            child.collectDescendants(nodes);
        }
    }
}
