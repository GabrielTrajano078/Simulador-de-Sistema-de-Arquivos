package filesystem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Simulador de sistema de arquivos com suporte a journaling.
 */
public class FileSystemSimulator {

    private static final String ROOT_NAME = "/";

    private final Directory root;
    private final Journal journal;
    private final Path imagePath;

    public FileSystemSimulator(String imageFileName, String journalFileName) {
        this.imagePath = Path.of(imageFileName);
        this.journal = new Journal(journalFileName);
        this.root = new Directory(ROOT_NAME);
        loadOrCreate();
        recoverFromJournal();
    }

    public Directory getRoot() {
        return root;
    }

    public Journal getJournal() {
        return journal;
    }

    // --- Operações do sistema de arquivos ---

    public void createDirectory(String path) throws IOException {
        executeWithJournal("CREATE_DIR", path, () -> {
            PathInfo info = resolveParent(path);
            if (info.parent.hasChild(info.name)) {
                throw new FileSystemException("Diretório já existe: " + path);
            }
            info.parent.addChild(new Directory(info.name));
        });
    }

    public void deleteDirectory(String path) throws IOException {
        executeWithJournal("DELETE_DIR", path, () -> {
            FileSystemNode node = resolveNode(path);
            if (!node.isDirectory()) {
                throw new FileSystemException("Não é um diretório: " + path);
            }
            Directory dir = (Directory) node;
            if (dir == root) {
                throw new FileSystemException("Não é possível apagar a raiz");
            }
            if (!dir.isEmpty()) {
                throw new FileSystemException("Diretório não está vazio: " + path);
            }
            dir.getParent().removeChild(dir.getName());
        });
    }

    public void renameDirectory(String oldPath, String newPath) throws IOException {
        String params = oldPath + " -> " + newPath;
        executeWithJournal("RENAME_DIR", params, () -> {
            FileSystemNode node = resolveNode(oldPath);
            if (!node.isDirectory()) {
                throw new FileSystemException("Não é um diretório: " + oldPath);
            }
            if (node == root) {
                throw new FileSystemException("Não é possível renomear a raiz");
            }
            PathInfo newInfo = resolveParent(newPath);
            if (newInfo.parent.hasChild(newInfo.name)) {
                throw new FileSystemException("Destino já existe: " + newPath);
            }
            Directory dir = (Directory) node;
            dir.getParent().removeChild(dir.getName());
            dir.setName(newInfo.name);
            newInfo.parent.addChild(dir);
        });
    }

    public void createFile(String path, String content) throws IOException {
        String params = path + " :: " + encode(content);
        executeWithJournal("CREATE_FILE", params, () -> {
            PathInfo info = resolveParent(path);
            if (info.parent.hasChild(info.name)) {
                throw new FileSystemException("Arquivo já existe: " + path);
            }
            info.parent.addChild(new File(info.name, content));
        });
    }

    public void deleteFile(String path) throws IOException {
        executeWithJournal("DELETE_FILE", path, () -> {
            FileSystemNode node = resolveNode(path);
            if (node.isDirectory()) {
                throw new FileSystemException("Não é um arquivo: " + path);
            }
            node.getParent().removeChild(node.getName());
        });
    }

    public void renameFile(String oldPath, String newPath) throws IOException {
        String params = oldPath + " -> " + newPath;
        executeWithJournal("RENAME_FILE", params, () -> {
            FileSystemNode node = resolveNode(oldPath);
            if (node.isDirectory()) {
                throw new FileSystemException("Não é um arquivo: " + oldPath);
            }
            PathInfo newInfo = resolveParent(newPath);
            if (newInfo.parent.hasChild(newInfo.name)) {
                throw new FileSystemException("Destino já existe: " + newPath);
            }
            File file = (File) node;
            file.getParent().removeChild(file.getName());
            file.setName(newInfo.name);
            newInfo.parent.addChild(file);
        });
    }

    public void copyFile(String sourcePath, String destPath) throws IOException {
        String params = sourcePath + " -> " + destPath;
        executeWithJournal("COPY_FILE", params, () -> {
            FileSystemNode node = resolveNode(sourcePath);
            if (node.isDirectory()) {
                throw new FileSystemException("Origem não é um arquivo: " + sourcePath);
            }
            PathInfo destInfo = resolveParent(destPath);
            if (destInfo.parent.hasChild(destInfo.name)) {
                throw new FileSystemException("Destino já existe: " + destPath);
            }
            File source = (File) node;
            destInfo.parent.addChild(source.copyWithName(destInfo.name));
        });
    }

    public List<String> listDirectory(String path) {
        FileSystemNode node = resolveNode(path);
        if (!node.isDirectory()) {
            throw new FileSystemException("Não é um diretório: " + path);
        }
        Directory dir = (Directory) node;
        List<String> result = new ArrayList<>();
        for (FileSystemNode child : dir.listChildren()) {
            String type = child.isDirectory() ? "[DIR]" : "[FILE]";
            result.add(type + " " + child.getName());
        }
        return result;
    }

    // --- Journaling e persistência ---

    private void executeWithJournal(String operation, String params, Runnable action) throws IOException {
        Journal.JournalEntry entry = journal.logPending(operation, params);
        try {
            action.run();
            saveImage();
            journal.commit(entry);
            journal.clearCommitted();
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private void loadOrCreate() {
        if (!Files.exists(imagePath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(imagePath, StandardCharsets.UTF_8);
            root.getChildren().clear();
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                parseImageLine(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar imagem do sistema de arquivos: " + e.getMessage(), e);
        }
    }

    private void parseImageLine(String line) {
        String[] parts = line.split(":", 4);
        String type = parts[0];
        String path = parts[1];

        if ("DIR".equals(type)) {
            createNodeInTree(path, true, null);
        } else if ("FILE".equals(type)) {
            String content = parts.length > 2 ? decode(parts[2]) : "";
            createNodeInTree(path, false, content);
        }
    }

    private void createNodeInTree(String path, boolean isDir, String content) {
        PathInfo info = resolveParent(path);
        if (info.parent.hasChild(info.name)) {
            return;
        }
        if (isDir) {
            info.parent.addChild(new Directory(info.name));
        } else {
            info.parent.addChild(new File(info.name, content));
        }
    }

    private void saveImage() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Simulador de Sistema de Arquivos - Imagem persistida");
        for (FileSystemNode node : root.collectDescendants()) {
            if (node == root) {
                continue;
            }
            String path = node.getAbsolutePath();
            if (node.isDirectory()) {
                lines.add("DIR:" + path);
            } else {
                File file = (File) node;
                lines.add("FILE:" + path + ":" + encode(file.getContent()));
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(imagePath, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * Recupera o sistema de arquivos a partir de entradas PENDING no journal.
     * Em caso de falha durante uma operação, a entrada pendente é descartada (rollback).
     */
    private void recoverFromJournal() {
        try {
            List<Journal.JournalEntry> pending = journal.getPendingEntries();
            if (!pending.isEmpty()) {
                System.out.println("[Journal] Detectadas " + pending.size()
                        + " operação(ões) incompleta(s). Executando rollback...");
                journal.clearCommitted();
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha na recuperação do journal: " + e.getMessage(), e);
        }
    }

    public void printTree() {
        printNode(root, 0);
    }

    private void printNode(FileSystemNode node, int depth) {
        if (node != root) {
            String indent = "  ".repeat(depth);
            String type = node.isDirectory() ? "DIR " : "FILE";
            System.out.println(indent + type + " " + node.getName()
                    + (node.isDirectory() ? "" : " (" + ((File) node).getContent().length() + " bytes)"));
        }
        if (node.isDirectory()) {
            for (FileSystemNode child : ((Directory) node).listChildren()) {
                printNode(child, node == root ? 0 : depth + 1);
            }
        }
    }

    // --- Utilitários de caminho ---

    private static class PathInfo {
        final Directory parent;
        final String name;

        PathInfo(Directory parent, String name) {
            this.parent = parent;
            this.name = name;
        }
    }

    private FileSystemNode resolveNode(String path) {
        String normalized = normalizePath(path);
        if ("/".equals(normalized)) {
            return root;
        }
        String[] parts = normalized.substring(1).split("/");
        FileSystemNode current = root;
        for (String part : parts) {
            if (!current.isDirectory()) {
                throw new FileSystemException("Caminho inválido: " + path);
            }
            Directory dir = (Directory) current;
            if (!dir.hasChild(part)) {
                throw new FileSystemException("Caminho não encontrado: " + path);
            }
            current = dir.getChild(part);
        }
        return current;
    }

    private PathInfo resolveParent(String path) {
        String normalized = normalizePath(path);
        if ("/".equals(normalized) || normalized.isEmpty()) {
            throw new FileSystemException("Caminho inválido: " + path);
        }
        int lastSlash = normalized.lastIndexOf('/');
        String parentPath = lastSlash <= 0 ? "/" : normalized.substring(0, lastSlash);
        String name = normalized.substring(lastSlash + 1);
        if (name.isEmpty()) {
            throw new FileSystemException("Nome inválido no caminho: " + path);
        }
        FileSystemNode parentNode = resolveNode(parentPath);
        if (!parentNode.isDirectory()) {
            throw new FileSystemException("Diretório pai inválido: " + parentPath);
        }
        return new PathInfo((Directory) parentNode, name);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new FileSystemException("Caminho não pode ser vazio");
        }
        String normalized = path.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public static class FileSystemException extends RuntimeException {
        public FileSystemException(String message) {
            super(message);
        }
    }
}
