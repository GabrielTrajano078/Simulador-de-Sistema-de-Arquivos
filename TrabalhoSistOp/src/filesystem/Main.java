package filesystem;

/**
 * Ponto de entrada do simulador de sistema de arquivos.
 */
public class Main {

    private static final String IMAGE_FILE = "filesystem.img";
    private static final String JOURNAL_FILE = "filesystem.journal";

    public static void main(String[] args) {
        try {
            FileSystemSimulator fs = new FileSystemSimulator(IMAGE_FILE, JOURNAL_FILE);
            Shell shell = new Shell(fs);
            shell.start();
        } catch (Exception e) {
            System.err.println("Erro fatal: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
