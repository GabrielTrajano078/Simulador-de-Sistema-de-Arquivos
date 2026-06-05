package filesystem;

/**
 * Representa um arquivo no sistema de arquivos simulado.
 */
public class File extends FileSystemNode {

    private String content;

    public File(String name) {
        super(name);
        this.content = "";
    }

    public File(String name, String content) {
        super(name);
        this.content = content != null ? content : "";
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content != null ? content : "";
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    public File copyWithName(String newName) {
        File copy = new File(newName, content);
        return copy;
    }
}
