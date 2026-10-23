package raycastingti;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor de Inteligência Artificial Local do Professor Diego (TI).
 *
 * Recursos:
 * - Descoberta dinâmica de qualquer modelo '.gguf' colocado na pasta 'gguf/' do projeto.
 * - Streaming SSE em tempo real ('"stream": true') para que o Professor Diego escreva a explicação
 *   palavra por palavra na tela sem nunca dar 'request timed out'.
 * - Gerenciamento rigoroso de processo do 'llama-server.exe' com Watchdog de PID pai.
 */
public class ProfessorAI {

    public interface AICallback {
        default void onPartialResponse(String partialResponse) {}
        void onResponse(String response);
        void onError(String error);
    }

    public static final String NATIVE_LIGHT_MODEL = "IA Leve Nativa (0 MB RAM - Instantânea)";

    private static final int LLAMA_PORT = 18089;
    private static volatile Process llamaServerProcess = null;
    private static volatile Process watchdogProcess = null;
    private static volatile boolean ggufServerReady = false;
    private static volatile int initGeneration = 0;

    private static final List<String> availableModels = new ArrayList<>();
    private static final Map<String, File> modelFileMap = new HashMap<>();
    private static volatile int selectedModelIndex = 0;

    private static volatile String activeBackendName = "Inicializando...";
    private static volatile String shortModelBadge = "IA LOCAL";
    private static volatile String engineStatusText = "Verificando modelos na pasta gguf/...";

    private static class KnowledgeIntent {
        final String id;
        final String[] keywords;
        final String[] phrases;
        final String[] responses;
        final String followUp;

        KnowledgeIntent(String id, String[] keywords, String[] phrases, String[] responses, String followUp) {
            this.id = id;
            this.keywords = keywords;
            this.phrases = phrases;
            this.responses = responses;
            this.followUp = followUp;
        }
    }

    private static final List<KnowledgeIntent> KNOWLEDGE_BASE = new ArrayList<>();
    private static final Map<String, String> SLANG_MAP = new HashMap<>();
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "o", "a", "os", "as", "um", "uma", "de", "do", "da", "dos", "das",
            "em", "no", "na", "nos", "nas", "por", "para", "com", "sem", "que",
            "e", "ou", "se", "mas", "como", "qual", "quais", "me", "te", "se",
            "eu", "voce", "ele", "ela", "nos", "eles", "meu", "minha", "seu", "sua",
            "esse", "essa", "isso", "este", "esta", "isto", "ao", "aos", "pelo", "pela"
    ));

    private static volatile String lastIntentId = null;
    private static final Map<String, Integer> intentUsageCount = new HashMap<>();
    private static final Random RANDOM = new Random();

    static {
        initSlangMap();
        initKnowledgeBase();
        // Hook de segurança para encerrar o llama-server ao fechar a JVM
        Runtime.getRuntime().addShutdownHook(new Thread(ProfessorAI::shutdownServer, "Llama-ShutdownHook"));
        refreshAvailableModels();
    }

    public static String getActiveBackendName() {
        return activeBackendName;
    }

    public static String getShortModelBadge() {
        return shortModelBadge;
    }

    public static String getEngineStatusText() {
        return engineStatusText;
    }

    public static boolean isGGUFServerReady() {
        return ggufServerReady;
    }

    /**
     * Escaneia a pasta 'gguf/' do projeto em busca de todos os arquivos '.gguf' disponíveis
     * e adiciona também a opção de IA Leve Nativa (0 MB RAM).
     */
    public static synchronized List<String> refreshAvailableModels() {
        String previouslySelected = getSelectedModelName();
        availableModels.clear();
        modelFileMap.clear();

        String[] baseDirs = { ".", ".." };
        Set<String> seenNames = new HashSet<>();

        for (String base : baseDirs) {
            File ggufDir = new File(base, "gguf");
            if (ggufDir.exists() && ggufDir.isDirectory()) {
                File[] files = ggufDir.listFiles((d, name) -> name.toLowerCase().endsWith(".gguf"));
                if (files != null) {
                    Arrays.sort(files, Comparator.comparing(File::getName));
                    for (File f : files) {
                        if (f.isFile() && seenNames.add(f.getName())) {
                            double sizeGB = f.length() / (1024.0 * 1024.0 * 1024.0);
                            String label = String.format(Locale.US, "%s (%.1f GB)", f.getName(), sizeGB);
                            availableModels.add(label);
                            modelFileMap.put(label, f);
                        }
                    }
                }
            }
        }

        // Sempre inclui a opção leve nativa (para PCs mais fracos ou uso instantâneo sem consumo de RAM)
        availableModels.add(NATIVE_LIGHT_MODEL);

        if (previouslySelected != null && availableModels.contains(previouslySelected)) {
            selectedModelIndex = availableModels.indexOf(previouslySelected);
        } else {
            selectedModelIndex = 0;
        }
        return new ArrayList<>(availableModels);
    }

    public static synchronized List<String> getAvailableModels() {
        if (availableModels.isEmpty()) {
            refreshAvailableModels();
        }
        return new ArrayList<>(availableModels);
    }

    public static synchronized int getSelectedModelIndex() {
        return selectedModelIndex;
    }

    public static synchronized String getSelectedModelName() {
        if (availableModels.isEmpty()) return NATIVE_LIGHT_MODEL;
        if (selectedModelIndex < 0 || selectedModelIndex >= availableModels.size()) {
            selectedModelIndex = 0;
        }
        return availableModels.get(selectedModelIndex);
    }

    /**
     * Troca o modelo ativo (qualquer .gguf da pasta gguf/ ou a IA Leve Nativa) e reinicia o motor.
     */
    public static synchronized void selectModelByIndex(int index) {
        if (availableModels.isEmpty()) refreshAvailableModels();
        if (index < 0 || index >= availableModels.size()) return;
        selectedModelIndex = index;
        restartEngineWithSelectedModel();
    }

    public static synchronized void cycleModel(int direction) {
        if (availableModels.isEmpty()) refreshAvailableModels();
        int count = availableModels.size();
        if (count <= 1) return;
        selectedModelIndex = (selectedModelIndex + direction + count) % count;
        restartEngineWithSelectedModel();
    }

    public static synchronized void initLocalEngineAsync() {
        if (initGeneration == 0) {
            restartEngineWithSelectedModel();
        }
    }

    /**
     * Encerra imediatamente qualquer instância do llama-server.exe (filha ou órfã) liberando 100% da RAM/CPU.
     */
    public static synchronized void shutdownServer() {
        ggufServerReady = false;
        try {
            if (watchdogProcess != null && watchdogProcess.isAlive()) {
                watchdogProcess.destroyForcibly();
                watchdogProcess = null;
            }
        } catch (Exception ignored) {
        }

        try {
            if (llamaServerProcess != null) {
                llamaServerProcess.descendants().forEach(ph -> {
                    try { ph.destroyForcibly(); } catch (Exception ignored) {}
                });
                llamaServerProcess.destroyForcibly();
                llamaServerProcess = null;
            }
        } catch (Exception ignored) {
        }

        // Garante que nenhum processo llama-server.exe órfão continue aberto no Windows
        killOrphanLlamaServerProcesses();
    }

    private static void killOrphanLlamaServerProcesses() {
        try {
            ProcessHandle.allProcesses().forEach(ph -> {
                try {
                    Optional<String> cmd = ph.info().command();
                    if (cmd.isPresent() && cmd.get().toLowerCase().contains("llama-server")) {
                        ph.destroyForcibly();
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static synchronized void restartEngineWithSelectedModel() {
        final int myGen = ++initGeneration;
        final String chosenLabel = getSelectedModelName();
        final File chosenGGUF = modelFileMap.get(chosenLabel);

        Thread initThread = new Thread(() -> {
            // 1. Sempre fecha qualquer llama-server anterior antes de trocar ou iniciar
            shutdownServer();

            if (myGen != initGeneration) return;

            // 2. Se o usuário escolheu a IA Leve Nativa ou não há arquivo .gguf
            if (chosenLabel.equals(NATIVE_LIGHT_MODEL) || chosenGGUF == null || !chosenGGUF.exists()) {
                activeBackendName = "IA Leve Nativa (0 MB RAM)";
                shortModelBadge = "IA LEVE (0 MB)";
                engineStatusText = "Modo Leve Ativo (Zero uso de RAM/CPU)";
                ggufServerReady = false;
                return;
            }

            File serverExe = findLlamaServerExecutable();
            if (serverExe == null) {
                activeBackendName = "IA Leve Nativa (llama-server ausente)";
                shortModelBadge = "IA LEVE";
                engineStatusText = "Binário llama-server não encontrado; usando IA Leve";
                ggufServerReady = false;
                return;
            }

            try {
                String modelFileName = chosenGGUF.getName();
                activeBackendName = "llama.cpp + " + modelFileName;
                shortModelBadge = truncateBadge(modelFileName);
                engineStatusText = "Carregando " + modelFileName + " na memória...";

                // Usa os núcleos da CPU deixando 2 threads livres para o jogo e o Windows
                int totalCores = Runtime.getRuntime().availableProcessors();
                int safeThreads = Math.max(2, Math.min(8, totalCores > 2 ? totalCores - 2 : totalCores));

                ProcessBuilder pb = new ProcessBuilder(
                        serverExe.getAbsolutePath(),
                        "-m", chosenGGUF.getAbsolutePath(),
                        "--port", String.valueOf(LLAMA_PORT),
                        "-c", "1536",
                        "-t", String.valueOf(safeThreads),
                        "-tb", String.valueOf(safeThreads),
                        "--reasoning", "off",
                        "--no-warmup"
                );
                pb.directory(serverExe.getParentFile());
                pb.redirectErrorStream(true);
                Process started = pb.start();
                llamaServerProcess = started;

                // Inicia um Watchdog leve do SO: se o processo Java morrer (mesmo via botão Stop do NetBeans),
                // o Watchdog mata o llama-server.exe imediatamente!
                startParentWatchdog(started.pid());

                Thread logDrainer = new Thread(() -> {
                    try (var in = started.getInputStream()) {
                        byte[] buf = new byte[1024];
                        while (in.read(buf) != -1) {
                            if (myGen != initGeneration) break;
                        }
                    } catch (Exception ignored) {
                    }
                }, "LlamaServer-LogDrainer");
                logDrainer.setDaemon(true);
                logDrainer.start();

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
                for (int attempt = 0; attempt < 95; attempt++) {
                    if (myGen != initGeneration) return;
                    if (!started.isAlive()) break;

                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create("http://127.0.0.1:" + LLAMA_PORT + "/health"))
                                .timeout(Duration.ofSeconds(1))
                                .GET()
                                .build();
                        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                        if (res.statusCode() == 200 && res.body().contains("\"ok\"")) {
                            if (myGen == initGeneration) {
                                ggufServerReady = true;
                                engineStatusText = "Online (" + modelFileName + " | " + safeThreads + " threads)";
                            }
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                    Thread.sleep(800);
                }

                if (myGen == initGeneration && !ggufServerReady) {
                    engineStatusText = "Falha ao carregar GGUF; usando IA Leve Nativa";
                    shutdownServer();
                }
            } catch (Exception e) {
                if (myGen == initGeneration) {
                    activeBackendName = "IA Leve Nativa (Offline)";
                    shortModelBadge = "IA LEVE";
                    engineStatusText = "Erro no GGUF; usando IA Leve Nativa";
                }
            }
        }, "RayCastingTI-LlamaEngineManager");
        initThread.setDaemon(true);
        initThread.start();
    }

    /**
     * Cria um processo vigia (Watchdog) oculto no Windows que monitora o PID do Java.
     * Caso o jogo seja fechado pelo 'Stop' do NetBeans ou Gerenciador de Tarefas,
     * o llama-server.exe é encerrado automaticamente em até 2 segundos.
     */
    private static void startParentWatchdog(long llamaPid) {
        try {
            long javaPid = ProcessHandle.current().pid();
            String psCommand = String.format(
                    "while (Get-Process -Id %d -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 2 }; "
                    + "Stop-Process -Id %d -Force -ErrorAction SilentlyContinue",
                    javaPid, llamaPid
            );
            ProcessBuilder wpb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psCommand
            );
            wpb.redirectErrorStream(true);
            watchdogProcess = wpb.start();
        } catch (Exception ignored) {
        }
    }

    private static String truncateBadge(String fileName) {
        String clean = fileName.replaceAll("(?i)\\.gguf$", "");
        if (clean.length() > 16) {
            clean = clean.substring(0, 16);
        }
        return clean.toUpperCase();
    }

    private static File findLlamaServerExecutable() {
        String[] baseDirs = { ".", ".." };
        for (String base : baseDirs) {
            File direct = new File(base, "llama-b11669-bin-win-cpu-x64/llama-server.exe");
            if (direct.exists() && direct.isFile()) return direct;

            File root = new File(base);
            File[] children = root.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && child.getName().toLowerCase().startsWith("llama")) {
                        File exe = new File(child, "llama-server.exe");
                        if (exe.exists() && exe.isFile()) return exe;
                    }
                }
            }
        }
        return null;
    }

    public static void ask(String playerName, String message, AICallback callback) {
        initLocalEngineAsync();

        new Thread(() -> {
            try {
                String safeName = (playerName == null || playerName.trim().isEmpty()) ? "Aluno" : playerName.trim();
                String cleanMsg = (message == null) ? "" : message.trim();

                if (cleanMsg.isEmpty()) {
                    callback.onResponse("Olá, " + safeName + "! Eu sou o Professor Diego. Pode perguntar qualquer coisa sobre TI, programação, redes, arquitetura de computadores ou desenvolvimento de jogos que eu te explico nos mínimos detalhes!");
                    return;
                }

                // Se um modelo GGUF está selecionado
                if (!getSelectedModelName().equals(NATIVE_LIGHT_MODEL)) {
                    if (!ggufServerReady && llamaServerProcess != null && llamaServerProcess.isAlive()) {
                        for (int i = 0; i < 35 && !ggufServerReady; i++) {
                            Thread.sleep(600);
                        }
                    }

                    if (ggufServerReady || isLocalPortOpen(LLAMA_PORT, 150)) {
                        String ggufReply = queryProjectLlamaServer(safeName, cleanMsg, callback);
                        if (ggufReply != null && !ggufReply.isBlank()) {
                            callback.onResponse(ggufReply);
                            return;
                        }
                    }
                }

                // Motor NLP Local Nativo (0 MB RAM / resposta instantânea)
                Thread.sleep(200);
                String response = generateEmbeddedLocalResponse(safeName, cleanMsg);
                callback.onResponse(response);

            } catch (Exception e) {
                callback.onError("Ops, ocorreu um erro no motor local: " + e.getMessage());
            }
        }, "ProfessorAI-Worker").start();
    }

    /**
     * Consulta o servidor llama-server usando Streaming SSE ("stream": true).
     * Assim os tokens aparecem ao vivo na tela conforme são gerados na CPU e nunca ocorre 'request timed out'!
     */
    private static String queryProjectLlamaServer(String playerName, String message, AICallback callback) {
        StringBuilder accumulated = new StringBuilder();
        try {
            String systemPrompt = "Você é o Professor Diego, professor de um curso técnico de TI dentro de um laboratório 3D. "
                    + "O nome do seu aluno é " + playerName + ". "
                    + "Responda sempre em português do Brasil, sendo explicativo na medida certa e indo direto ao ponto do assunto perguntado, sem enrolação e sem introduções teóricas longas. "
                    + "Quando o aluno perguntar 'como fazer' ou 'como montar' algo (por exemplo, como montar um RJ45), ensine diretamente o passo a passo prático da montagem ou execução, sem ficar explicando todo o conceito teórico antes. "
                    + "Use no máximo 1 a 2 parágrafos curtos ou uma lista passo a passo objetiva.";

            String payload = "{"
                    + "\"messages\":["
                    + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
                    + "{\"role\":\"user\",\"content\":\"" + escapeJson(message) + "\"}"
                    + "],"
                    + "\"temperature\":0.5,"
                    + "\"max_tokens\":320,"
                    + "\"stream\":true"
                    + "}";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + LLAMA_PORT + "/v1/chat/completions"))
                    .timeout(Duration.ofMinutes(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                ggufServerReady = true;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;
                        if (trimmed.equals("data: [DONE]") || trimmed.equals("[DONE]")) {
                            break;
                        }
                        if (trimmed.startsWith("data:")) {
                            String jsonChunk = trimmed.substring(5).trim();
                            String tokenDelta = extractJsonContentField(jsonChunk, false);
                            if (tokenDelta != null && !tokenDelta.isEmpty()) {
                                accumulated.append(tokenDelta);
                                String liveCleaned = cleanReplyText(accumulated.toString());
                                if (!liveCleaned.isEmpty()) {
                                    callback.onPartialResponse(liveCleaned + " ▌");
                                }
                            }
                        } else if (trimmed.startsWith("{")) {
                            // Fallback caso o servidor retorne JSON único sem SSE
                            String fullContent = extractJsonContentField(trimmed, true);
                            if (fullContent != null && !fullContent.isBlank()) {
                                accumulated.append(fullContent);
                            }
                        }
                    }
                }
                String finalCleaned = cleanReplyText(accumulated.toString());
                if (!finalCleaned.isBlank()) {
                    return finalCleaned;
                }
            }
        } catch (Exception e) {
            String partial = cleanReplyText(accumulated.toString());
            if (partial.length() > 25) {
                return partial;
            }
            System.out.println("[AVISO IA LOCAL] Falha na requisição GGUF, usando resposta rápida: " + e.getMessage());
        }
        return null;
    }

    private static boolean isLocalPortOpen(int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractJsonContentField(String json, boolean trimOutput) {
        int choicesIdx = json.indexOf("\"choices\"");
        int searchFrom = (choicesIdx != -1) ? choicesIdx : 0;
        String target = "\"content\"";
        int idx = json.indexOf(target, searchFrom);
        while (idx != -1) {
            if (idx == 0 || json.charAt(idx - 1) != '_') {
                int colon = json.indexOf(':', idx + target.length());
                if (colon != -1) {
                    int pos = colon + 1;
                    while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
                    if (pos < json.length() && json.charAt(pos) == '"') {
                        return unescapeJsonString(json, pos + 1, trimOutput);
                    }
                }
            }
            idx = json.indexOf(target, idx + target.length());
        }
        return null;
    }

    private static String unescapeJsonString(String json, int startIdx, boolean trimOutput) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == 'n') sb.append('\n');
                else if (next == 'r') sb.append("");
                else if (next == 't') sb.append("  ");
                else if (next == '"' || next == '\\' || next == '/') sb.append(next);
                else if (next == 'u' && i + 5 < json.length()) {
                    try {
                        int code = Integer.parseInt(json.substring(i + 2, i + 6), 16);
                        sb.append((char) code);
                        i += 4;
                    } catch (Exception ignored) {
                    }
                }
                i++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return trimOutput ? sb.toString().trim() : sb.toString();
    }

    private static String cleanReplyText(String text) {
        return text.replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("(?s)<\\|thought\\|>.*?<\\|thought\\|>", "")
                .replaceAll("(?s)<\\|thought>.*?<thought\\|>", "")
                .replaceAll("\\*+", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static synchronized String generateEmbeddedLocalResponse(String playerName, String rawMessage) {
        String normalized = normalizeText(rawMessage);
        List<String> tokens = tokenize(normalized);

        String mathOrBinary = tryMathOrBinaryConversion(playerName, normalized);
        if (mathOrBinary != null) {
            return mathOrBinary;
        }

        if (lastIntentId != null && isFollowUpRequest(normalized)) {
            for (KnowledgeIntent intent : KNOWLEDGE_BASE) {
                if (intent.id.equals(lastIntentId) && intent.followUp != null) {
                    return formatResponse(intent.followUp, playerName);
                }
            }
        }

        KnowledgeIntent bestIntent = null;
        double bestScore = 0.0;

        for (KnowledgeIntent intent : KNOWLEDGE_BASE) {
            double score = scoreIntent(intent, normalized, tokens);
            if (score > bestScore) {
                bestScore = score;
                bestIntent = intent;
            }
        }

        if (bestIntent != null && bestScore >= 1.5) {
            lastIntentId = bestIntent.id;
            int count = intentUsageCount.getOrDefault(bestIntent.id, 0);
            intentUsageCount.put(bestIntent.id, count + 1);
            String chosenTemplate = bestIntent.responses[count % bestIntent.responses.length];
            return formatResponse(chosenTemplate, playerName);
        }

        return generateContextualFallback(playerName, normalized);
    }

    private static double scoreIntent(KnowledgeIntent intent, String normalizedText, List<String> userTokens) {
        double score = 0.0;
        for (String phrase : intent.phrases) {
            if (normalizedText.contains(phrase)) {
                score += 6.0 + (phrase.length() * 0.1);
            }
        }
        for (String kw : intent.keywords) {
            for (String token : userTokens) {
                if (token.equals(kw)) {
                    score += 3.5;
                } else if (token.length() >= 4 && kw.length() >= 4 && (token.startsWith(kw) || kw.startsWith(token))) {
                    score += 2.2;
                } else if (token.length() >= 5 && kw.length() >= 5 && levenshteinDistance(token, kw) <= 1) {
                    score += 1.8;
                }
            }
        }
        return score;
    }

    private static boolean isFollowUpRequest(String normalized) {
        String[] followUpTriggers = {
                "explica melhor", "explique melhor", "me da um exemplo", "de um exemplo",
                "exemplo", "como assim", "fale mais", "conta mais", "nao entendi",
                "aprofundar", "mais detalhes", "e na pratica", "como funciona isso"
        };
        for (String trigger : followUpTriggers) {
            if (normalized.contains(trigger) || normalized.equals("por que") || normalized.equals("porque")) {
                return true;
            }
        }
        return false;
    }

    private static String tryMathOrBinaryConversion(String playerName, String normalized) {
        if (normalized.contains("binario") || normalized.contains("hexadecimal")) {
            Matcher m = Pattern.compile("\\b(\\d{1,9})\\b").matcher(normalized);
            if (m.find()) {
                try {
                    int num = Integer.parseInt(m.group(1));
                    if (normalized.contains("hexadecimal")) {
                        return "Excelente exercício de sistemas de numeração, " + playerName + "! Aqui é o Professor Diego explicando: na base hexadecimal (base 16, que usa dígitos de 0 a 9 e letras de A até F), o número decimal "
                                + num + " é representado como 0x" + Integer.toHexString(num).toUpperCase()
                                + ". Já em binário (base 2), cada dígito hexadecimal equivale a exatamente 4 bits (um nibble), resultando em "
                                + Integer.toBinaryString(num) + "!";
                    }
                    return "Ótimo exercício prático, " + playerName + "! Vamos converter juntos: dividindo sucessivamente por 2 e pegando os restos das divisões, o número decimal "
                            + num + " em binário (base 2) é " + Integer.toBinaryString(num)
                            + ". E se agruparmos esses bits de 4 em 4, obtemos sua representação em hexadecimal (base 16): 0x"
                            + Integer.toHexString(num).toUpperCase() + "!";
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Matcher mathMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/x^])\\s*(\\d+(?:\\.\\d+)?)").matcher(normalized);
        if (mathMatcher.find()) {
            try {
                double a = Double.parseDouble(mathMatcher.group(1));
                String op = mathMatcher.group(2);
                double b = Double.parseDouble(mathMatcher.group(3));
                double result;
                switch (op) {
                    case "+": result = a + b; break;
                    case "-": result = a - b; break;
                    case "*":
                    case "x": result = a * b; break;
                    case "/":
                        if (b == 0) return "Cuidado, " + playerName + "! Como o Professor Diego sempre avisa nas aulas de arquitetura: tentar dividir por zero gera uma interrupção de hardware na ULA (Unidade Lógica e Aritmética) do processador e lança uma ArithmeticException no Java!";
                        result = a / b;
                        break;
                    case "^": result = Math.pow(a, b); break;
                    default: return null;
                }
                String formatted = (result == Math.floor(result) && !Double.isInfinite(result))
                        ? String.valueOf((long) result)
                        : String.format(Locale.US, "%.2f", result);
                return "Calculando passo a passo na nossa Unidade Lógica e Aritmética (ULA): a expressão " + mathMatcher.group(1) + " " + op + " "
                        + mathMatcher.group(3) + " resulta em " + formatted + ", " + playerName + "! No processador, essas operações matemáticas são feitas através de portas lógicas (AND, OR, XOR) somando registradores em nanossegundos.";
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String generateContextualFallback(String playerName, String normalized) {
        if (normalized.endsWith("?") || normalized.startsWith("como") || normalized.startsWith("o que") || normalized.startsWith("qual")) {
            String[] smartFallbacks = {
                    "Excelente pergunta, " + playerName + "! Aqui é o Professor Diego e eu adoro quando os alunos têm essa curiosidade técnica. Na Tecnologia da Informação, todo sistema é construído em camadas: desde os transistores físicos da CPU e a memória RAM, passando pelo Sistema Operacional e pelas redes TCP/IP, até chegar no código de alto nível (como Java e Python). Me diga qual dessas áreas você quer que eu destrinche passo a passo para você agora!",
                    "Muito bom questionamento, " + playerName + "! Como seu Professor Diego de TI, gosto de explicar tanto a teoria quanto a prática. A gente pode analisar esse tema pelo ponto de vista da Engenharia de Software (algoritmos, estruturas de dados, orientação a objetos) ou pela Infraestrutura e Hardware (processadores, GPUs, servidores e redes). Sobre qual tópico específico você quer uma aula completa?"
            };
            return smartFallbacks[RANDOM.nextInt(smartFallbacks.length)];
        }

        String[] conversationalFallbacks = {
                "Perfeito, " + playerName + "! Eu sou o Professor Diego e estou aqui no laboratório para te dar aulas completas sobre qualquer área da computação. Além de conversar comigo, você pode se aproximar das bancadas e pressionar [E] ou Clique Direito para usar nossos computadores com Terminal CMD completo e 6 jogos clássicos na pasta Downloads!",
                "Muito bem observado, " + playerName + "! Aqui no laboratório do Professor Diego a prática anda junto com a teoria: experimente abrir os PCs da bancada, testar comandos de rede como 'ipconfig', 'ping', 'netstat', 'nmap', criar arquivos com 'echo' ou me pedir uma explicação detalhada sobre Java, Python, Inteligência Artificial, Hardware ou sobre como este motor 3D Raycasting funciona!"
        };
        return conversationalFallbacks[RANDOM.nextInt(conversationalFallbacks.length)];
    }

    private static String formatResponse(String template, String playerName) {
        return template.replace("{name}", playerName);
    }

    private static String normalizeText(String input) {
        String lower = input.toLowerCase(Locale.ROOT).trim();
        String deaccented = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        deaccented = deaccented.replace("c#", "csharp").replace("c++", "cpp");
        return deaccented;
    }

    private static List<String> tokenize(String normalized) {
        String cleaned = normalized.replaceAll("[^a-z0-9#+\\-*/ ]", " ");
        String[] rawTokens = cleaned.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String t : rawTokens) {
            if (t.isEmpty()) continue;
            String mapped = SLANG_MAP.getOrDefault(t, t);
            for (String sub : mapped.split("\\s+")) {
                if (!sub.isEmpty() && !STOP_WORDS.contains(sub)) {
                    result.add(sub);
                }
            }
        }
        return result;
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static void initSlangMap() {
        SLANG_MAP.put("vc", "voce");
        SLANG_MAP.put("vcs", "voces");
        SLANG_MAP.put("pq", "porque");
        SLANG_MAP.put("oq", "o que");
        SLANG_MAP.put("eh", "e");
        SLANG_MAP.put("tb", "tambem");
        SLANG_MAP.put("tbm", "tambem");
        SLANG_MAP.put("blz", "beleza");
        SLANG_MAP.put("obg", "obrigado");
        SLANG_MAP.put("vlw", "valeu");
        SLANG_MAP.put("flw", "falou");
        SLANG_MAP.put("prof", "professor");
        SLANG_MAP.put("pc", "computador");
        SLANG_MAP.put("pcs", "computador");
        SLANG_MAP.put("ti", "tecnologia informacao");
        SLANG_MAP.put("ia", "inteligencia artificial");
        SLANG_MAP.put("ai", "inteligencia artificial");
        SLANG_MAP.put("bd", "banco dados");
        SLANG_MAP.put("db", "banco dados");
        SLANG_MAP.put("so", "sistema operacional");
        SLANG_MAP.put("os", "sistema operacional");
        SLANG_MAP.put("poo", "orientacao objetos");
        SLANG_MAP.put("oop", "orientacao objetos");
        SLANG_MAP.put("js", "javascript");
        SLANG_MAP.put("ts", "typescript");
        SLANG_MAP.put("py", "python");
    }

    private static void initKnowledgeBase() {
        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "greeting",
                new String[]{"oi", "ola", "opa", "eae", "salve", "bom", "dia", "tarde", "noite", "tudo", "bem", "beleza"},
                new String[]{"tudo bem", "bom dia", "boa tarde", "boa noite", "e ai", "ola professor", "oi professor"},
                new String[]{
                        "Olá, {name}! Aqui é o Professor Diego. Tudo pronto aqui no laboratório de TI! Pode mandar sua dúvida sobre redes, cabeamento RJ45, hardware, Java, Python ou sobre os computadores da bancada que eu te explico de forma prática e direto ao ponto.",
                        "E aí, {name}! Bem-vindo à aula do Professor Diego. O que você quer aprender a montar, configurar ou programar hoje?"
                },
                "Pode perguntar sobre montagem de cabo RJ45, comandos de rede, programação em Java/Python ou funcionamento do motor 3D, {name}!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "who_are_you",
                new String[]{"quem", "professor", "diego", "nome", "inteligencia", "artificial", "local", "offline", "api", "gemma", "gguf", "llama"},
                new String[]{"quem e voce", "qual seu nome", "voce e uma ia", "ia local", "sem internet", "sem api", "professor diego"},
                new String[]{
                        "Eu sou o Professor Diego, {name}, seu instrutor de TI aqui do laboratório! Eu rodo 100% localmente na sua máquina: posso usar tanto os modelos .gguf da pasta 'gguf/' (via llama-server) quanto o motor leve nativo em Java puro, sem precisar de internet ou API externa.",
                        "Meu nome é Diego — sou o Professor Diego deste curso técnico de TI, {name}! Estou aqui para te ensinar na prática desde infraestrutura e redes até programação e arquitetura de computadores."
                },
                "Dica prática, {name}: no menu de Opções você pode escolher qualquer arquivo .gguf colocado na pasta 'gguf/' ou alternar para a IA Leve Nativa se quiser usar zero de RAM!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "rj45_cabo",
                new String[]{"rj45", "crimpar", "crimpagem", "cabo", "rede", "utp", "cat5e", "cat6", "t568a", "t568b", "alicate", "conector", "montar", "fios", "pares"},
                new String[]{"como montar rj45", "montar rj45", "crimpar rj45", "como crimpar", "cabo de rede", "ordem das cores", "padrao t568a", "padrao t568b"},
                new String[]{
                        "Vamos direto ao passo a passo prático para montar (crimpar) um cabo RJ45, {name}:\n"
                        + "1. Decape cerca de 2 cm da capa externa do cabo UTP com o alicate de crimpagem, cuidando para não cortar os fios internos.\n"
                        + "2. Destrance os 4 pares e alinhe os 8 fios na ordem do padrão escolhido — o mais usado no mercado é o T568B (da esquerda para a direita): 1-Branco/Laranja, 2-Laranja, 3-Branco/Verde, 4-Azul, 5-Branco/Azul, 6-Verde, 7-Branco/Marrom, 8-Marrom.\n"
                        + "3. Estique bem os fios lado a lado e apare as pontas com a lâmina do alicate para ficarem retas (cerca de 1,2 cm), garantindo que a capa do cabo entre no conector.\n"
                        + "4. Com a trava do conector RJ45 voltada para baixo, insira os fios até encostarem no fundo do conector e aperte firme no alicate de crimpagem até travar os pinos dourados!",
                        "Para montar o RJ45 no padrão T568A (usado em órgãos públicos ou ponta de cabo crossover), {name}, o processo é o mesmo, mudando apenas a ordem dos pares verde e laranja:\n"
                        + "1-Branco/Verde, 2-Verde, 3-Branco/Laranja, 4-Azul, 5-Branco/Azul, 6-Laranja, 7-Branco/Marrom, 8-Marrom.\n"
                        + "Depois de alinhar, corte reto deixando 1,2 cm, empurre até o final do RJ45 com o clipe para baixo, crimpe com o alicate e valide os 8 fios no testador de cabos!"
                },
                "Se as duas pontas usarem o mesmo padrão (T568B nas duas), temos um Cabo Direto (PC para Switch/Roteador). Se uma ponta for T568A e a outra T568B, temos um Cabo Crossover!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "raycasting_engine",
                new String[]{"raycasting", "raycast", "doom", "wolfenstein", "motor", "engine", "3d", "jogo", "renderizacao", "dda", "zbuffer", "parede", "grafico"},
                new String[]{"como esse jogo funciona", "como esse jogo 3d funciona", "como funciona o jogo", "como foi feito", "falso 3d", "motor grafico"},
                new String[]{
                        "Este jogo funciona com Raycasting (algoritmo DDA), {name}: o mapa do laboratório é uma grade 2D onde 0 é piso, 1 é parede e 2 é bancada. A cada quadro, disparamos um raio por coluna da tela a partir da sua posição até bater na parede. Pela distância perpendicular do raio, calculamos a altura da fatia vertical na tela: perto fica grande, longe fica pequeno, gerando o efeito 3D em tempo real!",
                        "Na prática, {name}, nosso motor desenha direto num array de pixels do Java Swing: primeiro faz o Floor Casting do chão, depois lança os raios para desenhar as paredes do fundo e as bancadas na frente (sem esconder a parede atrás das mesas), e por último desenha os sprites usando um Z-Buffer para controlar a profundidade."
                },
                "Usamos a distância perpendicular ao plano da câmera em vez da distância direta para evitar a distorção olho-de-peixe (fisheye) nas paredes, {name}!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "lab_secrets",
                new String[]{"computador", "cmd", "terminal", "easter", "egg", "segredo", "pacman", "tetris", "dino", "snake", "matrix", "jogar", "bancada", "downloads"},
                new String[]{"como usar o pc", "o que fazer no jogo", "jogos secretos", "como abrir o cmd", "computadores do lab"},
                new String[]{
                        "Para usar os computadores do laboratório, {name}, chegue perto de uma bancada e aperte [E] ou Clique Direito. No CMD que abrir, digite 'cd Downloads' e depois 'dir' para ver e rodar 6 jogos: 'pacman.exe', 'tetris.exe', 'doom.exe', 'dino.exe', 'snake.exe' e 'matrix.exe'! Você também pode testar comandos como 'ipconfig', 'ping', 'nmap', 'tree' e 'systeminfo'.",
                        "Encoste em qualquer PC da bancada e aperte [E], {name}! O terminal tem mais de 45 comandos funcionais. Use TAB para autocompletar nomes, Setas Cima/Baixo para o histórico de comandos e entre na pasta 'Downloads' ('cd Downloads') para jogar os arcades instalados."
                },
                "Você também pode entrar em 'cd Documents' no CMD e digitar 'type notas_aula_ti.txt' ou simular compilação com 'javac Main.java', {name}!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "java_lang",
                new String[]{"java", "jvm", "jdk", "jre", "swing", "awt", "bytecode", "garbage", "collector", "netbeans"},
                new String[]{"o que e java", "como funciona o java", "java swing", "aprender java"},
                new String[]{
                        "No Java, {name}, você escreve o código no arquivo '.java' e compila com 'javac' gerando o Bytecode ('.class'). Quem executa esse Bytecode é a JVM (Java Virtual Machine), permitindo que o mesmo programa rode em Windows, Linux ou Mac sem precisar reescrever o código, além de gerenciar a memória automaticamente com o Garbage Collector.",
                        "Aqui no projeto RayCastingTI usamos Java puro com AWT/Swing ('JFrame' e 'JPanel'), rodando um Game Loop em uma Thread a 60 FPS e desenhando cada pixel em um 'BufferedImage' em memória, {name}!"
                },
                "Resumo rápido das siglas, {name}: JVM executa o bytecode, JRE é o ambiente para rodar programas Java, e JDK é o kit completo com compilador para programar!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "oop",
                new String[]{"orientacao", "objetos", "classe", "objeto", "heranca", "polimorfismo", "encapsulamento", "abstracao", "interface"},
                new String[]{"orientacao a objetos", "o que e poo", "pilares da poo", "classe e objeto"},
                new String[]{
                        "Na Programação Orientada a Objetos (POO), {name}, a 'Classe' é o molde (define atributos e métodos) e o 'Objeto' é a instância criada na memória com 'new'. Os 4 pilares práticos são:\n"
                        + "1. Abstração: modelar só o que importa do objeto.\n"
                        + "2. Encapsulamento: proteger variáveis com 'private' e expor métodos seguros.\n"
                        + "3. Herança ('extends'): reaproveitar código de uma classe base.\n"
                        + "4. Polimorfismo: usar a mesma interface para comportamentos diferentes."
                },
                "Aqui no código do jogo, {name}, 'GamePanel' usa herança ('extends JPanel') e implementa interfaces como 'Runnable' e 'KeyListener'!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "python_lang",
                new String[]{"python", "pip", "django", "flask", "pandas", "pytorch", "indentacao", "script"},
                new String[]{"o que e python", "aprender python", "python ou java"},
                new String[]{
                        "O Python é uma linguagem de sintaxe limpa onde a própria indentação (espaços) define os blocos de código em vez de chaves '{ }', {name}. Na prática, é a linguagem mais usada hoje para automação de scripts, Inteligência Artificial (PyTorch, TensorFlow), análise de dados (Pandas) e APIs web rápidas (FastAPI/Django)."
                },
                "Dica prática, {name}: use Python quando precisar criar automações rápidas ou trabalhar com IA/Dados, e Java para sistemas robustos de grande porte!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "hardware_cpu",
                new String[]{"cpu", "processador", "intel", "amd", "ryzen", "core", "nucleo", "nucleos", "threads", "clock", "ghz", "cache", "alu"},
                new String[]{"o que e cpu", "como funciona o processador", "intel ou amd", "clock do processador"},
                new String[]{
                        "O processador (CPU) executa programas em 3 passos contínuos, {name}: busca a instrução na RAM (Fetch), decodifica (Decode) e calcula na ULA (Execute). Para não esperar a lentidão da memória RAM, ele guarda os dados mais usados na memória Cache (L1, L2 e L3) dentro do próprio chip e divide o trabalho entre vários Núcleos (Cores) e Threads."
                },
                "Quando você roda um modelo .gguf aqui no jogo sem placa de vídeo dedicada, é a CPU que calcula as matrizes da IA usando metade dos seus núcleos, {name}!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "hardware_gpu",
                new String[]{"gpu", "placa", "video", "rtx", "gtx", "nvidia", "radeon", "vram", "shader", "raytracing", "fps"},
                new String[]{"placa de video", "o que e gpu", "cpu vs gpu", "ray tracing"},
                new String[]{
                        "A diferença prática entre CPU e GPU é o paralelismo, {name}: enquanto a CPU tem poucos núcleos muito fortes para tarefas sequenciais do sistema, a GPU (Placa de Vídeo) tem milhares de núcleos menores que calculam milhões de pixels 3D ou pesos de Inteligência Artificial todos ao mesmo tempo na memória VRAM!"
                },
                "Nosso jogo usa Raycasting via software na CPU, que é o precursor histórico do Ray Tracing feito em hardware pelas placas RTX atuais, {name}!"
        ));

        KNOWLEDGE_BASE.add(new KnowledgeIntent(
                "nerd_humor",
                new String[]{"piada", "engracado", "humor", "cafe", "bug", "bugs", "erro", "compilar"},
                new String[]{"conta uma piada", "me conta uma piada", "o que e bug", "na minha maquina funciona"},
                new String[]{
                        "Haha, boa {name}! Sabe por que existem apenas 10 tipos de pessoas no mundo? As que entendem binário e as que não entendem! ('10' em binário é 2 em decimal). E curiosidade rápida: o termo 'Bug' surgiu em 1947 quando a equipe de Grace Hopper tirou uma mariposa real de dentro de um relé do computador Harvard Mark II!"
                },
                "E lembre-se da frase clássica de todo programador, {name}: 'Na minha máquina funciona!' — foi por isso que criaram a JVM e o Docker!"
        ));
    }
}
