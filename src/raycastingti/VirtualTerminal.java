package raycastingti;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulador completo de Prompt de Comando (CMD) com Sistema de Arquivos Virtual em memória,
 * mais de 45 comandos interativos, suporte a cores ('color 0a'), histórico de comandos (Setas Cima/Baixo),
 * rolagem de tela (PageUp/PageDown/Scroll), autocompletar (TAB) e inicialização dos 6 Mini-Games.
 */
public class VirtualTerminal {

    public interface TerminalListener {
        void onLaunchGame(ArcadeGames.GameType gameType);
        void onExitTerminal();
    }

    private static class VNode {
        String name;
        boolean isDirectory;
        String content;
        int sizeBytes;
        String timestamp;
        VNode parent;
        final Map<String, VNode> children = new LinkedHashMap<>();

        VNode(String name, boolean isDirectory, String content, int sizeBytes, VNode parent) {
            this.name = name;
            this.isDirectory = isDirectory;
            this.content = content;
            this.sizeBytes = sizeBytes;
            this.parent = parent;
            this.timestamp = "23/10/2026  14:30";
        }
    }

    private final TerminalListener listener;
    private VNode rootDir;
    private VNode userHomeDir;
    private VNode currentDir;
    private String lastPlayerName = "Aluno";

    private final List<String> outputLines = new ArrayList<>();
    private final List<String> commandHistory = new ArrayList<>();
    private int historyCursor = -1;
    private String cmdInput = "";
    private int scrollOffset = 0; // Linhas roladas para cima

    private String windowTitle = "Administrador: Prompt de Comando - Workstation Lab-TI";
    private Color bgColor = new Color(8, 12, 18);
    private Color textColor = new Color(0, 255, 140);
    private Color headerColor = new Color(0, 210, 255);

    private int tabCycleIndex = 0;
    private String lastTabPrefix = null;

    public VirtualTerminal(TerminalListener listener) {
        this.listener = listener;
        initFileSystem("Aluno");
        printBanner();
    }

    public void updatePlayerName(String playerName) {
        String clean = (playerName == null || playerName.trim().isEmpty()) ? "Aluno" : playerName.trim();
        if (!clean.equals(lastPlayerName)) {
            lastPlayerName = clean;
            if (userHomeDir != null) {
                rootDir.children.get("users").children.remove(userHomeDir.name.toLowerCase());
                userHomeDir.name = clean;
                rootDir.children.get("users").children.put(clean.toLowerCase(), userHomeDir);
            }
        }
    }

    private void initFileSystem(String userName) {
        rootDir = new VNode("C:", true, null, 0, null);

        VNode windows = mkdir(rootDir, "Windows");
        VNode sys32 = mkdir(windows, "System32");
        addFile(sys32, "cmd.exe", "[BINARIO DO SISTEMA WINDOWS]", 289792);
        addFile(sys32, "ping.exe", "[UTILITARIO DE REDE ICMP]", 22528);
        addFile(sys32, "taskmgr.exe", "[GERENCIADOR DE TAREFAS]", 415232);
        addFile(sys32, "drivers_gpu.sys", "Driver de Video Software Raycaster v4.2 - OK", 65536);

        VNode progFiles = mkdir(rootDir, "Program Files");
        VNode raycastingFolder = mkdir(progFiles, "RayCastingTI");
        addFile(raycastingFolder, "RayCastingTI.jar", "[MOTOR RAYCASTING 3D + IA LOCAL]", 142080);
        addFile(raycastingFolder, "arquitetura_motor.txt",
                "=== ARQUITETURA DO MOTOR RAYCASTING-TI ===\n"
                + "- Algoritmo DDA (Digital Differential Analyzer) para intersecao de raios na matriz 2D.\n"
                + "- Z-Buffer 1D e Table-Buffer para renderizar bancadas sem ocultar as paredes de fundo.\n"
                + "- Sprites 2D ordenados por distancia (Painter's Algorithm).\n"
                + "- Integracao com llama.cpp (Gemma-4 GGUF) + Motor NLP Nativo.", 512);

        VNode users = mkdir(rootDir, "Users");
        userHomeDir = mkdir(users, userName);
        currentDir = userHomeDir;

        // Pasta Downloads com todos os jogos executáveis
        VNode downloads = mkdir(userHomeDir, "Downloads");
        addFile(downloads, "pacman.exe", "[EXECUTAVEL ARCADE: PAC-MAN]", 65536);
        addFile(downloads, "tetris.exe", "[EXECUTAVEL ARCADE: TETRIS]", 58368);
        addFile(downloads, "doom.exe", "[EXECUTAVEL ARCADE: DOOM 2D ARENA]", 131072);
        addFile(downloads, "dino.exe", "[EXECUTAVEL ARCADE: CHROME DINO RUNNER]", 49152);
        addFile(downloads, "snake.exe", "[EXECUTAVEL ARCADE: SNAKE]", 32768);
        addFile(downloads, "matrix.exe", "[EXECUTAVEL: SIMULADOR MATRIX]", 28672);
        addFile(downloads, "leia_me_jogos.txt",
                "=== PACOTE DE JOGOS SECRETOS DO LABORATORIO ===\n"
                + "Para jogar qualquer um dos classicos abaixo, basta digitar o nome dele aqui no CMD:\n"
                + "  - pacman.exe  (Labirinto completo com 4 fantasmas e Power Pellets)\n"
                + "  - tetris.exe  (Grade 10x18 com as 7 pecas oficiais, rotacao e Ghost Piece)\n"
                + "  - doom.exe    (Arena Shooter 2D com ondas de demonios e barra de HP)\n"
                + "  - dino.exe    (T-Rex Runner com pulo, agachamento, cactos e pterodactilos)\n"
                + "  - snake.exe   (Jogo da Cobrinha classico com macas douradas)\n"
                + "  - matrix.exe  (Protetor de tela Chuva Digital Matrix)", 640);

        // Pasta Desktop
        VNode desktop = mkdir(userHomeDir, "Desktop");
        addFile(desktop, "tarefas_lab.txt",
                "LISTA DE ATIVIDADES DA AULA DE TI:\n"
                + "[x] 1. Conversar com o Professor de TI sobre Java, POO e Hardware.\n"
                + "[x] 2. Testar comandos de rede no CMD (ipconfig, ping, netstat, tracert).\n"
                + "[ ] 3. Descobrir os 6 jogos escondidos na pasta Downloads.\n"
                + "[ ] 4. Testar o comando 'color 0a' e 'systeminfo' no terminal.", 320);
        addFile(desktop, "wifi_lab.txt",
                "REDE WI-FI DO LABORATORIO:\n"
                + "SSID: LAB_TI_5GHZ\n"
                + "Senha: EuAmoProgramarEmJava2026!\n"
                + "Gateway: 192.168.1.1", 180);

        // Pasta Documents
        VNode docs = mkdir(userHomeDir, "Documents");
        addFile(docs, "OlaMundo.java",
                "public class OlaMundo {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Ola do Laboratorio 3D de TI! Java rodando perfeitamente!\");\n"
                + "    }\n"
                + "}", 195);
        addFile(docs, "cola_comandos_cmd.txt",
                "=== RESUMO DE COMANDOS DO TERMINAL ===\n"
                + "Navegacao : dir, cd <pasta>, cd .., tree, mkdir <nome>, rmdir <nome>\n"
                + "Arquivos  : type <arq>, echo texto > arq.txt, copy, ren, del, find\n"
                + "Rede      : ipconfig, ping <host>, tracert <host>, netstat, arp -a, nslookup, nmap\n"
                + "Sistema   : systeminfo, neofetch, tasklist, taskkill, mem, chkdsk, ver, date, whoami\n"
                + "Diversao  : color 0a, calc <conta>, ai <pergunta>, cowsay <msg>, matrix, coffee, joke", 520);

        // Pasta Pictures
        VNode pics = mkdir(userHomeDir, "Pictures");
        addFile(pics, "doom_logo.ascii",
                "   =================     ===============     ===============   ========  ========\n"
                + "   \\\\ . . . . . . .\\\\   //. . . . . . .\\\\   //. . . . . . .\\\\  \\\\. . .\\\\// . . //\n"
                + "   ||. . ._____. . .|| ||. . ._____. . .|| ||. . ._____. . .|| || . . .\\/ . . .||\n"
                + "   || . .||   ||. . || || . .||   ||. . || || . .||   ||. . || ||. . . . . . . ||\n"
                + "   ||. . ||   || . .|| ||. . ||   || . .|| ||. . ||   || . .|| || . | . . . . .||\n"
                + "   || . .||___||. _-|| ||-_ .||___||. . || || . .||___||. _-|| ||-_.|\\ . . . . ||\n"
                + "   ||___________||     ||___________||     ||___________||     ||___| \\\\_______||", 610);

        // Arquivo na pasta inicial do usuário
        addFile(userHomeDir, "dicas_professor.txt",
                "Bem-vindo ao computador do Laboratorio de TI!\n"
                + "1) Digite 'cd Downloads' e depois 'dir' para ver os 6 jogos instalados.\n"
                + "2) Digite 'help' para ver as dezenas de comandos reais que voce pode testar.\n"
                + "3) Voce pode criar pastas com 'mkdir', criar arquivos com 'echo texto > teste.txt' e mudar cores com 'color'!", 340);
    }

    private VNode mkdir(VNode parent, String name) {
        VNode dir = new VNode(name, true, null, 0, parent);
        parent.children.put(name.toLowerCase(Locale.ROOT), dir);
        return dir;
    }

    private void addFile(VNode parent, String name, String content, int size) {
        VNode file = new VNode(name, false, content, size, parent);
        parent.children.put(name.toLowerCase(Locale.ROOT), file);
    }

    public void onOpen(String playerName) {
        updatePlayerName(playerName);
        scrollOffset = 0;
    }

    private void printBanner() {
        outputLines.clear();
        println("Microsoft Windows [Versão 10.0.19045.5011 - Workstation Lab-TI]");
        println("(c) Lab-TI Corporation. Todos os direitos reservados.");
        println("Digite 'help' para listar todos os comandos ou 'cd Downloads' para acessar os jogos.");
        println("");
    }

    private void println(String line) {
        if (line == null) return;
        for (String sub : line.split("\n", -1)) {
            outputLines.add(sub);
        }
        scrollOffset = 0;
    }

    public String getCurrentPathString() {
        List<String> parts = new ArrayList<>();
        VNode cur = currentDir;
        while (cur != null) {
            parts.add(0, cur.name);
            cur = cur.parent;
        }
        return String.join("\\", parts) + "\\";
    }

    // -------------------------------------------------------------------------
    // INTERPRETADOR DE COMANDOS DO TERMINAL
    // -------------------------------------------------------------------------
    public void executeCommand(String rawCmd) {
        String promptPrefix = getCurrentPathString() + "> ";
        println(promptPrefix + rawCmd);

        String trimmed = rawCmd.trim();
        if (trimmed.isEmpty()) return;

        commandHistory.add(trimmed);
        historyCursor = commandHistory.size();

        // Suporte a redirecionamento de saída: echo texto > arquivo.txt ou >> arquivo.txt
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("echo ") && trimmed.contains(">")) {
            handleEchoRedirection(trimmed);
            return;
        }

        String[] parts = trimmed.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = (parts.length > 1) ? parts[1].trim() : "";

        // Verifica se o comando é o nome de um dos 6 jogos (mesmo fora da pasta Downloads se quiser, ou dentro dela)
        if (tryLaunchGameCommand(cmd)) {
            return;
        }

        switch (cmd) {
            case "help":
            case "?":
                printHelp(arg);
                break;

            case "cls":
            case "clear":
                outputLines.clear();
                scrollOffset = 0;
                break;

            case "exit":
            case "quit":
                listener.onExitTerminal();
                break;

            case "dir":
            case "ls":
                cmdDir(arg);
                break;

            case "cd":
            case "chdir":
                cmdCd(arg);
                break;

            case "cd..":
                cmdCd("..");
                break;

            case "cd\\":
                cmdCd("\\");
                break;

            case "tree":
                cmdTree();
                break;

            case "mkdir":
            case "md":
                cmdMkdir(arg);
                break;

            case "rmdir":
            case "rd":
                cmdRmdir(arg);
                break;

            case "type":
            case "cat":
            case "more":
                cmdType(arg);
                break;

            case "echo":
                println(arg.isEmpty() ? "ECHO está ativado." : arg);
                break;

            case "touch":
            case "notepad":
            case "edit":
                cmdTouchOrEdit(arg);
                break;

            case "del":
            case "rm":
            case "erase":
                cmdDel(arg);
                break;

            case "copy":
            case "cp":
                cmdCopy(arg);
                break;

            case "ren":
            case "rename":
            case "mv":
            case "move":
                cmdRename(arg);
                break;

            case "find":
            case "grep":
                cmdFind(arg);
                break;

            case "color":
                cmdColor(arg);
                break;

            case "title":
                if (!arg.isEmpty()) {
                    windowTitle = arg;
                    println("Título da janela alterado para: " + arg);
                } else {
                    println("Uso: title <novo titulo da janela>");
                }
                break;

            case "ipconfig":
            case "ifconfig":
                cmdIpconfig(arg);
                break;

            case "ping":
                cmdPing(arg);
                break;

            case "tracert":
            case "traceroute":
                cmdTracert(arg);
                break;

            case "nslookup":
                cmdNslookup(arg);
                break;

            case "netstat":
                cmdNetstat();
                break;

            case "arp":
                cmdArp();
                break;

            case "nmap":
            case "portscan":
                cmdNmap(arg);
                break;

            case "curl":
            case "wget":
                cmdCurl(arg);
                break;

            case "ssh":
            case "telnet":
                println("Conectando a " + (arg.isEmpty() ? "192.168.1.254" : arg) + ":22... Acesso negado (Chave SSH do Professor necessária).");
                break;

            case "systeminfo":
            case "neofetch":
                cmdSystemInfo();
                break;

            case "tasklist":
            case "ps":
            case "top":
                cmdTasklist();
                break;

            case "taskkill":
            case "kill":
                cmdTaskkill(arg);
                break;

            case "ver":
            case "winver":
            case "uname":
                println("Microsoft Windows [Versão 10.0.19045.5011] - Lab-TI Edition (Kernel x64)");
                break;

            case "whoami":
                println("lab-ti\\" + lastPlayerName.toLowerCase(Locale.ROOT));
                break;

            case "hostname":
                println("LAB-TI-WORKSTATION-04");
                break;

            case "date":
            case "time":
            case "now": {
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                println("Data e Hora atual do sistema: " + now);
                break;
            }

            case "vol":
            case "chkdsk":
            case "sfc":
            case "defrag":
                println("O volume na unidade C é LAB_TI_NVME (Número de Série: 4F8A-99C1)");
                println("Verificando sistema de arquivos NTFS em C:... 0 setores defeituosos. Integridade: 100% OK.");
                break;

            case "mem":
            case "free": {
                Runtime rt = Runtime.getRuntime();
                long totalMB = rt.totalMemory() / (1024 * 1024);
                long freeMB = rt.freeMemory() / (1024 * 1024);
                long usedMB = totalMB - freeMB;
                long maxMB = rt.maxMemory() / (1024 * 1024);
                println("Memória JVM Alocada : " + totalMB + " MB (Em uso: " + usedMB + " MB | Livre: " + freeMB + " MB)");
                println("Memória Máxima JVM  : " + maxMB + " MB | Núcleos Lógicos CPU: " + rt.availableProcessors());
                break;
            }

            case "set":
            case "env":
            case "path":
                println("COMPUTERNAME=LAB-TI-WORKSTATION-04");
                println("USERNAME=" + lastPlayerName);
                println("OS=Windows_NT");
                println("JAVA_VERSION=" + System.getProperty("java.version"));
                println("AI_ENGINE=" + ProfessorAI.getActiveBackendName());
                println("PATH=C:\\Windows\\System32;C:\\Program Files\\Java\\bin;C:\\Program Files\\RayCastingTI");
                break;

            case "java":
            case "javac":
                cmdJava(cmd, arg);
                break;

            case "python":
            case "py":
                println("Python 3.12.4 (Lab-TI Virtual Env) - Dica: use 'calc <expressao>' para cálculos rápidos!");
                break;

            case "calc":
            case "math":
                cmdCalc(arg);
                break;

            case "bin":
            case "hex":
                cmdBaseConvert(cmd, arg);
                break;

            case "ai-status":
                println("=== DIAGNÓSTICO DO MOTOR DE IA LOCAL ===");
                println("  Modelo Selecionado : " + ProfessorAI.getSelectedModelName());
                println("  Backend Ativo      : " + ProfessorAI.getActiveBackendName());
                println("  Status do Servidor : " + ProfessorAI.getEngineStatusText());
                println("  Dica: Use 'ai <sua pergunta>' para perguntar ao Professor direto pelo CMD ou 'ai-stop' para liberar RAM!");
                break;

            case "ai-stop":
                ProfessorAI.selectModelByIndex(ProfessorAI.getAvailableModels().size() - 1);
                println("Servidor llama-server.exe encerrado! Modo 'IA Leve Nativa (0 MB RAM)' ativado.");
                break;

            case "ai":
            case "ask":
            case "professor":
                if (arg.isEmpty()) {
                    println("Uso: ai <sua pergunta de TI>   (Ex: ai O que é um ponteiro em C?)");
                } else {
                    println("[Consultando Professor de TI (" + ProfessorAI.getShortModelBadge() + ")...]");
                    ProfessorAI.ask(lastPlayerName, arg, new ProfessorAI.AICallback() {
                        @Override
                        public void onResponse(String response) {
                            println("PROFESSOR: " + response);
                        }

                        @Override
                        public void onError(String error) {
                            println("ERRO IA: " + error);
                        }
                    });
                }
                break;

            case "history":
                for (int i = 0; i < commandHistory.size(); i++) {
                    println(String.format("  %2d  %s", i + 1, commandHistory.get(i)));
                }
                break;

            case "cowsay":
                cmdCowsay(arg.isEmpty() ? "Bons estudos de TI, " + lastPlayerName + "!" : arg);
                break;

            case "coffee":
            case "cafe":
                println("      ( (");
                println("       ) )");
                println("    ........");
                println("    |      |]   Café fresquinho compilado com sucesso!");
                println("    \\      /    Energia de programação +100%!");
                println("     `----'");
                break;

            case "joke":
            case "piada":
                println("Por que o programador foi demitido da fábrica de sucos? Porque ele não conseguia dar 'concentrate'!");
                println("E por que o SQL entrou no bar? Para dar um JOIN em duas tabelas!");
                break;

            case "hack":
            case "sudo":
                println("[ACESSO ROOT] Iniciando protocolo de segurança do Lab-TI...");
                println("[OK] Firewall verificado | [OK] Criptografia AES-256 ativa | Bom trabalho, White Hat!");
                break;

            case "shutdown":
            case "reboot":
                println("Encerrando sessão remota da Workstation... Voltando ao laboratório 3D.");
                listener.onExitTerminal();
                break;

            default:
                println("'" + cmd + "' não é reconhecido como um comando interno ou externo.");
                println("Digite 'help' para ver a lista completa de comandos ou 'dir' para ver os arquivos.");
                break;
        }
    }

    private boolean tryLaunchGameCommand(String cmd) {
        String clean = cmd.replace(".exe", "");
        boolean inDownloads = currentDir.name.equalsIgnoreCase("Downloads")
                || currentDir.children.containsKey(clean + ".exe");

        // Permite iniciar direto ou dentro da pasta Downloads
        switch (clean) {
            case "pacman":
                navigateToDownloadsIfPossible();
                listener.onLaunchGame(ArcadeGames.GameType.PACMAN);
                return true;
            case "tetris":
                navigateToDownloadsIfPossible();
                listener.onLaunchGame(ArcadeGames.GameType.TETRIS);
                return true;
            case "doom":
                navigateToDownloadsIfPossible();
                listener.onLaunchGame(ArcadeGames.GameType.DOOM2D);
                return true;
            case "dino":
                navigateToDownloadsIfPossible();
                listener.onLaunchGame(ArcadeGames.GameType.DINO);
                return true;
            case "snake":
                navigateToDownloadsIfPossible();
                listener.onLaunchGame(ArcadeGames.GameType.SNAKE);
                return true;
            case "matrix":
                if (inDownloads || cmd.endsWith(".exe")) {
                    navigateToDownloadsIfPossible();
                    listener.onLaunchGame(ArcadeGames.GameType.MATRIX);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void navigateToDownloadsIfPossible() {
        VNode dl = userHomeDir.children.get("downloads");
        if (dl != null) currentDir = dl;
    }

    // -------------------------------------------------------------------------
    // IMPLEMENTAÇÃO DETALHADA DOS COMANDOS
    // -------------------------------------------------------------------------
    private void printHelp(String topic) {
        println("================ GUIA DE COMANDOS DO TERMINAL LAB-TI ================");
        println(" [ARQUIVOS E PASTAS]");
        println("   dir / ls           Lista arquivos e pastas do diretório atual");
        println("   cd <pasta>         Entra em uma pasta (use 'cd ..' p/ voltar ou 'cd \\' p/ raiz)");
        println("   tree               Exibe a árvore gráfica de diretórios e arquivos");
        println("   type <arquivo>     Exibe o conteúdo de um arquivo de texto (.txt, .java, .ascii)");
        println("   mkdir / rmdir      Cria ('mkdir nova') ou remove ('rmdir nova') uma pasta");
        println("   echo txt > arq.txt Cria um arquivo novo com o texto informado");
        println("   copy / ren / del   Copia, renomeia ou apaga arquivos virtuais");
        println(" [JOGOS EXECUTÁVEIS (Pasta Downloads)]");
        println("   pacman.exe         Pac-Man Arcade com labirinto completo e 4 fantasmas");
        println("   tetris.exe         Tetris oficial 10x18 com rotação e Ghost Piece");
        println("   doom.exe           DOOM 2D Arena Shooter com ondas de demônios");
        println("   dino.exe           Chrome Dino Runner com pulo, agachamento e pterodáctilos");
        println("   snake.exe          Jogo da Cobrinha clássico | matrix.exe (Chuva Matrix)");
        println(" [REDE, SISTEMA E IA LOCAL]");
        println("   ipconfig / ping    Configuração IP, teste de latência ICMP, tracert, netstat, arp, nmap");
        println("   systeminfo / mem   Resumo completo de hardware, CPU, RAM, tasklist e taskkill");
        println("   ai <pergunta>      Conversa com a IA do Professor direto no CMD (ou 'ai-status' / 'ai-stop')");
        println("   color <codigo>     Muda cor do CMD (Ex: color 0a, color 07, color 1f, color 0e, color ?)");
        println("   calc <expressao>   Calculadora (Ex: calc 1024*8, bin 42, hex 255, cowsay, coffee, cls, exit)");
        println("   Dica: Use Setas CIMA/BAIXO p/ histórico, TAB p/ autocompletar e Roda do Mouse p/ rolar!");
    }

    private void cmdDir(String arg) {
        VNode target = arg.isEmpty() ? currentDir : resolvePath(arg);
        if (target == null) {
            println("O sistema não pode encontrar o caminho especificado: " + arg);
            return;
        }
        if (!target.isDirectory) {
            println(String.format("%s    %10d  %s", target.timestamp, target.sizeBytes, target.name));
            return;
        }

        println(" O volume na unidade C é LAB_TI_NVME");
        println(" Diretório de " + getPathForNode(target));
        println("");

        int fileCount = 0;
        int dirCount = 0;
        long totalBytes = 0;

        if (target.parent != null) {
            println("23/10/2026  14:30    <DIR>          .");
            println("23/10/2026  14:30    <DIR>          ..");
            dirCount += 2;
        }

        for (VNode child : target.children.values()) {
            if (child.isDirectory) {
                println(String.format("%s    <DIR>          %s", child.timestamp, child.name));
                dirCount++;
            } else {
                String tag = child.name.toLowerCase(Locale.ROOT).endsWith(".exe") ? "<EXE>" : "     ";
                println(String.format("%s    %s %8d %s", child.timestamp, tag, child.sizeBytes, child.name));
                fileCount++;
                totalBytes += child.sizeBytes;
            }
        }
        println(String.format("              %2d arquivo(s)     %10d bytes", fileCount, totalBytes));
        println(String.format("              %2d pasta(s)   256.842.113.024 bytes disponíveis", dirCount));
    }

    private void cmdCd(String arg) {
        if (arg.isEmpty()) {
            println(getCurrentPathString());
            return;
        }
        String clean = arg.replace("\"", "").trim();
        if (clean.equals("~")) {
            currentDir = userHomeDir;
            return;
        }
        VNode target = resolvePath(clean);
        if (target == null) {
            println("O sistema não pode encontrar o caminho especificado: " + clean);
        } else if (!target.isDirectory) {
            println("O nome do diretório é inválido (é um arquivo): " + target.name);
        } else {
            currentDir = target;
        }
    }

    private void cmdTree() {
        println("Listagem de caminhos de pasta para o volume LAB_TI_NVME");
        println(getCurrentPathString());
        printTreeRecursive(currentDir, "");
    }

    private void printTreeRecursive(VNode dir, String indent) {
        List<VNode> list = new ArrayList<>(dir.children.values());
        for (int i = 0; i < list.size(); i++) {
            VNode node = list.get(i);
            boolean last = (i == list.size() - 1);
            String branch = last ? "└───" : "├───";
            println(indent + branch + node.name + (node.isDirectory ? "\\" : ""));
            if (node.isDirectory) {
                printTreeRecursive(node, indent + (last ? "    " : "│   "));
            }
        }
    }

    private void cmdMkdir(String arg) {
        if (arg.isEmpty()) {
            println("A sintaxe do comando está incorreta. Uso: mkdir <nome_da_pasta>");
            return;
        }
        String name = arg.replace("\"", "").trim();
        String key = name.toLowerCase(Locale.ROOT);
        if (currentDir.children.containsKey(key)) {
            println("Já existe uma subpasta ou arquivo com o nome '" + name + "'.");
            return;
        }
        mkdir(currentDir, name);
        println("Diretório '" + name + "' criado com sucesso.");
    }

    private void cmdRmdir(String arg) {
        if (arg.isEmpty()) {
            println("Uso: rmdir <nome_da_pasta>");
            return;
        }
        String key = arg.replace("\"", "").trim().toLowerCase(Locale.ROOT);
        VNode node = currentDir.children.get(key);
        if (node == null || !node.isDirectory) {
            println("O sistema não pode encontrar o diretório especificado.");
        } else if (!node.children.isEmpty()) {
            println("A pasta não está vazia. Apague os arquivos internos primeiro com 'del'.");
        } else {
            currentDir.children.remove(key);
            println("Diretório removido com sucesso.");
        }
    }

    private void cmdType(String arg) {
        if (arg.isEmpty()) {
            println("Uso: type <nome_do_arquivo>");
            return;
        }
        VNode node = resolvePath(arg.replace("\"", "").trim());
        if (node == null) {
            println("O sistema não pode encontrar o arquivo especificado: " + arg);
        } else if (node.isDirectory) {
            println("Acesso negado: '" + node.name + "' é um diretório.");
        } else {
            println(node.content != null ? node.content : "");
        }
    }

    private void handleEchoRedirection(String raw) {
        boolean append = raw.contains(">>");
        String[] split = raw.split(append ? ">>" : ">", 2);
        String textPart = split[0].trim().substring(4).trim();
        String fileName = (split.length > 1) ? split[1].trim().replace("\"", "") : "";
        if (fileName.isEmpty()) {
            println("Erro de sintaxe no redirecionamento. Ex: echo Ola Mundo > nota.txt");
            return;
        }
        String key = fileName.toLowerCase(Locale.ROOT);
        VNode existing = currentDir.children.get(key);
        if (existing != null && existing.isDirectory) {
            println("Não é possível sobrescrever um diretório.");
            return;
        }
        if (existing != null && append) {
            existing.content = (existing.content == null || existing.content.isEmpty())
                    ? textPart
                    : existing.content + "\n" + textPart;
            existing.sizeBytes = existing.content.length();
            println("Texto adicionado ao arquivo '" + existing.name + "'.");
        } else {
            addFile(currentDir, fileName, textPart, textPart.length());
            println("Arquivo '" + fileName + "' gravado com sucesso (" + textPart.length() + " bytes).");
        }
    }

    private void cmdTouchOrEdit(String arg) {
        if (arg.isEmpty()) {
            println("Uso: touch <arquivo.txt> [conteudo opcional]");
            return;
        }
        String[] sp = arg.split("\\s+", 2);
        String name = sp[0];
        String content = (sp.length > 1) ? sp[1] : "Arquivo criado pelo terminal do Lab-TI.";
        addFile(currentDir, name, content, content.length());
        println("Arquivo '" + name + "' salvo em " + getCurrentPathString());
    }

    private void cmdDel(String arg) {
        if (arg.isEmpty()) {
            println("Uso: del <nome_do_arquivo>");
            return;
        }
        String key = arg.replace("\"", "").trim().toLowerCase(Locale.ROOT);
        VNode node = currentDir.children.get(key);
        if (node == null) {
            println("Não foi possível encontrar " + getCurrentPathString() + arg);
        } else if (node.isDirectory) {
            println("'" + node.name + "' é uma pasta. Use 'rmdir' para remover pastas.");
        } else if (node.name.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            println("Acesso negado: Os executáveis do laboratório são protegidos pelo Administrador.");
        } else {
            currentDir.children.remove(key);
            println("Arquivo '" + node.name + "' excluído.");
        }
    }

    private void cmdCopy(String arg) {
        String[] sp = arg.split("\\s+");
        if (sp.length < 2) {
            println("Uso: copy <arquivo_origem> <arquivo_destino>");
            return;
        }
        VNode src = resolvePath(sp[0]);
        if (src == null || src.isDirectory) {
            println("Arquivo de origem não encontrado.");
            return;
        }
        addFile(currentDir, sp[1], src.content, src.sizeBytes);
        println("        1 arquivo(s) copiado(s).");
    }

    private void cmdRename(String arg) {
        String[] sp = arg.split("\\s+");
        if (sp.length < 2) {
            println("Uso: ren <nome_atual> <novo_nome>");
            return;
        }
        String oldKey = sp[0].toLowerCase(Locale.ROOT);
        VNode node = currentDir.children.get(oldKey);
        if (node == null) {
            println("O sistema não pode encontrar o arquivo especificado.");
            return;
        }
        currentDir.children.remove(oldKey);
        node.name = sp[1];
        currentDir.children.put(sp[1].toLowerCase(Locale.ROOT), node);
        println("Arquivo renomeado para '" + sp[1] + "'.");
    }

    private void cmdFind(String arg) {
        if (arg.isEmpty()) {
            println("Uso: find <palavra> <arquivo>");
            return;
        }
        String[] sp = arg.replace("\"", "").split("\\s+", 2);
        if (sp.length < 2) {
            println("Uso: find <palavra> <arquivo>");
            return;
        }
        String term = sp[0].toLowerCase(Locale.ROOT);
        VNode file = resolvePath(sp[1]);
        if (file == null || file.isDirectory) {
            println("Arquivo não encontrado: " + sp[1]);
            return;
        }
        println("---------- " + file.name.toUpperCase(Locale.ROOT));
        int matches = 0;
        for (String line : file.content.split("\n")) {
            if (line.toLowerCase(Locale.ROOT).contains(term)) {
                println("  " + line);
                matches++;
            }
        }
        if (matches == 0) println("  (Nenhuma ocorrência encontrada)");
    }

    private void cmdColor(String arg) {
        String clean = arg.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty() || clean.equals("?")) {
            println("Define as cores de fundo e texto do console (Ex: color 0a):");
            println("  color 0a  -> Verde Matrix no fundo Preto (Padrão Lab-TI)");
            println("  color 07  -> Branco clássico no fundo Preto");
            println("  color 0b  -> Ciano Neon no fundo Preto");
            println("  color 0e  -> Amarelo Âmbar no fundo Preto");
            println("  color 0c  -> Vermelho Hacker no fundo Preto");
            println("  color 1f  -> Branco no fundo Azul Clássico (Estilo BIOS/BSOD)");
            println("  color 70  -> Preto no fundo Cinza Claro (Modo Claro)");
            return;
        }
        switch (clean) {
            case "0a":
            case "a":
                bgColor = new Color(8, 12, 18);
                textColor = new Color(0, 255, 140);
                break;
            case "07":
            case "7":
                bgColor = Color.BLACK;
                textColor = new Color(225, 225, 225);
                break;
            case "0b":
            case "b":
                bgColor = new Color(6, 12, 22);
                textColor = new Color(0, 230, 255);
                break;
            case "0e":
            case "e":
                bgColor = new Color(14, 12, 6);
                textColor = new Color(255, 215, 50);
                break;
            case "0c":
            case "c":
                bgColor = new Color(16, 6, 8);
                textColor = new Color(255, 75, 75);
                break;
            case "1f":
                bgColor = new Color(0, 35, 145);
                textColor = Color.WHITE;
                break;
            case "70":
                bgColor = new Color(220, 225, 230);
                textColor = new Color(20, 25, 30);
                break;
            default:
                println("Código de cor inválido. Digite 'color ?' para ver as combinações disponíveis.");
                return;
        }
        println("Esquema de cores atualizado para '" + clean + "'.");
    }

    private void cmdIpconfig(String arg) {
        println("Configuração de IP do Windows - Laboratório de TI");
        println("");
        println("Adaptador Ethernet Gigabit Lab-TI:");
        println("   Sufixo DNS específico de conexão. . . . . . : labti.local");
        println("   Endereço IPv6 de link local . . . . . . . . : fe80::84a1:c9ff:fe2b:4201%12");
        println("   Endereço IPv4. . . . . . . .  . . . . . . . : 192.168.1.42");
        println("   Máscara de Sub-rede . . . . . . . . . . . . : 255.255.255.0");
        println("   Gateway Padrão. . . . . . . . . . . . . . . : 192.168.1.1");
        if (arg.toLowerCase(Locale.ROOT).contains("/all")) {
            println("   Endereço Físico (MAC) . . . . . . . . . . . : 00-1A-2B-3C-4D-5E");
            println("   DHCP Habilitado . . . . . . . . . . . . . . : Sim");
            println("   Servidores DNS. . . . . . . . . . . . . . . : 8.8.8.8, 1.1.1.1");
        }
    }

    private void cmdPing(String arg) {
        String target = arg.isEmpty() ? "192.168.1.1" : arg;
        println("Disparando " + target + " com 32 bytes de dados:");
        for (int i = 1; i <= 4; i++) {
            int ms = target.contains("192.168") || target.contains("127.0.0.1") || target.equals("localhost") ? 1 : (11 + (i % 3));
            println("Resposta de " + target + ": bytes=32 tempo=" + ms + "ms TTL=64");
        }
        println("Estatísticas do Ping para " + target + ":");
        println("    Pacotes: Enviados = 4, Recebidos = 4, Perdidos = 0 (0% de perda)");
    }

    private void cmdTracert(String arg) {
        String target = arg.isEmpty() ? "google.com" : arg;
        println("Rastreando a rota para " + target + " com no máximo 30 saltos:");
        println("  1    <1 ms    <1 ms    <1 ms  switch-lab-ti.local [192.168.1.1]");
        println("  2     3 ms     2 ms     3 ms  gateway-fibra-academica.net [10.20.0.1]");
        println("  3     9 ms     8 ms     9 ms  backbone-br-sp.rede.br [200.160.0.14]");
        println("  4    12 ms    11 ms    12 ms  " + target + " [142.250.79.14]");
        println("Rastreamento concluído.");
    }

    private void cmdNslookup(String arg) {
        String domain = arg.isEmpty() ? "raycastingti.edu.br" : arg;
        println("Servidor:  dns.google");
        println("Address:  8.8.8.8");
        println("Não é resposta de autorização:");
        println("Nome:    " + domain);
        println("Address: 142.250.218.110");
    }

    private void cmdNetstat() {
        println("Conexões ativas no Laboratório:");
        println("  Proto  Endereço Local         Endereço Externo       Estado");
        println("  TCP    127.0.0.1:18089        0.0.0.0:0              LISTENING   (Motor IA Local)");
        println("  TCP    192.168.1.42:51422     192.168.1.200:443      ESTABLISHED (Rack Servidor Lab)");
        println("  TCP    192.168.1.42:51428     142.250.79.14:443      TIME_WAIT");
        println("  UDP    0.0.0.0:5353           *:*");
    }

    private void cmdArp() {
        println("Interface: 192.168.1.42 --- 0xc");
        println("  Endereço IP           Endereço Físico       Tipo");
        println("  192.168.1.1           00-50-56-c0-00-01     dinâmico  (Roteador Lab)");
        println("  192.168.1.10          00-1b-63-84-45-e6     dinâmico  (PC do Professor)");
        println("  192.168.1.200         ac-de-48-00-11-22     estático  (Mainframe Rack Wall4)");
    }

    private void cmdNmap(String arg) {
        String host = arg.isEmpty() ? "192.168.1.200" : arg;
        println("Starting Nmap 7.95 Educational Lab Scanner at " + host + "...");
        println("PORT      STATE SERVICE       VERSION");
        println("22/tcp    open  ssh           OpenSSH 9.6 (Linux Kernel 6.8)");
        println("80/tcp    open  http          Apache httpd 2.4.58 (Intranet Escola TI)");
        println("3306/tcp  open  mysql         MySQL 8.4.0-LabDB");
        println("18089/tcp open  llama-server  llama.cpp Local AI HTTP Server");
        println("Nmap done: 1 IP address (1 host up) scanned in 0.42 seconds.");
    }

    private void cmdCurl(String arg) {
        String url = arg.isEmpty() ? "http://localhost:18089/health" : arg;
        println("GET " + url + " -> HTTP/1.1 200 OK");
        println("{\"status\":\"ok\", \"server\":\"Workstation Lab-TI\", \"ai\":\"" + ProfessorAI.getShortModelBadge() + "\"}");
    }

    private void cmdSystemInfo() {
        Runtime rt = Runtime.getRuntime();
        long maxMB = rt.maxMemory() / (1024 * 1024);
        println("   .---.   =========================================================");
        println("  /     \\  ESTAÇÃO DE TRABALHO: LAB-TI-WORKSTATION-04");
        println("  | () () | Sistema Operacional : Windows 10 Pro Virtual (64-bit)");
        println("   \\  ^  /  Usuário Conectado   : " + lastPlayerName);
        println("    |||||   Java Runtime (JVM)  : OpenJDK " + System.getProperty("java.version"));
        println("    '---'   Processador (CPU)   : " + rt.availableProcessors() + " Núcleos Lógicos (x86_64 AVX2)");
        println("            Memória Alocada JVM : " + maxMB + " MB");
        println("            Motor Gráfico       : Software Raycasting DDA + Z-Buffer 60 FPS");
        println("            Modelo de IA Ativo  : " + ProfessorAI.getSelectedModelName());
        println("            Status do Motor IA  : " + ProfessorAI.getEngineStatusText());
        println("           =========================================================");
    }

    private void cmdTasklist() {
        boolean ggufRunning = ProfessorAI.isGGUFServerReady();
        println("Nome da Imagem                 PID Nome da Sessão       Uso de Memória");
        println("========================= ======== ================ ==================");
        println("System                           4 Services                  14.220 K");
        println("explorer.exe                  1840 Console                   68.400 K");
        println("java.exe (RayCastingTI)       4096 Console                  145.900 K");
        if (ggufRunning) {
            println("llama-server.exe (GGUF AI)    8812 Console                4.620.000 K");
        }
        println("cmd.exe                       9104 Console                    8.192 K");
    }

    private void cmdTaskkill(String arg) {
        String lower = arg.toLowerCase(Locale.ROOT);
        if (lower.contains("llama")) {
            ProfessorAI.selectModelByIndex(ProfessorAI.getAvailableModels().size() - 1);
            println("SUCESSO: O processo 'llama-server.exe' foi finalizado e a memória RAM foi liberada!");
        } else if (lower.contains("java") || lower.contains("doom")) {
            println("Acesso negado: Se você matar o processo do jogo, o universo 3D do laboratório entrará em colapso!");
        } else {
            println("Uso: taskkill /im llama-server.exe  (Encerra o servidor GGUF em segundo plano)");
        }
    }

    private void cmdJava(String cmd, String arg) {
        if (arg.equals("-version") || arg.equals("--version")) {
            println("openjdk version \"" + System.getProperty("java.version") + "\" 64-Bit Server VM (Lab-TI Build)");
            return;
        }
        if (arg.isEmpty()) {
            println("Uso: javac OlaMundo.java   ou   java OlaMundo");
            return;
        }
        if (cmd.equals("javac")) {
            println("Compilando '" + arg + "'... [0 erros, 0 avisos] -> Bytecode .class gerado com sucesso!");
        } else {
            String cleanName = arg.replace(".java", "").replace(".class", "");
            VNode srcFile = currentDir.children.get((cleanName + ".java").toLowerCase(Locale.ROOT));
            if (srcFile != null && srcFile.content != null && srcFile.content.contains("println(")) {
                Matcher m = Pattern.compile("println\\(\"(.*?)\"\\)").matcher(srcFile.content);
                while (m.find()) {
                    println(m.group(1));
                }
            } else {
                println("Executando classe " + cleanName + ": Olá do Laboratório 3D de TI! Java rodando perfeitamente!");
            }
        }
    }

    private void cmdCalc(String arg) {
        if (arg.isEmpty()) {
            println("Uso: calc <expressao>  (Ex: calc 1024 * 64  |  calc 2 ^ 10)");
            return;
        }
        Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/x^%])\\s*(-?\\d+(?:\\.\\d+)?)").matcher(arg);
        if (m.find()) {
            double a = Double.parseDouble(m.group(1));
            String op = m.group(2);
            double b = Double.parseDouble(m.group(3));
            double res;
            switch (op) {
                case "+": res = a + b; break;
                case "-": res = a - b; break;
                case "*":
                case "x": res = a * b; break;
                case "/":
                    if (b == 0) { println("Erro: Divisão por zero!"); return; }
                    res = a / b; break;
                case "%": res = a % b; break;
                case "^": res = Math.pow(a, b); break;
                default: return;
            }
            println("Resultado = " + ((res == Math.floor(res)) ? String.valueOf((long) res) : String.valueOf(res)));
        } else {
            println("Expressão não reconhecida. Exemplo: calc 256 * 4");
        }
    }

    private void cmdBaseConvert(String mode, String arg) {
        try {
            int val = Integer.parseInt(arg.trim());
            println("Decimal: " + val + "  |  Binário: 0b" + Integer.toBinaryString(val) + "  |  Hexadecimal: 0x" + Integer.toHexString(val).toUpperCase());
        } catch (Exception e) {
            println("Uso: " + mode + " <numero_inteiro>  (Ex: " + mode + " 42)");
        }
    }

    private void cmdCowsay(String msg) {
        String border = "-".repeat(Math.min(55, msg.length() + 2));
        println(" " + border);
        println("< " + msg + " >");
        println(" " + border);
        println("        \\   ^__^");
        println("         \\  (oo)\\_______");
        println("            (__)\\       )\\/\\");
        println("                ||----w |");
        println("                ||     ||");
    }

    private VNode resolvePath(String rawPath) {
        String p = rawPath.replace("/", "\\").trim();
        if (p.isEmpty() || p.equals(".")) return currentDir;
        if (p.equals("\\") || p.equalsIgnoreCase("c:\\") || p.equalsIgnoreCase("c:")) return rootDir;

        VNode cursor = currentDir;
        if (p.toLowerCase(Locale.ROOT).startsWith("c:\\")) {
            cursor = rootDir;
            p = p.substring(3);
        } else if (p.startsWith("\\")) {
            cursor = rootDir;
            p = p.substring(1);
        }

        String[] segments = p.split("\\\\+");
        for (String seg : segments) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) {
                if (cursor.parent != null) cursor = cursor.parent;
            } else {
                if (!cursor.isDirectory) return null;
                VNode next = cursor.children.get(seg.toLowerCase(Locale.ROOT));
                if (next == null) return null;
                cursor = next;
            }
        }
        return cursor;
    }

    private String getPathForNode(VNode node) {
        List<String> parts = new ArrayList<>();
        VNode cur = node;
        while (cur != null) {
            parts.add(0, cur.name);
            cur = cur.parent;
        }
        return String.join("\\", parts) + "\\";
    }

    // -------------------------------------------------------------------------
    // CONTROLES DE TECLADO E AUTOCOMPLETAR (TAB / HISTÓRICO / ROLAGEM)
    // -------------------------------------------------------------------------
    public void handleKeyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_ESCAPE) {
            listener.onExitTerminal();
            return;
        }

        if (code == KeyEvent.VK_TAB) {
            handleTabCompletion();
            e.consume();
            return;
        } else {
            lastTabPrefix = null;
        }

        if (code == KeyEvent.VK_ENTER) {
            String toRun = cmdInput;
            cmdInput = "";
            executeCommand(toRun);
        } else if (code == KeyEvent.VK_BACK_SPACE) {
            if (cmdInput.length() > 0) {
                cmdInput = cmdInput.substring(0, cmdInput.length() - 1);
            }
        } else if (code == KeyEvent.VK_UP) {
            if (!commandHistory.isEmpty() && historyCursor > 0) {
                historyCursor--;
                cmdInput = commandHistory.get(historyCursor);
            }
        } else if (code == KeyEvent.VK_DOWN) {
            if (historyCursor < commandHistory.size() - 1) {
                historyCursor++;
                cmdInput = commandHistory.get(historyCursor);
            } else {
                historyCursor = commandHistory.size();
                cmdInput = "";
            }
        } else if (code == KeyEvent.VK_PAGE_UP) {
            scrollOffset = Math.min(Math.max(0, outputLines.size() - 5), scrollOffset + 5);
        } else if (code == KeyEvent.VK_PAGE_DOWN) {
            scrollOffset = Math.max(0, scrollOffset - 5);
        } else {
            char c = e.getKeyChar();
            if (cmdInput.length() < 95 && !Character.isISOControl(c) && c != KeyEvent.CHAR_UNDEFINED) {
                cmdInput += c;
            }
        }
    }

    public void handleMouseWheel(int wheelRotation) {
        if (wheelRotation < 0) {
            scrollOffset = Math.min(Math.max(0, outputLines.size() - 5), scrollOffset + 3);
        } else {
            scrollOffset = Math.max(0, scrollOffset - 3);
        }
    }

    private void handleTabCompletion() {
        if (lastTabPrefix == null) {
            lastTabPrefix = cmdInput.trim();
            tabCycleIndex = 0;
        }

        String prefix = lastTabPrefix;
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();

        if (lowerPrefix.contains(" ")) {
            int lastSpace = prefix.lastIndexOf(' ');
            String cmdPart = prefix.substring(0, lastSpace + 1);
            String filePrefix = prefix.substring(lastSpace + 1).toLowerCase(Locale.ROOT);
            for (VNode child : currentDir.children.values()) {
                if (child.name.toLowerCase(Locale.ROOT).startsWith(filePrefix)) {
                    candidates.add(cmdPart + child.name);
                }
            }
        } else {
            // Prioriza executáveis e arquivos da pasta atual + comandos principais
            for (VNode child : currentDir.children.values()) {
                if (child.name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                    candidates.add(child.isDirectory ? ("cd " + child.name) : child.name);
                }
            }
            String[] cmds = {
                "help", "dir", "cd Downloads", "cd Documents", "cd Desktop", "tree",
                "pacman.exe", "tetris.exe", "doom.exe", "dino.exe", "snake.exe", "matrix.exe",
                "systeminfo", "ipconfig", "ping", "netstat", "nmap", "tasklist", "color 0a", "cls", "exit"
            };
            for (String c : cmds) {
                if (c.toLowerCase(Locale.ROOT).startsWith(lowerPrefix) && !candidates.contains(c)) {
                    candidates.add(c);
                }
            }
        }

        if (!candidates.isEmpty()) {
            cmdInput = candidates.get(tabCycleIndex % candidates.size());
            tabCycleIndex++;
        }
    }

    // -------------------------------------------------------------------------
    // RENDERIZAÇÃO DO TERMINAL CMD
    // -------------------------------------------------------------------------
    public void render(Graphics2D g2d, int screenWidth, int screenHeight) {
        int cmdHeight = screenHeight - 52;

        // Fundo configurável pelo comando 'color'
        g2d.setColor(bgColor);
        g2d.fillRect(0, 0, screenWidth, cmdHeight);

        // Barra de título estilo janela CMD
        g2d.setColor(headerColor);
        g2d.fillRect(0, 0, screenWidth, 28);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        g2d.drawString(" [C:\\_]  " + windowTitle, 10, 19);

        // Sub-barra de atalhos rápidos
        g2d.setColor(new Color(20, 30, 45));
        g2d.fillRect(0, 28, screenWidth, 24);
        g2d.setColor(new Color(175, 205, 235));
        g2d.setFont(new Font("Courier New", Font.PLAIN, 12));
        g2d.drawString("Comandos: 'help' | 'dir' | 'cd Downloads' | 'tree' | 'systeminfo' | 'color ?' | [TAB] Completar | [ESC] Sair", 14, 44);

        // Linhas de saída com suporte a rolagem
        g2d.setFont(new Font("Courier New", Font.PLAIN, 14));
        int startY = 72;
        int lineHeight = 19;
        int maxVisibleLines = Math.max(5, (cmdHeight - startY - 34) / lineHeight);

        int endIdx = Math.max(0, outputLines.size() - scrollOffset);
        int startIdx = Math.max(0, endIdx - maxVisibleLines);

        int y = startY;
        g2d.setColor(textColor);
        for (int i = startIdx; i < endIdx; i++) {
            g2d.drawString(outputLines.get(i), 18, y);
            y += lineHeight;
        }

        // Indicador de rolagem caso o usuário tenha subido o histórico
        if (scrollOffset > 0) {
            g2d.setColor(Color.YELLOW);
            g2d.drawString("▲ Rolando histórico (-" + scrollOffset + " linhas) - Pressione PageDown ou digite para descer", screenWidth - 560, cmdHeight - 8);
        }

        // Linha de comando ativa
        String cursor = ((System.currentTimeMillis() / 400) % 2 == 0) ? "█" : "_";
        g2d.setColor(bgColor.getRed() > 180 ? Color.BLACK : Color.WHITE);
        g2d.setFont(new Font("Courier New", Font.BOLD, 14));
        g2d.drawString(getCurrentPathString() + "> " + cmdInput + cursor, 18, y);
    }
}
