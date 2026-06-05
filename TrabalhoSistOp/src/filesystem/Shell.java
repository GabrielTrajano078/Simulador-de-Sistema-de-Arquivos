package filesystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Interface Shell interativa para o simulador de sistema de arquivos.
 */
public class Shell {

    private final FileSystemSimulator fs;
    private boolean running;

    public Shell(FileSystemSimulator fs) {
        this.fs = fs;
        this.running = true;
    }

    public void start() throws IOException {
        printBanner();
        printHelp();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while (running) {
            System.out.print("fs> ");
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            processCommand(line);
        }

        System.out.println("Encerrando simulador. Estado salvo em disco.");
    }

    private void processCommand(String line) {
        String[] parts = splitCommand(line);
        String command = parts[0].toLowerCase();

        try {
            switch (command) {
                case "help":
                    printHelp();
                    break;
                case "exit":
                case "quit":
                    running = false;
                    break;
                case "mkdir":
                    requireArgs(parts, 2, "mkdir <caminho>");
                    fs.createDirectory(parts[1]);
                    System.out.println("Diretório criado: " + parts[1]);
                    break;
                case "rmdir":
                    requireArgs(parts, 2, "rmdir <caminho>");
                    fs.deleteDirectory(parts[1]);
                    System.out.println("Diretório removido: " + parts[1]);
                    break;
                case "renamedir":
                    requireArgs(parts, 3, "renamedir <caminho_antigo> <caminho_novo>");
                    fs.renameDirectory(parts[1], parts[2]);
                    System.out.println("Diretório renomeado: " + parts[1] + " -> " + parts[2]);
                    break;
                case "touch":
                    requireArgs(parts, 2, "touch <caminho> [conteúdo]");
                    String content = parts.length > 2 ? parts[2] : "";
                    fs.createFile(parts[1], content);
                    System.out.println("Arquivo criado: " + parts[1]);
                    break;
                case "rm":
                case "rmfile":
                    requireArgs(parts, 2, "rm <caminho>");
                    fs.deleteFile(parts[1]);
                    System.out.println("Arquivo removido: " + parts[1]);
                    break;
                case "rename":
                case "renamefile":
                    requireArgs(parts, 3, "rename <caminho_antigo> <caminho_novo>");
                    fs.renameFile(parts[1], parts[2]);
                    System.out.println("Arquivo renomeado: " + parts[1] + " -> " + parts[2]);
                    break;
                case "cp":
                case "copy":
                    requireArgs(parts, 3, "cp <origem> <destino>");
                    fs.copyFile(parts[1], parts[2]);
                    System.out.println("Arquivo copiado: " + parts[1] + " -> " + parts[2]);
                    break;
                case "ls":
                    String path = parts.length > 1 ? parts[1] : "/";
                    List<String> listing = fs.listDirectory(path);
                    if (listing.isEmpty()) {
                        System.out.println("(diretório vazio)");
                    } else {
                        listing.forEach(System.out::println);
                    }
                    break;
                case "tree":
                    fs.printTree();
                    break;
                case "journal":
                    printJournal();
                    break;
                default:
                    System.out.println("Comando desconhecido: " + command);
                    System.out.println("Digite 'help' para ver os comandos disponíveis.");
            }
        } catch (FileSystemSimulator.FileSystemException e) {
            System.out.println("Erro: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Erro de I/O: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }

    private void printJournal() throws IOException {
        List<Journal.JournalEntry> entries = fs.getJournal().readAll();
        if (entries.isEmpty()) {
            System.out.println("(journal vazio)");
            return;
        }
        System.out.println("--- Journal ---");
        for (Journal.JournalEntry entry : entries) {
            System.out.printf("[%s] %s | %s | %s%n",
                    entry.getStatus(), entry.getOperation(), entry.getParams(), entry.getId());
        }
    }

    private String[] splitCommand(String line) {
        if (line.startsWith("\"")) {
            return splitQuoted(line);
        }
        return line.split("\\s+");
    }

    private String[] splitQuoted(String line) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens.toArray(new String[0]);
    }

    private void requireArgs(String[] parts, int min, String usage) {
        if (parts.length < min) {
            throw new IllegalArgumentException("Uso: " + usage);
        }
    }

    private void printBanner() {
        System.out.println("========================================");
        System.out.println("  Simulador de Sistema de Arquivos");
        System.out.println("  Modo Shell com Journaling");
        System.out.println("========================================");
    }

    private void printHelp() {
        System.out.println();
        System.out.println("Comandos disponíveis:");
        System.out.println("  mkdir <caminho>                        - Criar diretório");
        System.out.println("  rmdir <caminho>                        - Apagar diretório (vazio)");
        System.out.println("  renamedir <antigo> <novo>              - Renomear diretório");
        System.out.println("  touch <caminho> [conteúdo]             - Criar arquivo");
        System.out.println("  rm <caminho>                             - Apagar arquivo");
        System.out.println("  rename <antigo> <novo>                 - Renomear arquivo");
        System.out.println("  cp <origem> <destino>                  - Copiar arquivo");
        System.out.println("  ls [caminho]                           - Listar diretório");
        System.out.println("  tree                                     - Exibir árvore completa");
        System.out.println("  journal                                  - Exibir log de operações");
        System.out.println("  help                                     - Exibir esta ajuda");
        System.out.println("  exit                                     - Sair");
        System.out.println();
    }
}
