# Simulador de Sistema de Arquivos

**Disciplina:** Sistemas Operacionais  
**Trabalho:** Simulador de Sistema de Arquivos com Journaling  
**Linguagem:** Java  
**Repositório GitHub:** https://github.com/SEU_USUARIO/TrabalhoSistOp

---

## Resumo

Este trabalho propõe o desenvolvimento de um simulador para compreender um sistema de arquivos. O programa implementa operações básicas de manipulação de arquivos e diretórios em Java, com suporte a **journaling** para garantir a integridade dos dados. O simulador persiste o estado em um arquivo de imagem (`filesystem.img`) e registra todas as operações em um log (`filesystem.journal`), executando em modo **Shell** interativo.

---

## Introdução

O gerenciamento eficiente de arquivos é crucial para o funcionamento dos sistemas operacionais. Para isso, entender como é montado e organizado um sistema de arquivos é a base para a compreensão dos sistemas operacionais.

### Objetivo

Desenvolver um simulador de sistema de arquivos em Java que implemente funcionalidades básicas de manipulação de arquivos e diretórios, com suporte a Journaling para garantir a integridade dos dados. O simulador permite a criação de um arquivo que simula o sistema de arquivos e realiza operações como copiar, apagar, renomear arquivos e diretórios, bem como listar o conteúdo de um diretório.

---

# Parte 1: Introdução ao Sistema de Arquivos com Journaling

## O que é um Sistema de Arquivos?

Um **sistema de arquivos** (file system) é o método utilizado pelos sistemas operacionais para organizar, armazenar, recuperar e gerenciar dados em dispositivos de armazenamento persistente (HDs, SSDs, pendrives, etc.). Ele define como os dados são nomeados, onde são gravados fisicamente e quais metadados (tamanho, permissões, datas) são associados a cada arquivo.

A importância do sistema de arquivos está em:

- **Abstração:** O usuário e as aplicações interagem com arquivos e diretórios, sem precisar conhecer detalhes do hardware.
- **Organização:** Estrutura hierárquica em árvore (diretórios e subdiretórios).
- **Persistência:** Garante que os dados permaneçam após desligamento do sistema.
- **Integridade:** Mecanismos para evitar corrupção em caso de falhas (queda de energia, travamento).

## Journaling

O **journaling** (ou registro em diário) é uma técnica usada em sistemas de arquivos modernos (como ext4, NTFS, XFS) para garantir **consistência** e **recuperação** após falhas.

### Propósito

Antes de modificar os dados reais no disco, o sistema registra a intenção da operação em um **log** (journal). Se o sistema falhar no meio de uma operação, na reinicialização o SO pode consultar o journal para:

- **Completar** operações que foram registradas mas não finalizadas, ou
- **Desfazer** (rollback) operações incompletas, restaurando um estado consistente.

### Funcionamento (Write-Ahead Logging)

1. A operação é **registrada no journal** com status `PENDING`.
2. A operação é **executada** na estrutura de dados / imagem do sistema de arquivos.
3. O estado é **persistido** em disco.
4. A entrada do journal é marcada como `COMMITTED` e, após checkpoint, pode ser removida.

### Tipos de Journaling

| Tipo | Descrição |
|------|-----------|
| **Write-ahead logging** | Registra a intenção da mudança antes de aplicá-la. Usado neste simulador. |
| **Metadata journaling** | Apenas metadados (nomes, diretórios) são registrados no log. |
| **Full data journaling** | Dados e metadados são registrados — maior segurança, menor desempenho. |
| **Log-structured** | Todas as escritas são sequenciais no log; o sistema é reconstruído a partir dele. |

---

# Parte 2: Arquitetura do Simulador

## Estrutura de Dados

O sistema de arquivos simulado é representado em memória como uma **árvore hierárquica**:

```
FileSystemNode (abstrata)
├── File        → representa um arquivo (nome + conteúdo textual)
└── Directory   → representa um diretório (nome + mapa de filhos)
```

- **`FileSystemNode`:** Classe base com nome, referência ao pai e caminho absoluto.
- **`File`:** Armazena conteúdo textual do arquivo.
- **`Directory`:** Mantém um `Map<String, FileSystemNode>` com os filhos (arquivos e subdiretórios).
- **`FileSystemSimulator`:** Gerencia a raiz (`/`), resolve caminhos e executa operações.
- **`Journal`:** Gerencia o arquivo de log de operações.

### Persistência

| Arquivo | Função |
|---------|--------|
| `filesystem.img` | Imagem persistida do sistema de arquivos (diretórios e arquivos) |
| `filesystem.journal` | Log de operações com status PENDING/COMMITTED |

Formato da imagem (`filesystem.img`):

```
# Simulador de Sistema de Arquivos - Imagem persistida
DIR:/home
DIR:/home/docs
FILE:/home/docs/nota.txt:SGVsbG8gV29ybGQ=
```

Formato do journal (`filesystem.journal`):

```
1|2026-06-05T10:30:00|CREATE_DIR|/home|COMMITTED
2|2026-06-05T10:30:05|CREATE_FILE|/home/nota.txt :: SGVsbG8=|COMMITTED
```

## Implementação do Journaling

Cada operação no simulador segue o fluxo:

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│ Log PENDING │ ──► │ Executar op  │ ──► │ Salvar .img │ ──► │ Mark COMMIT  │
└─────────────┘     └──────────────┘     └─────────────┘     └──────────────┘
```

Na **recuperação** (ao iniciar o programa):

- Se existirem entradas `PENDING` no journal, significa que uma operação foi interrompida antes do commit.
- O simulador descarta essas entradas e mantém a última imagem consistente salva em `filesystem.img`.

---

# Parte 3: Implementação em Java

## Classes Principais

### `FileSystemSimulator`

Implementa o simulador com os seguintes métodos:

| Método | Descrição |
|--------|-----------|
| `createDirectory(path)` | Cria um diretório |
| `deleteDirectory(path)` | Remove um diretório vazio |
| `renameDirectory(old, new)` | Renomeia/move um diretório |
| `createFile(path, content)` | Cria um arquivo com conteúdo |
| `deleteFile(path)` | Remove um arquivo |
| `renameFile(old, new)` | Renomeia/move um arquivo |
| `copyFile(src, dest)` | Copia um arquivo |
| `listDirectory(path)` | Lista conteúdo de um diretório |

### `File` e `Directory`

Representam os nós da árvore do sistema de arquivos. `Directory` contém filhos; `File` contém conteúdo textual.

### `Journal`

Gerencia o log de operações com métodos `logPending()`, `commit()` e `clearCommitted()`.

### `Shell`

Interface interativa (modo Shell) que recebe comandos do usuário e chama os métodos do simulador.

### `Main`

Ponto de entrada do programa. Inicializa o simulador e inicia o Shell.

## Estrutura do Projeto

```
TrabalhoSistOp/
├── README.md
├── .gitignore
└── src/
    └── filesystem/
        ├── Main.java
        ├── Shell.java
        ├── FileSystemSimulator.java
        ├── Journal.java
        ├── FileSystemNode.java
        ├── File.java
        └── Directory.java
```

---

# Parte 4: Instalação e Funcionamento

## Requisitos

- **Java JDK 11** ou superior
- Terminal / prompt de comando

## Compilação

No diretório raiz do projeto:

```bash
javac -d out src/filesystem/*.java
```

## Execução

```bash
java -cp out filesystem.Main
```

O programa iniciará o modo Shell interativo:

```
========================================
  Simulador de Sistema de Arquivos
  Modo Shell com Journaling
========================================

fs>
```

## Comandos do Shell

| Comando | Descrição | Exemplo |
|---------|-----------|---------|
| `mkdir` | Criar diretório | `mkdir /home` |
| `rmdir` | Apagar diretório vazio | `rmdir /home` |
| `renamedir` | Renomear diretório | `renamedir /home /usuario` |
| `touch` | Criar arquivo | `touch /home/nota.txt Olá mundo` |
| `rm` | Apagar arquivo | `rm /home/nota.txt` |
| `rename` | Renomear arquivo | `rename /home/a.txt /home/b.txt` |
| `cp` | Copiar arquivo | `cp /home/a.txt /home/copia.txt` |
| `ls` | Listar diretório | `ls /home` |
| `tree` | Exibir árvore completa | `tree` |
| `journal` | Exibir log de operações | `journal` |
| `help` | Ajuda | `help` |
| `exit` | Sair | `exit` |

## Exemplo de Sessão

```
fs> mkdir /documentos
Diretório criado: /documentos

fs> touch /documentos/relatorio.txt Conteudo do relatorio
Arquivo criado: /documentos/relatorio.txt

fs> ls /documentos
[FILE] relatorio.txt

fs> cp /documentos/relatorio.txt /documentos/backup.txt
Arquivo copiado: /documentos/relatorio.txt -> /documentos/backup.txt

fs> tree
DIR documentos (0 bytes)
  FILE relatorio.txt (22 bytes)
  FILE backup.txt (22 bytes)

fs> journal
--- Journal ---
[COMMITTED] CREATE_DIR | /documentos | 1
[COMMITTED] CREATE_FILE | /documentos/relatorio.txt :: ... | 2
...

fs> exit
Encerrando simulador. Estado salvo em disco.
```

## Metodologia

O simulador foi desenvolvido em Java. Ele recebe chamadas de métodos (via Shell ou diretamente no código) com os parâmetros adequados. Cada método implementa o comportamento correspondente a um comando do sistema operacional, registra a operação no journal e persiste o estado. O programa exibe o resultado na tela quando necessário.

---

## Resultados Esperados

Espera-se que o simulador forneça insights sobre o funcionamento de um sistema de arquivos. Com base nos resultados obtidos, é possível avaliar e entender como funciona esse elemento fundamental de um sistema operacional: a organização hierárquica de arquivos, a persistência em disco e o papel do journaling na garantia de integridade dos dados.

---

## Autores

- Nome do Integrante 1
- Nome do Integrante 2

> **Link do GitHub:** https://github.com/SEU_USUARIO/TrabalhoSistOp  
> *(Substitua pelo link real do repositório antes de gerar o PDF para entrega no AVA)*
