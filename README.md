# 🎮 RayCastingTI — Raycasting Tech School (Edição IA Local & Multi-GGUF)

**RayCastingTI** (*Raycasting: Lab-TI*) é um jogo educativo em 3D retrô inspirado nos clássicos *Wolfenstein 3D* e *DOOM*, inteiramente desenvolvido em **Java puro (Swing / AWT)** sem dependências de motores gráficos externos.

No jogo, você explora um **Laboratório de Tecnologia da Informação** em primeira pessoa, interage com o **Professor de TI** (movido por **Inteligência Artificial 100% Local** com suporte a múltiplos modelos **GGUF** via `llama.cpp`) para tirar dúvidas sobre programação, hardware, redes e desenvolvimento de jogos, e utiliza os computadores do laboratório através de um **Terminal CMD Virtual** repleto de comandos e *Easter Eggs* jogáveis!

---

## ✨ Principais Recursos

- **🧱 Motor Gráfico Raycasting 3D Próprio (DDA + Z-Buffer):**
  - Renderização por software direto em buffer de pixels (`DataBufferInt`) para alto desempenho (60 FPS em até `1920x1080` e **Tela Cheia**).
  - Mapeamento de texturas em piso antiestático, paredes técnicas, rack de servidores, bancadas 3D com tampo e *sprites* 2D (*billboarding*) ordenados por profundidade.
- **🧠 Seletor Dinâmico de Modelos de IA Local (`gguf/` + `llama.cpp`):**
  - **Qualquer modelo `.gguf` na pasta `gguf/` pode ser usado:** Basta colocar um ou mais arquivos `.gguf` dentro da pasta `gguf/` do projeto e escolher qual modelo deseja usar diretamente dentro do menu **Opções de Vídeo e IA** do jogo!
  - **Modo `IA Leve Nativa (0 MB RAM - Instantânea)`:** Ideal para computadores mais modestos ou notebooks sem muita memória RAM livre — responde instantaneamente usando o motor NLP embarcado em Java puro e desliga o `llama-server.exe`.
  - **Encerramento Automático Garantido (*Watchdog* + *ShutdownHook*):** Ao fechar o jogo (pelo botão `X`, pelo menu `Sair`, por `Alt+F4` ou mesmo clicando no botão *Stop* do NetBeans), o processo em segundo plano `llama-server.exe` é encerrado automaticamente em segundos, liberando 100% da memória RAM e CPU.
- **🖥️ Opções Avançadas de Vídeo:**
  - **Modo Tela Cheia (*Fullscreen Borderless*)** ou **Modo Janela** (alternável pelo menu ou a qualquer momento pressionando a tecla **`F11`**).
  - **Resoluções suportadas:** `800x600 (4:3)`, `1280x720 (HD)`, `1600x900 (HD+)` e `1920x1080 (Full HD)`.
  - **Ajuste de Campo de Visão (FOV):** `Padrão (66°)`, `Amplo (78°)` e `Ultra (90°)`.
- **💻 Terminal CMD Virtual Completo & 6 Mini-Games Arcade (`Downloads/`):**
  - Sistema de arquivos virtual em memória (`C:\Windows\System32`, `C:\Program Files\RayCastingTI`, `C:\Users\<Aluno>\Downloads`, `Desktop`, `Documents`, `Pictures`) com **mais de 45 comandos reais** de Windows/Linux, histórico com `Seta Cima/Baixo`, rolagem com `PageUp/PageDown`/Roda do Mouse, autocompletar inteligente com `TAB` e troca de cores (`color 0a`, `color 1f`, etc.).
  - **6 Jogos Executáveis 100% refeitos e sem bugs na pasta `Downloads`:** `pacman.exe`, `tetris.exe`, `doom.exe`, `dino.exe`, `snake.exe` e `matrix.exe`!

---

## ⚠️ Aviso Importante sobre Desempenho da IA Local e Configurações Recomendadas

Como a Inteligência Artificial do jogo roda **100% localmente no seu próprio computador** (sem usar servidores na nuvem), o modelo `.gguf` precisa ser carregado inteiramente na sua **Memória RAM** e processado pela sua **CPU**. Modelos grandes de linguagem (LLMs) exigem bastante hardware!

> [!IMPORTANT]
> **O seu PC ficou pesado ao carregar o modelo?**
> No menu **Opções de Vídeo e IA** do jogo, você pode trocar a opção **"Modelo IA (pasta gguf/)"** para **`IA Leve Nativa (0 MB RAM - Instantânea)`** ou colocar um modelo `.gguf` menor (como `Qwen2.5-0.5B` ou `Llama-3.2-1B`) na pasta `gguf/`. Ao selecionar a IA Leve Nativa, o jogo fecha o `llama-server.exe` na hora e libera toda a memória RAM!

### 💻 Tabela de Requisitos de Hardware por Tipo de Modelo

| Modo / Tamanho do Modelo `.gguf` | Exemplos de Modelos Compatíveis | Memória RAM Livre Necessária | Processador (CPU) Recomendado | Tempo Médio de Resposta (CPU) |
| :--- | :--- | :--- | :--- | :--- |
| **IA Leve Nativa (Java NLP)** | *Embarcado no código (Sem `.gguf`)* | **~50 MB RAM** *(Ultra Leve)* | Qualquer Dual-Core (Intel Celeron / Core i3 ou superior) | **Instantâneo (< 0.3s)** |
| **Modelos GGUF Compactos (0.5B a 1.5B)** | `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf`, `Llama-3.2-1B-Instruct-Q4_K_M.gguf`, `gemma-3-1b-it-Q4_K_M.gguf` | **4 GB a 8 GB RAM** no sistema (~1 GB livre) | Quad-Core (Intel Core i3 / AMD Ryzen 3 ou superior) | **Rápido (1s a 4s)** |
| **Modelos GGUF Médios (2B a 3B)** | `gemma-2-2b-it-Q4_K_M.gguf`, `Qwen2.5-3B-Instruct-Q4_K_M.gguf`, `Llama-3.2-3B-Instruct-Q4_K_M.gguf` | **8 GB a 12 GB RAM** no sistema (~2.5 GB livres) | Hexa-Core (Intel Core i5 / AMD Ryzen 5 ou superior) | **Moderado (4s a 10s)** |
| **Modelos GGUF Pesados (4B a 8B)** | **`gemma-4-E4B-it-Q4_K_M.gguf` (4.6 GB)**, `Llama-3.1-8B-Instruct-Q4_K_M.gguf` | **16 GB RAM ou mais** (~5 GB livres só para o modelo) | Octa-Core moderno com AVX2 (Intel Core i7 / AMD Ryzen 7) | **Pesado em CPU (12s a 25s)** |

### 🔧 Otimizações Aplicadas no Jogo para Proteger seu PC
1. **Limite Inteligente de Threads (`-t`):** O jogo limita automaticamente o `llama-server.exe` a usar no máximo **metade dos núcleos da sua CPU**, garantindo que o Windows e o motor 3D do jogo continuem rodando a 60 FPS sem travamentos.
2. **Desativação de Raciocínio Longo (`--reasoning off`):** Evita que modelos como o Gemma-4 gastem dezenas de segundos gerando blocos ocultos de pensamento antes de responder.
3. **Watchdog de Encerramento Automático:** Um vigia leve monitora o PID do jogo; assim que o jogo fecha (mesmo se for forçado a fechar), o processo `llama-server.exe` em segundo plano é finalizado imediatamente.

---

## 🔄 Como Adicionar e Escolher Outros Modelos `.gguf` no Jogo

1. Baixe qualquer modelo de linguagem no formato `.gguf` (por exemplo, no [Hugging Face](https://huggingface.co/)).
2. Copie o arquivo `.gguf` para dentro da pasta **`gguf/`** do projeto:
   ```text
   C:\Users\matheus.duarte\Documents\NetBeansProjects\RayCastingTI\gguf\
   ```
3. Dentro do jogo, abra **OPÇÕES DE VÍDEO E IA**:
   - Caso o jogo já esteja aberto, clique em **`[ ATUALIZAR LISTA DE MODELOS .GGUF ]`**.
   - Na linha **`Modelo IA (pasta gguf/):`**, use as **Setas Esquerda/Direita** (ou clique com o mouse) para alternar entre qualquer um dos arquivos `.gguf` da pasta ou a **`IA Leve Nativa (0 MB RAM)`**.

---

## 🕹️ Mini-Games Arcade na Pasta `Downloads` dos PCs

Ao interagir com qualquer computador nas bancadas (`E` ou `Clique Direito`), digite `cd Downloads` (ou rode o nome do jogo de qualquer pasta) para jogar versões completas e sem bugs:

| Executável | Jogo / Mecânicas | Controles |
| :--- | :--- | :--- |
| **`pacman.exe`** | **Pac-Man Arcade:** Labirinto clássico 19x11, pílulas normais, 4 *Power Pellets* energizantes (deixam os fantasmas azuis e vulneráveis!), 3 vidas e 4 fantasmas (*Blinky, Pinky, Inky, Clyde*) com curva pré-agendada suave. | `WASD` ou `Setas` para mover \| `R` Reiniciar \| `ESC` Sair |
| **`tetris.exe`** | **Tetris Oficial:** Grade 10x18 com as 7 peças oficiais (*I, O, T, S, Z, J, L*), rotação com *wall-kick*, sombra guia (*Ghost Piece*), painel de próxima peça (*Next*), limpeza de linhas e queda rápida. | `A/D` ou `Esq/Dir` Mover \| `W/Cima` Girar \| `S/Baixo` Descer \| `ESPAÇO` Queda Instantânea (*Hard Drop*) |
| **`doom.exe`** | **DOOM 2D Arena Shooter:** Ondas progressivas de demônios (*Imps*, *Pinky Demons* e *Cacodemons*), bolas de fogo inimigas, canhão de plasma, barra de HP e explosões de partículas. | `A/D` ou `Esq/Dir` Mover \| `ESPAÇO` ou `W/Cima` Atirar Plasma |
| **`dino.exe`** | **Chrome Dino Runner:** T-Rex Runner completo com física de pulo, agachamento, cactos simples/triplos, **Pterodáctilos voadores** em duas alturas, ciclo automático Dia/Noite e velocidade progressiva. | `ESPAÇO` ou `W/Cima` Pular \| `S/Baixo` Agachar |
| **`snake.exe`** | **Snake Clássico:** Jogo da cobrinha em grade 24x14 com maçãs normais e maçãs douradas bônus. | `WASD` ou `Setas` para mudar direção |
| **`matrix.exe`** | **Matrix Digital Rain:** Simulador visual de chuva de código Matrix em tempo real. | `ESC` para voltar ao CMD |

---

## 💻 Comandos do Terminal CMD Virtual (`VirtualTerminal`)

O terminal dos computadores do laboratório funciona como um **Prompt de Comando real** onde você pode explorar pastas, criar/editar/apagar arquivos, testar ferramentas de rede, rodar scripts e conversar com a IA:

- **📂 Arquivos e Diretórios:**
  - `dir` / `ls` — Lista arquivos e pastas do diretório atual com data, tamanho e contador de bytes livres.
  - `cd <pasta>`, `cd ..`, `cd \`, `cd ~` — Navega entre `C:\`, `C:\Windows\System32`, `C:\Program Files\RayCastingTI`, `C:\Users\<Aluno>\Downloads`, `Desktop`, `Documents`, `Pictures`.
  - `tree` — Desenha a árvore hierárquica completa de subpastas e arquivos em ASCII.
  - `type <arquivo>` / `cat <arquivo>` — Exibe o conteúdo de qualquer arquivo `.txt`, `.java`, `.py`, `.md` ou `.log`.
  - `mkdir <nome>` / `md` e `rmdir <nome>` / `rd` — Cria e remove pastas no sistema de arquivos virtual.
  - `echo <texto> > arquivo.txt` e `echo <texto> >> arquivo.txt` — Cria ou adiciona texto dentro de arquivos reais na memória!
  - `touch <arquivo>` / `edit <arquivo> <conteudo>` — Cria ou edita arquivos rapidamente.
  - `copy <orig> <dest>` (`cp`), `ren <antigo> <novo>` (`mv`), `del <arquivo>` (`rm`) — Copia, renomeia e apaga arquivos.
  - `find <termo>` / `grep <termo>` — Busca um texto dentro de todos os arquivos da pasta atual.
- **🌐 Rede e Segurança Cibernética:**
  - `ipconfig` e `ipconfig /all` — Exibe placas de rede, MAC Address, IPv4, Gateway, DHCP e DNS.
  - `ping <host>` — Dispara pacotes ICMP com estatísticas de latência e TTL.
  - `tracert <host>` — Rastreia os saltos (*hops*) até o destino.
  - `nslookup <dominio>` — Resolve nomes DNS.
  - `netstat` — Lista conexões TCP ativas e portas em estado `LISTENING` (incluindo a porta `18080` da IA).
  - `arp -a` — Exibe a tabela ARP da LAN do laboratório.
  - `nmap <alvo>` — Executa um *Port Scan* educacional mostrando serviços abertos na rede do laboratório.
  - `curl <url>` e `ssh <usuario@host>` — Testa requisições HTTP (inclusive `/health` do `llama-server`) e conexões SSH.
- **⚙️ Sistema, Hardware e Desenvolvimento:**
  - `systeminfo` / `neofetch` — Exibe resumo completo da máquina, Java VM, CPU, RAM consumida e GPU.
  - `tasklist` / `ps` e `taskkill /im <proc>` — Lista processos em execução e permite encerrar tarefas (inclusive desligar o `llama-server.exe` pelo CMD!).
  - `ver`, `whoami`, `hostname`, `date`, `time`, `chkdsk`, `vol`, `mem` / `free`, `set` / `env`, `history`.
  - `color <hex>` (`color 0a`, `color 07`, `color 0b`, `color 0e`, `color 0c`, `color 1f`, `color 70`, `color ?`) e `title <texto>` — Personaliza as cores do fundo/texto e o título da janela CMD!
  - `java -version`, `javac Main.java`, `java Main.java`, `python script.py` — Simula compilação e execução dos códigos-fonte da pasta `Documents`!
  - `calc <expressão>`, `bin <num>`, `hex <num>` — Calculadora científica (`+ - * / % ^`) e conversor Binário/Hexadecimal.
  - `ai <sua pergunta>`, `ai-status`, `ai-stop` — Faz perguntas para a IA diretamente de dentro do terminal CMD!
  - **Easter Eggs de Terminal:** `cowsay <frase>`, `hack`, `coffee`, `joke`.

---

## 🎮 Controles Gerais do Jogo

| Tecla / Ação | Função |
| :--- | :--- |
| `W`, `S` ou `Seta Cima`, `Seta Baixo` | Andar para frente / para trás no laboratório 3D |
| `A`, `D` ou `Seta Esquerda`, `Seta Direita` | Girar a câmera para a esquerda / direita |
| `Clique Direito` ou Tecla `E` | Interagir com o **Professor de TI** ou acessar um **PC da Bancada** |
| `Clique Esquerdo` ou `ESPAÇO` | Soco / Ação em primeira pessoa |
| `F11` | Alternar entre **Tela Cheia (Fullscreen)** e **Modo Janela** a qualquer momento |
| `TAB` *(no CMD)* | Autocompleta comandos e nomes de arquivos/pastas |
| `Seta Cima / Baixo` *(no CMD)* | Navega pelo histórico de comandos digitados |
| `PageUp / PageDown / Scroll` *(no CMD)* | Rola o histórico de saída do terminal |
| `ESC` | Pausar o jogo / Fechar mini-game / Sair do CMD ou chat |

---

## 🚀 Como Executar o Jogo

### Opção 1: Executar direto pelo arquivo JAR compilado
```powershell
cd C:\Users\matheus.duarte\Documents\NetBeansProjects\RayCastingTI
java -jar dist/RayCastingTI.jar
```

### Opção 2: Executar pelo Apache NetBeans
1. Abra o projeto `RayCastingTI` no **NetBeans IDE**.
2. Pressione `F6` (**Run Project**).

---

## 📂 Estrutura do Projeto

```text
RayCastingTI/
├── src/raycastingti/
│   ├── DoomEngine.java      # Janela principal (JFrame) e encerramento seguro do servidor IA
│   ├── GamePanel.java       # Motor Raycasting 3D, Tela Cheia (F11), menus e integração geral
│   ├── ArcadeGames.java     # Motor completo dos 6 Mini-Games (Pac-Man, Tetris, Doom 2D, Dino, Snake, Matrix)
│   ├── VirtualTerminal.java # Simulador de CMD com Sistema de Arquivos Virtual e 45+ comandos
│   ├── Entity.java          # Representação de entidades/sprites no mundo 3D (Professor e PCs)
│   └── ProfessorAI.java     # Gerenciador multi-modelo GGUF (llama-server + Watchdog + IA Leve)
├── gguf/                    # Coloque seus arquivos .gguf aqui (ignorados pelo Git via .gitignore)
├── llama-b11669-bin-win-cpu-x64/ # Binários locais do motor llama.cpp (llama-server.exe)
├── sprites/                 # Texturas do laboratório, rack de servidores, bancadas, PC, professor e HUD
├── .gitignore               # Ignora arquivos .gguf e binários pesados para não travar o Git/GitHub
└── dist/RayCastingTI.jar          # Executável JAR empacotado e pronto para rodar
```
