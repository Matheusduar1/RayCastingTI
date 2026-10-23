package raycastingti;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener, MouseWheelListener {

    enum State { MENU, NAME_INPUT, OPTIONS, PLAYING, PAUSED, NPC_CHAT, CMD_VIRTUAL, EASTER_EGG }
    State currentState = State.MENU;

    int screenWidth = 800;
    int screenHeight = 600;
    int windowedWidth = 800;
    int windowedHeight = 600;
    boolean isFullScreen = false;

    final String[] resolutionLabels = { "800x600 (4:3)", "1280x720 (HD)", "1600x900 (HD+)", "1920x1080 (Full HD)" };
    final int[][] resolutionSizes = { {800, 600}, {1280, 720}, {1600, 900}, {1920, 1080} };
    int selectedResIndex = 0;

    final String[] fovLabels = { "Padrão (66°)", "Amplo (78°)", "Ultra (90°)" };
    final double[] fovValues = { 0.66, 0.80, 0.96 };
    int selectedFovIndex = 0;

    String playerName = "";
    double posX = 4.5, posY = 4.5;
    double dirX = -1.0, dirY = 0.0;
    double planeX = 0.0, planeY = 0.66;
    double menuAnimTick = 0.0;

    boolean moveForward, moveBackward, rotateLeft, rotateRight;
    boolean isPunching = false;
    int punchFrame = 0;

    double[] zBuffer;
    double[] tableZBuffer;
    int[] tableTopYBuffer;
    int[] wallTypeBuffer;

    BufferedImage screenBuffer;
    int[] screenPixels;

    int[] floorTexArray;
    int[] wall1TexArray;
    int[] wall4TexArray;
    int[] tableTexArray;

    int[][] worldMap = {
        {1,1,1,1,1,1,1,1,1,4,1},
        {1,0,3,0,0,0,0,0,0,0,1},
        {1,2,2,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,1},
        {1,0,0,0,0,0,0,0,0,0,1},
        {1,5,0,0,5,2,5,0,0,5,1},
        {1,5,0,0,5,2,5,0,0,5,1},
        {1,5,0,0,5,2,5,0,0,5,1},
        {1,1,1,1,1,1,1,1,1,1,1}
    };

    BufferedImage texWall1, texWall4, texTable, texFloor, texHandOpen, texPunch;
    ArrayList<Entity> entities = new ArrayList<>();

    volatile String professorResponse = "Olá! Eu sou o Professor Diego, responsável pelo Laboratório de TI. Sobre qual tema de programação, hardware, redes ou desenvolvimento de jogos você gostaria de uma aula explicativa e completa hoje?";
    String playerChatInput = "";
    int chatScrollOffset = 0;
    volatile boolean loadingAI = false;

    int menuSelection = 0;
    int optionsSelection = 0; // 0=Modo Tela, 1=Resolução, 2=FOV, 3=Modelo IA (gguf/), 4=Recarregar gguf/, 5=Voltar
    int pauseSelection = 0;

    // Motores dedicados do Terminal CMD Virtual e dos Mini-Games Arcade
    final ArcadeGames arcadeGames = new ArcadeGames();
    final VirtualTerminal virtualTerminal;

    private static class TableHit {
        double entryDist;
        double exitDist;
        int side;
        int texX;
    }

    public GamePanel() {
        setPreferredSize(new Dimension(screenWidth, screenHeight));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);

        virtualTerminal = new VirtualTerminal(new VirtualTerminal.TerminalListener() {
            @Override
            public void onLaunchGame(ArcadeGames.GameType gameType) {
                arcadeGames.startGame(gameType);
                currentState = State.EASTER_EGG;
            }

            @Override
            public void onExitTerminal() {
                resetMovementFlags();
                currentState = State.PLAYING;
            }
        });

        initBuffers();
        loadGameAssets();

        // Inicia o motor de IA local com o modelo selecionado da pasta gguf/
        ProfessorAI.initLocalEngineAsync();

        Thread gameThread = new Thread(this, "RayCastingTI-GameLoop");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    private void exitGameCleanly() {
        ProfessorAI.shutdownServer();
        System.exit(0);
    }

    private void applyVideoSettings(boolean toggleFullscreenOnly) {
        Window topWindow = SwingUtilities.getWindowAncestor(this);
        if (!(topWindow instanceof JFrame)) return;
        JFrame topFrame = (JFrame) topWindow;

        windowedWidth = resolutionSizes[selectedResIndex][0];
        windowedHeight = resolutionSizes[selectedResIndex][1];

        int targetW = windowedWidth;
        int targetH = windowedHeight;

        if (isFullScreen) {
            Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
            targetW = screenDim.width;
            targetH = screenDim.height;
        }

        boolean modeChanged = (topFrame.isUndecorated() != isFullScreen);
        screenWidth = targetW;
        screenHeight = targetH;
        initBuffers();
        setPreferredSize(new Dimension(screenWidth, screenHeight));

        if (modeChanged) {
            topFrame.dispose();
            topFrame.setUndecorated(isFullScreen);
            if (isFullScreen) {
                topFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                topFrame.setSize(screenWidth, screenHeight);
                topFrame.setLocation(0, 0);
            } else {
                topFrame.setExtendedState(JFrame.NORMAL);
                topFrame.pack();
                topFrame.setLocationRelativeTo(null);
            }
            topFrame.setVisible(true);
        } else if (!isFullScreen) {
            topFrame.pack();
            topFrame.setLocationRelativeTo(null);
        }
        requestFocusInWindow();
    }

    private void applyFovSetting() {
        double fovMag = fovValues[selectedFovIndex];
        // O vetor plane é sempre perpendicular ao vetor dir (-dirY, dirX)
        planeX = dirY * fovMag;
        planeY = -dirX * fovMag;
    }

    private synchronized void initBuffers() {
        zBuffer = new double[screenWidth];
        tableZBuffer = new double[screenWidth];
        tableTopYBuffer = new int[screenWidth];
        wallTypeBuffer = new int[screenWidth];
        screenBuffer = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
        screenPixels = ((DataBufferInt) screenBuffer.getRaster().getDataBuffer()).getData();
    }

    private void loadGameAssets() {
        texWall1 = loadOrCreateFallback("sprites/wall1.png", Color.DARK_GRAY, true, 128, 128);
        texWall4 = loadOrCreateFallback("sprites/wall4.png", Color.BLUE, true, 128, 128);
        texTable = loadOrCreateFallback("sprites/table.png", new Color(139, 69, 19), true, 128, 128);
        texFloor = loadOrCreateFallback("sprites/floor.png", new Color(50, 50, 50), true, 128, 128);

        ensureOriginalHandSprites();
        texHandOpen = loadOrCreateFallback("sprites/hand_open.png", new Color(236, 186, 148), false, 256, 256);
        texPunch = loadOrCreateFallback("sprites/punch.png", new Color(236, 186, 148), false, 256, 256);

        // O sprite npc.png do Professor é carregado sem qualquer modificação
        BufferedImage texNPC = loadOrCreateFallback("sprites/npc.png", Color.GREEN, false, 500, 500);
        BufferedImage texPC = loadOrCreateFallback("sprites/pc.png", Color.LIGHT_GRAY, false, 128, 128);

        floorTexArray = texFloor.getRGB(0, 0, 128, 128, null, 0, 128);
        wall1TexArray = texWall1.getRGB(0, 0, 128, 128, null, 0, 128);
        wall4TexArray = texWall4.getRGB(0, 0, 128, 128, null, 0, 128);
        tableTexArray = texTable.getRGB(0, 0, 128, 128, null, 0, 128);

        for (int x = 0; x < worldMap.length; x++) {
            for (int y = 0; y < worldMap[0].length; y++) {
                if (worldMap[x][y] == 3) {
                    entities.add(new Entity(x + 0.5, y + 0.5, 3, 0.9, 0.0, texNPC));
                    worldMap[x][y] = 0;
                } else if (worldMap[x][y] == 5) {
                    entities.add(new Entity(x + 0.5, y + 0.5, 5, 0.30, 0.21, texPC));
                    worldMap[x][y] = 2;
                }
            }
        }
    }

    /**
     * Garante a geração dos sprites originais de mão humana comum:
     * - sprites/hand_open.png (mão aberta relaxada em 1ª pessoa)
     * - sprites/punch.png     (mão fechada dando soco em 1ª pessoa)
     */
    public static void ensureOriginalHandSprites() {
        try {
            File spritesDir = new File("sprites");
            if (!spritesDir.exists()) {
                File parentSprites = new File("..", "sprites");
                if (parentSprites.exists()) spritesDir = parentSprites;
                else spritesDir.mkdirs();
            }

            File openFile = new File(spritesDir, "hand_open.png");
            BufferedImage open64 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            drawOpenHandSprite(open64.createGraphics());
            saveScaledPixelArt(open64, 256, 256, openFile);

            File punchFile = new File(spritesDir, "punch.png");
            BufferedImage punch64 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            drawPunchHandSprite(punch64.createGraphics());
            saveScaledPixelArt(punch64, 256, 256, punchFile);
        } catch (Exception ignored) {
        }
    }

    private static void saveScaledPixelArt(BufferedImage src, int w, int h, File out) throws Exception {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ImageIO.write(dst, "png", out);
    }

    private static void drawOpenHandSprite(Graphics2D g) {
        Color outline = new Color(38, 24, 18);
        Color skinBase = new Color(236, 186, 148);
        Color skinLight = new Color(250, 210, 176);
        Color skinShadow = new Color(198, 142, 105);
        Color skinDeep = new Color(168, 112, 80);
        Color nailColor = new Color(252, 232, 218);

        Color sleeveDark = new Color(28, 52, 88);
        Color sleeveMid = new Color(42, 78, 128);
        Color sleeveLight = new Color(64, 108, 168);
        Color cuffWhite = new Color(225, 232, 242);

        // 1. Manga de camisa casual azul + punho branco
        g.setColor(outline);
        g.fillRect(23, 47, 22, 17);
        g.setColor(sleeveMid);
        g.fillRect(24, 49, 20, 15);
        g.setColor(sleeveLight);
        g.fillRect(25, 49, 6, 15);
        g.setColor(sleeveDark);
        g.fillRect(38, 49, 6, 15);
        g.setColor(cuffWhite);
        g.fillRect(24, 47, 20, 2);

        // 2. Pulso humano comum
        g.setColor(outline);
        g.fillRect(24, 40, 19, 8);
        g.setColor(skinBase);
        g.fillRect(25, 40, 17, 7);
        g.setColor(skinShadow);
        g.fillRect(38, 40, 4, 7);

        // 3. Palma aberta
        g.setColor(outline);
        g.fillRoundRect(20, 25, 25, 17, 5, 5);
        g.setColor(skinBase);
        g.fillRoundRect(21, 26, 23, 15, 4, 4);
        g.setColor(skinLight);
        g.fillRect(23, 27, 12, 10);
        g.setColor(skinShadow);
        g.fillRect(38, 27, 5, 13);
        g.setColor(skinDeep);
        g.drawLine(25, 34, 34, 36);
        g.drawLine(27, 30, 38, 31);

        // 4. Polegar aberto à esquerda
        g.setColor(outline);
        g.fillRoundRect(12, 24, 10, 13, 4, 4);
        g.setColor(skinBase);
        g.fillRoundRect(13, 25, 8, 11, 3, 3);
        g.setColor(skinLight);
        g.fillRect(14, 26, 4, 6);
        g.setColor(nailColor);
        g.fillRect(14, 25, 3, 2);

        // 5. Quatro dedos abertos (Indicador, Médio, Anelar, Mínimo)
        int[] fx = {20, 26, 32, 38};
        int[] fy = {12, 10, 12, 16};
        int[] fh = {15, 17, 15, 12};
        for (int i = 0; i < 4; i++) {
            g.setColor(outline);
            g.fillRoundRect(fx[i], fy[i], 6, fh[i], 3, 3);
            g.setColor(skinBase);
            g.fillRect(fx[i] + 1, fy[i] + 1, 4, fh[i] - 2);
            g.setColor(skinLight);
            g.fillRect(fx[i] + 1, fy[i] + 2, 2, fh[i] - 4);
            g.setColor(nailColor);
            g.fillRect(fx[i] + 1, fy[i] + 1, 3, 2);
            g.setColor(skinShadow);
            g.drawLine(fx[i] + 1, fy[i] + fh[i] / 2, fx[i] + 4, fy[i] + fh[i] / 2);
        }
        g.dispose();
    }

    private static void drawPunchHandSprite(Graphics2D g) {
        Color outline = new Color(38, 24, 18);
        Color skinBase = new Color(236, 186, 148);
        Color skinLight = new Color(250, 210, 176);
        Color skinShadow = new Color(198, 142, 105);
        Color skinDeep = new Color(168, 112, 80);

        Color sleeveDark = new Color(28, 52, 88);
        Color sleeveMid = new Color(42, 78, 128);
        Color sleeveLight = new Color(64, 108, 168);
        Color cuffWhite = new Color(225, 232, 242);

        // Linhas sutis de movimento do soco
        g.setColor(new Color(255, 255, 220, 170));
        g.drawLine(14, 10, 10, 4);
        g.drawLine(32, 6, 32, 1);
        g.drawLine(50, 10, 54, 4);

        // 1. Manga esticada no soco
        g.setColor(outline);
        g.fillRect(21, 40, 24, 24);
        g.setColor(sleeveMid);
        g.fillRect(22, 42, 22, 22);
        g.setColor(sleeveLight);
        g.fillRect(23, 42, 7, 22);
        g.setColor(sleeveDark);
        g.fillRect(37, 42, 7, 22);
        g.setColor(cuffWhite);
        g.fillRect(22, 40, 22, 2);

        // 2. Pulso firme
        g.setColor(outline);
        g.fillRect(22, 33, 21, 8);
        g.setColor(skinBase);
        g.fillRect(23, 33, 19, 7);
        g.setColor(skinShadow);
        g.fillRect(37, 33, 5, 7);

        // 3. Punho fechado (Soco comum sem luva do Doom)
        g.setColor(outline);
        g.fillRoundRect(16, 12, 32, 23, 6, 6);
        g.setColor(skinBase);
        g.fillRoundRect(17, 13, 30, 21, 5, 5);

        int[] kx = {18, 25, 32, 39};
        for (int i = 0; i < 4; i++) {
            g.setColor(skinLight);
            g.fillRect(kx[i], 14, 5, 5);
            g.setColor(skinShadow);
            g.fillRect(kx[i], 20, 6, 8);
            g.setColor(skinDeep);
            g.drawRect(kx[i], 19, 6, 9);
        }

        // 4. Polegar travando o punho fechado
        g.setColor(outline);
        g.fillRoundRect(14, 23, 20, 9, 4, 4);
        g.setColor(skinBase);
        g.fillRect(15, 24, 18, 7);
        g.setColor(skinLight);
        g.fillRect(16, 25, 14, 3);
        g.setColor(skinDeep);
        g.drawLine(16, 29, 31, 29);

        g.dispose();
    }

    private BufferedImage loadOrCreateFallback(String path, Color fallbackColor, boolean forceSize, int w, int h) {
        BufferedImage loaded = null;
        try {
            File f = new File(path);
            if (!f.exists()) {
                File parentFile = new File("..", path);
                if (parentFile.exists()) f = parentFile;
            }
            if (f.exists()) {
                loaded = ImageIO.read(f);
            } else {
                String resourcePath = path.startsWith("/") ? path : "/" + path;
                try (InputStream is = GamePanel.class.getResourceAsStream(resourcePath)) {
                    if (is != null) loaded = ImageIO.read(is);
                }
            }
        } catch (Exception ignored) {
        }

        if (loaded != null) {
            int targetW = forceSize ? w : loaded.getWidth();
            int targetH = forceSize ? h : loaded.getHeight();
            BufferedImage argb = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = argb.createGraphics();
            g2.drawImage(loaded, 0, 0, targetW, targetH, null);
            g2.dispose();
            return argb;
        }

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(fallbackColor);
        g.fillRect(0, 0, w, h);
        if (forceSize) {
            g.setColor(Color.BLACK);
            g.drawRect(0, 0, w - 1, h - 1);
            g.drawLine(0, 0, w, h);
        }
        g.dispose();
        return img;
    }

    private void resetMovementFlags() {
        moveForward = false;
        moveBackward = false;
        rotateLeft = false;
        rotateRight = false;
        isPunching = false;
        punchFrame = 0;
    }

    @Override
    public void run() {
        while (true) {
            updateGameLogic();
            repaint();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void updateGameLogic() {
        menuAnimTick += 0.03;

        if (currentState == State.PLAYING) {
            double moveSpeed = 0.055;
            double rotSpeed = 0.04;
            double colBuffer = 0.30;

            if (moveForward) {
                double nextX = posX + dirX * moveSpeed;
                double checkX = posX + dirX * (moveSpeed + colBuffer);
                if (isWalkable(checkX, posY)) posX = nextX;

                double nextY = posY + dirY * moveSpeed;
                double checkY = posY + dirY * (moveSpeed + colBuffer);
                if (isWalkable(posX, checkY)) posY = nextY;
            }
            if (moveBackward) {
                double nextX = posX - dirX * moveSpeed;
                double checkX = posX - dirX * (moveSpeed + colBuffer);
                if (isWalkable(checkX, posY)) posX = nextX;

                double nextY = posY - dirY * moveSpeed;
                double checkY = posY - dirY * (moveSpeed + colBuffer);
                if (isWalkable(posX, checkY)) posY = nextY;
            }
            if (rotateRight) {
                double oldDirX = dirX;
                dirX = dirX * Math.cos(-rotSpeed) - dirY * Math.sin(-rotSpeed);
                dirY = oldDirX * Math.sin(-rotSpeed) + dirY * Math.cos(-rotSpeed);
                double oldPlaneX = planeX;
                planeX = planeX * Math.cos(-rotSpeed) - planeY * Math.sin(-rotSpeed);
                planeY = oldPlaneX * Math.sin(-rotSpeed) + planeY * Math.cos(-rotSpeed);
            }
            if (rotateLeft) {
                double oldDirX = dirX;
                dirX = dirX * Math.cos(rotSpeed) - dirY * Math.sin(rotSpeed);
                dirY = oldDirX * Math.sin(rotSpeed) + dirY * Math.cos(rotSpeed);
                double oldPlaneX = planeX;
                planeX = planeX * Math.cos(rotSpeed) - planeY * Math.sin(rotSpeed);
                planeY = oldPlaneX * Math.sin(rotSpeed) + planeY * Math.cos(rotSpeed);
            }
            if (isPunching) {
                punchFrame++;
                if (punchFrame > 10) {
                    isPunching = false;
                    punchFrame = 0;
                }
            }
        } else if (currentState == State.EASTER_EGG) {
            arcadeGames.update();
        }
    }

    private boolean isWalkable(double x, double y) {
        int mx = (int) x;
        int my = (int) y;
        if (mx < 0 || mx >= worldMap.length || my < 0 || my >= worldMap[0].length) {
            return false;
        }
        if (worldMap[mx][my] != 0) {
            return false;
        }
        for (Entity ent : entities) {
            if (ent.type == 3 && Math.hypot(x - ent.x, y - ent.y) < 0.55) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        switch (currentState) {
            case MENU:
                drawMainMenu(g2d);
                break;
            case NAME_INPUT:
                drawNameInput(g2d);
                break;
            case OPTIONS:
                drawOptions(g2d);
                break;
            case PAUSED:
                renderRaycasting3D(g2d);
                renderHUD(g2d);
                drawPauseMenu(g2d);
                break;
            case PLAYING:
            case NPC_CHAT:
            case CMD_VIRTUAL:
            case EASTER_EGG:
                renderRaycasting3D(g2d);
                renderHUD(g2d);
                if (currentState == State.NPC_CHAT) renderNPCChatWindow(g2d);
                if (currentState == State.CMD_VIRTUAL) virtualTerminal.render(g2d, screenWidth, screenHeight);
                if (currentState == State.EASTER_EGG) arcadeGames.render(g2d, screenWidth, screenHeight);
                break;
        }
        Toolkit.getDefaultToolkit().sync();
    }

    private void renderRaycasting3D(Graphics2D g2d) {
        int halfH = screenHeight / 2;

        for (int y = 0; y < halfH; y++) {
            int shade = 22 + (y * 18 / Math.max(1, halfH));
            int ceilingColor = (shade << 16) | ((shade + 2) << 8) | (shade + 6);
            int rowOffset = y * screenWidth;
            for (int x = 0; x < screenWidth; x++) {
                screenPixels[rowOffset + x] = ceilingColor;
            }
        }

        float rayDirX0 = (float) (dirX - planeX);
        float rayDirY0 = (float) (dirY - planeY);
        float rayDirX1 = (float) (dirX + planeX);
        float rayDirY1 = (float) (dirY + planeY);
        float posZ = 0.5f * screenHeight;

        for (int y = halfH; y < screenHeight; y++) {
            int p = y - halfH + 1;
            float rowDist = posZ / p;

            float stepX = rowDist * (rayDirX1 - rayDirX0) / screenWidth;
            float stepY = rowDist * (rayDirY1 - rayDirY0) / screenWidth;

            float fX = (float) posX + rowDist * rayDirX0;
            float fY = (float) posY + rowDist * rayDirY0;

            int rowOffset = y * screenWidth;
            for (int x = 0; x < screenWidth; x++) {
                int cellX = (int) fX;
                int cellY = (int) fY;
                int tx = (int) (128 * (fX - cellX)) & 127;
                int ty = (int) (128 * (fY - cellY)) & 127;

                fX += stepX;
                fY += stepY;
                screenPixels[rowOffset + x] = floorTexArray[(ty << 7) + tx];
            }
        }

        ArrayList<TableHit> columnTables = new ArrayList<>(4);

        for (int x = 0; x < screenWidth; x++) {
            double cameraX = 2.0 * x / (double) screenWidth - 1.0;
            double rayDirX = dirX + planeX * cameraX;
            double rayDirY = dirY + planeY * cameraX;

            int mapX = (int) posX;
            int mapY = (int) posY;

            double deltaDistX = (Math.abs(rayDirX) < 1e-10) ? 1e30 : Math.abs(1.0 / rayDirX);
            double deltaDistY = (Math.abs(rayDirY) < 1e-10) ? 1e30 : Math.abs(1.0 / rayDirY);

            double sideDistX, sideDistY;
            int stepX, stepY;

            if (rayDirX < 0) {
                stepX = -1;
                sideDistX = (posX - mapX) * deltaDistX;
            } else {
                stepX = 1;
                sideDistX = (mapX + 1.0 - posX) * deltaDistX;
            }
            if (rayDirY < 0) {
                stepY = -1;
                sideDistY = (posY - mapY) * deltaDistY;
            } else {
                stepY = 1;
                sideDistY = (mapY + 1.0 - posY) * deltaDistY;
            }

            columnTables.clear();
            boolean hitSolidWall = false;
            int side = 0;
            int solidBlockID = 1;
            int safetySteps = 0;

            while (!hitSolidWall && safetySteps++ < 64) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX;
                    mapX += stepX;
                    side = 0;
                } else {
                    sideDistY += deltaDistY;
                    mapY += stepY;
                    side = 1;
                }

                if (mapX < 0 || mapX >= worldMap.length || mapY < 0 || mapY >= worldMap[0].length) {
                    solidBlockID = 1;
                    hitSolidWall = true;
                    break;
                }

                int cell = worldMap[mapX][mapY];
                if (cell == 2) {
                    double entryDist = (side == 0) ? (sideDistX - deltaDistX) : (sideDistY - deltaDistY);
                    double exitDist = Math.min(sideDistX, sideDistY);
                    if (entryDist < 0.05) entryDist = 0.05;

                    double wallX = (side == 0) ? (posY + entryDist * rayDirY) : (posX + entryDist * rayDirX);
                    wallX -= Math.floor(wallX);
                    int texX = (int) (wallX * 128.0) & 127;
                    if (side == 0 && rayDirX > 0) texX = 127 - texX;
                    if (side == 1 && rayDirY < 0) texX = 127 - texX;

                    TableHit th = new TableHit();
                    th.entryDist = entryDist;
                    th.exitDist = Math.max(entryDist, exitDist);
                    th.side = side;
                    th.texX = texX;
                    columnTables.add(th);
                } else if (cell > 0) {
                    solidBlockID = cell;
                    hitSolidWall = true;
                }
            }

            double perpWallDist = (side == 0) ? (sideDistX - deltaDistX) : (sideDistY - deltaDistY);
            if (perpWallDist < 0.05) perpWallDist = 0.05;

            zBuffer[x] = perpWallDist;
            wallTypeBuffer[x] = columnTables.isEmpty() ? solidBlockID : 2;
            tableZBuffer[x] = columnTables.isEmpty() ? perpWallDist : columnTables.get(0).entryDist;
            tableTopYBuffer[x] = screenHeight;

            int lineHeight = (int) (screenHeight / perpWallDist);
            int rawStart = -lineHeight / 2 + halfH;
            int rawEnd = lineHeight / 2 + halfH;

            int drawStart = Math.max(0, rawStart);
            int drawEnd = Math.min(screenHeight - 1, rawEnd);

            double wallX = (side == 0) ? (posY + perpWallDist * rayDirY) : (posX + perpWallDist * rayDirX);
            wallX -= Math.floor(wallX);
            int texX = (int) (wallX * 128.0) & 127;
            if (side == 0 && rayDirX > 0) texX = 127 - texX;
            if (side == 1 && rayDirY < 0) texX = 127 - texX;

            int[] currentWallTex = (solidBlockID == 4) ? wall4TexArray : wall1TexArray;
            double step = 128.0 / lineHeight;
            double texPos = (drawStart - rawStart) * step;

            for (int y = drawStart; y <= drawEnd; y++) {
                int texY = ((int) texPos) & 127;
                texPos += step;
                int color = currentWallTex[(texY << 7) + texX];
                if (side == 1 && solidBlockID != 4) {
                    color = darkenColor(color);
                }
                screenPixels[y * screenWidth + x] = color;
            }

            for (int i = columnTables.size() - 1; i >= 0; i--) {
                TableHit th = columnTables.get(i);
                int tLineHeight = (int) (screenHeight / th.entryDist);
                int tRawEnd = tLineHeight / 2 + halfH;
                int frontTopY = halfH + (tLineHeight / 5);

                int backLineHeight = (int) (screenHeight / th.exitDist);
                int backTopY = halfH + (backLineHeight / 5);

                int topStartY = Math.max(halfH, Math.min(backTopY, frontTopY));
                int topEndY = Math.min(screenHeight - 1, Math.max(backTopY, frontTopY));
                int topSurfaceColor = (212 << 16) | (174 << 8) | 125;
                int topBorderColor = (120 << 16) | (88 << 8) | 52;
                for (int y = topStartY; y <= topEndY; y++) {
                    screenPixels[y * screenWidth + x] = (y == topStartY || y == topEndY) ? topBorderColor : topSurfaceColor;
                }

                int faceStart = Math.max(0, frontTopY);
                int faceEnd = Math.min(screenHeight - 1, tRawEnd);
                int faceHeight = Math.max(1, tRawEnd - frontTopY);
                double tStep = 128.0 / faceHeight;
                double tPos = (faceStart - frontTopY) * tStep;

                for (int y = faceStart; y <= faceEnd; y++) {
                    int texY = ((int) tPos) & 127;
                    tPos += tStep;
                    int color = tableTexArray[(texY << 7) + th.texX];
                    if (th.side == 1) {
                        color = darkenColor(color);
                    }
                    screenPixels[y * screenWidth + x] = color;
                }

                if (i == 0) {
                    tableTopYBuffer[x] = Math.max(0, topStartY);
                }
            }
        }

        g2d.drawImage(screenBuffer, 0, 0, null);
        renderSprites(g2d);
    }

    private int darkenColor(int rgb) {
        int r = (int) (((rgb >> 16) & 0xFF) * 0.80);
        int g = (int) (((rgb >> 8) & 0xFF) * 0.80);
        int b = (int) ((rgb & 0xFF) * 0.80);
        return (r << 16) | (g << 8) | b;
    }

    private void renderSprites(Graphics2D g2d) {
        entities.sort((e1, e2) -> {
            double d1 = Math.pow(posX - e1.x, 2) + Math.pow(posY - e1.y, 2);
            double d2 = Math.pow(posX - e2.x, 2) + Math.pow(posY - e2.y, 2);
            return Double.compare(d2, d1);
        });

        Entity closestPC = null;
        double closestPCDist = Double.MAX_VALUE;
        for (Entity ent : entities) {
            if (ent.type == 5) {
                double d = Math.hypot(posX - ent.x, posY - ent.y);
                if (d < closestPCDist) {
                    closestPCDist = d;
                    closestPC = ent;
                }
            }
        }

        for (Entity ent : entities) {
            double spriteX = ent.x - posX;
            double spriteY = ent.y - posY;

            double invDet = 1.0 / (planeX * dirY - dirX * planeY);
            double transformX = invDet * (dirY * spriteX - dirX * spriteY);
            double transformY = invDet * (-planeY * spriteX + planeX * spriteY);

            if (transformY > 0.15) {
                int spriteScreenX = (int) ((screenWidth / 2.0) * (1.0 + transformX / transformY));

                int texW = ent.tex.getWidth();
                int texH = ent.tex.getHeight();

                int stdHeight = Math.abs((int) (screenHeight / transformY));
                if (stdHeight > screenHeight * 4) stdHeight = screenHeight * 4;

                int spriteHeight = Math.max(1, (int) (stdHeight * ent.vScale));
                double aspect = (double) texW / texH;
                int spriteWidth = Math.max(1, (int) (spriteHeight * aspect));

                int baseEndY = screenHeight / 2 + stdHeight / 2;
                int drawEndY = baseEndY - (int) (ent.vOffset * stdHeight);
                int drawStartY = drawEndY - spriteHeight;

                int drawStartX = -spriteWidth / 2 + spriteScreenX;
                int drawEndX = spriteWidth / 2 + spriteScreenX;

                for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                    if (stripe >= 0 && stripe < screenWidth && transformY < zBuffer[stripe]) {
                        int texX = (stripe - drawStartX) * texW / spriteWidth;
                        if (texX < 0 || texX >= texW) continue;

                        int clipEndY = drawEndY;
                        if (transformY > tableZBuffer[stripe] + 0.55) {
                            clipEndY = Math.min(drawEndY, tableTopYBuffer[stripe]);
                        }

                        if (clipEndY > drawStartY) {
                            int visibleH = clipEndY - drawStartY;
                            int srcEndH = (int) ((double) visibleH / spriteHeight * texH);
                            srcEndH = Math.max(1, Math.min(texH, srcEndH));
                            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2d.drawImage(ent.tex, stripe, drawStartY, stripe + 1, clipEndY,
                                    texX, 0, texX + 1, srcEndH, null);
                        }
                    }
                }

                if (ent.type == 3 && currentState == State.PLAYING && transformY < 4.0
                        && spriteScreenX > 60 && spriteScreenX < screenWidth - 60) {
                    int bubbleY = Math.max(20, drawStartY - 58);
                    g2d.setColor(new Color(15, 22, 34, 235));
                    g2d.fillRoundRect(spriteScreenX - 115, bubbleY, 230, 48, 12, 12);
                    g2d.setColor(new Color(0, 230, 140));
                    g2d.drawRoundRect(spriteScreenX - 115, bubbleY, 230, 48, 12, 12);
                    g2d.setColor(new Color(0, 255, 150));
                    g2d.setFont(new Font("Arial", Font.BOLD, 12));
                    g2d.drawString("Professor Diego (TI)", spriteScreenX - 102, bubbleY + 19);
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Arial", Font.PLAIN, 11));
                    g2d.drawString("Clique Direito ou [E] p/ falar", spriteScreenX - 102, bubbleY + 36);
                }

                if (ent == closestPC && currentState == State.PLAYING && closestPCDist < 1.85
                        && transformY < 2.2 && spriteScreenX > 80 && spriteScreenX < screenWidth - 80) {
                    int bubbleY = Math.max(20, drawStartY - 45);
                    g2d.setColor(new Color(15, 25, 45, 230));
                    g2d.fillRoundRect(spriteScreenX - 105, bubbleY, 210, 38, 10, 10);
                    g2d.setColor(Color.CYAN);
                    g2d.drawRoundRect(spriteScreenX - 105, bubbleY, 210, 38, 10, 10);
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Arial", Font.BOLD, 11));
                    g2d.drawString("Workstation Lab-TI", spriteScreenX - 92, bubbleY + 15);
                    g2d.setFont(new Font("Arial", Font.PLAIN, 10));
                    g2d.setColor(Color.YELLOW);
                    g2d.drawString("Clique Direito ou [E] p/ abrir CMD", spriteScreenX - 92, bubbleY + 30);
                }
            }
        }
    }

    private void renderHUD(Graphics2D g2d) {
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        g2d.setColor(new Color(0, 255, 160, 200));
        g2d.drawLine(cx - 6, cy, cx - 2, cy);
        g2d.drawLine(cx + 2, cy, cx + 6, cy);
        g2d.drawLine(cx, cy - 6, cx, cy - 2);
        g2d.drawLine(cx, cy + 2, cx, cy + 6);

        // Mão original do personagem: Mão Aberta em repouso (texHandOpen) e Punho Fechado ao socar (texPunch)
        int armWidth = 165;
        int armHeight = 165;
        int walkBobX = (moveForward || moveBackward) ? (int) (Math.sin(menuAnimTick * 6.0) * 6) : 0;
        int walkBobY = (moveForward || moveBackward) ? (int) (Math.abs(Math.cos(menuAnimTick * 6.0)) * 5) : 0;

        if (isPunching) {
            int punchAdvance = (punchFrame <= 5) ? (punchFrame * 14) : ((10 - punchFrame) * 14);
            int armX = (screenWidth / 2) - (armWidth / 2);
            int armY = screenHeight - 48 - armHeight - punchAdvance;
            g2d.drawImage(texPunch, armX, armY, armWidth, armHeight, null);
        } else {
            int armX = (screenWidth / 2) - (armWidth / 2) + 55 + walkBobX;
            int armY = screenHeight - 44 - armHeight + walkBobY;
            g2d.drawImage(texHandOpen, armX, armY, armWidth, armHeight, null);
        }

        GradientPaint hudGrad = new GradientPaint(0, screenHeight - 52, new Color(25, 30, 40), 0, screenHeight, new Color(12, 15, 22));
        g2d.setPaint(hudGrad);
        g2d.fillRect(0, screenHeight - 52, screenWidth, 52);

        g2d.setColor(new Color(0, 200, 255));
        g2d.drawLine(0, screenHeight - 52, screenWidth, screenHeight - 52);
        g2d.setColor(new Color(60, 75, 95));
        g2d.drawRect(6, screenHeight - 46, screenWidth - 12, 40);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Courier New", Font.BOLD, 15));
        String displayUser = playerName.isEmpty() ? "ALUNO" : playerName.toUpperCase();
        g2d.drawString("ALUNO: " + displayUser, 18, screenHeight - 21);

        g2d.setColor(new Color(170, 215, 255));
        g2d.setFont(new Font("Courier New", Font.PLAIN, 12));
        g2d.drawString("[WASD] Mover  [E/Clique Dir] Interagir  [F11] Tela Cheia", screenWidth / 2 - 205, screenHeight - 21);

        boolean ggufReady = ProfessorAI.isGGUFServerReady();
        boolean isNativeLight = ProfessorAI.getSelectedModelName().equals(ProfessorAI.NATIVE_LIGHT_MODEL);
        g2d.setColor((ggufReady || isNativeLight) ? new Color(0, 255, 130) : new Color(255, 200, 50));
        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        String badge = "IA: " + ProfessorAI.getShortModelBadge();
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(badge, screenWidth - fm.stringWidth(badge) - 18, screenHeight - 21);
    }

    private void callProfessorAI(String message) {
        loadingAI = true;
        chatScrollOffset = 0;
        professorResponse = "Professor Diego está preparando a explicação... ▌";
        ProfessorAI.ask(playerName, message, new ProfessorAI.AICallback() {
            @Override
            public void onPartialResponse(String partialResponse) {
                professorResponse = partialResponse;
            }

            @Override
            public void onResponse(String response) {
                professorResponse = response;
                loadingAI = false;
            }

            @Override
            public void onError(String error) {
                professorResponse = error;
                chatScrollOffset = 0;
                loadingAI = false;
            }
        });
    }

    private List<String> wrapChatLines(String text, FontMetrics fm, int maxLineWidth) {
        List<String> wrapped = new ArrayList<>();
        if (text == null || text.isEmpty()) return wrapped;

        String[] paragraphs = text.split("\n");
        for (String para : paragraphs) {
            String trimmedPara = para.trim();
            if (trimmedPara.isEmpty()) {
                wrapped.add("");
                continue;
            }
            String[] words = trimmedPara.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String testLine = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(testLine) > maxLineWidth && line.length() > 0) {
                    wrapped.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(testLine);
                }
            }
            if (line.length() > 0) {
                wrapped.add(line.toString());
            }
        }
        return wrapped;
    }

    private void renderNPCChatWindow(Graphics2D g2d) {
        int boxX = 40;
        int boxH = Math.min(310, Math.max(255, screenHeight / 2 - 20));
        int boxY = screenHeight - 60 - boxH;
        int boxW = screenWidth - 80;

        GradientPaint chatBg = new GradientPaint(boxX, boxY, new Color(10, 18, 30, 245), boxX, boxY + boxH, new Color(5, 10, 18, 250));
        g2d.setPaint(chatBg);
        g2d.fillRoundRect(boxX, boxY, boxW, boxH, 14, 14);

        g2d.setColor(new Color(0, 230, 140));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(boxX, boxY, boxW, boxH, 14, 14);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setFont(new Font("Courier New", Font.BOLD, 14));
        g2d.drawString("PROFESSOR DIEGO (TI)", boxX + 20, boxY + 24);

        g2d.setColor(new Color(40, 65, 90));
        g2d.drawLine(boxX + 16, boxY + 32, boxX + boxW - 16, boxY + 32);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        FontMetrics fm = g2d.getFontMetrics();
        int maxLineWidth = boxW - 56;

        List<String> wrappedLines = wrapChatLines(professorResponse, fm, maxLineWidth);
        int textTopY = boxY + 52;
        int lineHeight = 20;
        int availableHeight = boxH - 122;
        int maxVisibleLines = Math.max(4, availableHeight / lineHeight);

        int maxScroll = Math.max(0, wrappedLines.size() - maxVisibleLines);
        if (chatScrollOffset > maxScroll) chatScrollOffset = maxScroll;
        if (chatScrollOffset < 0) chatScrollOffset = 0;

        int endIdx = Math.min(wrappedLines.size(), chatScrollOffset + maxVisibleLines);
        int yOffset = textTopY;
        for (int i = chatScrollOffset; i < endIdx; i++) {
            g2d.drawString(wrappedLines.get(i), boxX + 20, yOffset);
            yOffset += lineHeight;
        }

        // Barra de rolagem lateral quando a explicação do Professor Diego for longa
        if (maxScroll > 0) {
            int trackX = boxX + boxW - 22;
            int trackY = boxY + 38;
            int trackH = availableHeight;
            g2d.setColor(new Color(22, 34, 50));
            g2d.fillRoundRect(trackX, trackY, 8, trackH, 4, 4);

            int thumbH = Math.max(20, (int) ((double) maxVisibleLines / wrappedLines.size() * trackH));
            int thumbY = trackY + (int) ((double) chatScrollOffset / maxScroll * (trackH - thumbH));
            g2d.setColor(new Color(0, 230, 140));
            g2d.fillRoundRect(trackX, thumbY, 8, thumbH, 4, 4);

            g2d.setFont(new Font("Arial", Font.BOLD, 11));
            g2d.setColor(new Color(0, 255, 180));
            String scrollHint = "Role com Setas Cima/Baixo ou Mouse (" + (chatScrollOffset + 1) + "-" + endIdx + " de " + wrappedLines.size() + " linhas)";
            FontMetrics scrollFm = g2d.getFontMetrics();
            g2d.drawString(scrollHint, boxX + boxW - scrollFm.stringWidth(scrollHint) - 18, boxY + 24);
        }

        g2d.setColor(new Color(22, 30, 44));
        g2d.fillRoundRect(boxX + 18, boxY + boxH - 64, boxW - 36, 32, 8, 8);
        g2d.setColor(new Color(0, 180, 255));
        g2d.drawRoundRect(boxX + 18, boxY + boxH - 64, boxW - 36, 32, 8, 8);

        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("Arial", Font.BOLD, 13));
        String cursor = ((System.currentTimeMillis() / 400) % 2 == 0 && !loadingAI) ? "_" : "";
        g2d.drawString("Pergunta ao Prof. Diego: " + playerChatInput + cursor, boxX + 28, boxY + boxH - 43);

        g2d.setColor(new Color(170, 190, 210));
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        g2d.drawString("[ENTER] Enviar pergunta   |   [Setas Cima/Baixo ou Scroll] Rolar explicação   |   [ESC] Voltar",
                boxX + 20, boxY + boxH - 12);
    }

    private void drawMenuBackdrop(Graphics2D g2d) {
        renderRaycasting3D(g2d);

        GradientPaint darkOverlay = new GradientPaint(
                0, 0, new Color(6, 10, 18, 215),
                0, screenHeight, new Color(16, 8, 20, 235));
        g2d.setPaint(darkOverlay);
        g2d.fillRect(0, 0, screenWidth, screenHeight);

        g2d.setColor(new Color(0, 190, 255, 25));
        int gridStep = 40;
        int offset = (int) (menuAnimTick * 12) % gridStep;
        for (int x = offset; x < screenWidth; x += gridStep) {
            g2d.drawLine(x, 0, x, screenHeight);
        }
        for (int y = offset; y < screenHeight; y += gridStep) {
            g2d.drawLine(0, y, screenWidth, y);
        }

        g2d.setColor(new Color(0, 0, 0, 45));
        for (int y = 0; y < screenHeight; y += 4) {
            g2d.drawLine(0, y, screenWidth, y);
        }
    }

    private void drawMainMenu(Graphics2D g2d) {
        drawMenuBackdrop(g2d);

        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int cardW = 540;
        int cardH = 440;
        int cardX = cx - cardW / 2;
        int cardY = cy - cardH / 2 - 5;

        g2d.setColor(new Color(12, 16, 26, 230));
        g2d.fillRoundRect(cardX, cardY, cardW, cardH, 20, 20);
        g2d.setColor(new Color(0, 195, 255, 150));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(cardX, cardY, cardW, cardH, 20, 20);
        g2d.setStroke(new BasicStroke(1f));

        GradientPaint doomBanner = new GradientPaint(cardX + 20, cardY + 20, new Color(200, 30, 30), cardX + cardW - 20, cardY + 20, new Color(255, 120, 0));
        g2d.setPaint(doomBanner);
        g2d.fillRoundRect(cardX + 24, cardY + 22, cardW - 48, 6, 4, 4);

        g2d.setFont(new Font("Courier New", Font.BOLD, 42));
        g2d.setColor(new Color(90, 0, 0));
        drawCenteredString(g2d, "RAYCASTING-TI", cx + 3, cardY + 76);
        g2d.setColor(new Color(255, 65, 55));
        drawCenteredString(g2d, "RAYCASTING-TI", cx, cardY + 73);

        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        g2d.setColor(new Color(0, 220, 255));
        drawCenteredString(g2d, "RAYCASTING ENGINE  //  SELETOR GGUF & IA LOCAL", cx, cardY + 100);

        String[] options = { "INICIAR LABORATÓRIO", "OPÇÕES DE VÍDEO E IA", "SAIR DO SISTEMA" };
        int btnW = 360;
        int btnH = 48;
        int firstBtnY = cardY + 135;

        for (int i = 0; i < options.length; i++) {
            int btnX = cx - btnW / 2;
            int btnY = firstBtnY + i * 64;
            boolean selected = (i == menuSelection);

            if (selected) {
                GradientPaint selGrad = new GradientPaint(btnX, btnY, new Color(220, 45, 35, 225), btnX + btnW, btnY, new Color(140, 20, 20, 225));
                g2d.setPaint(selGrad);
                g2d.fillRoundRect(btnX, btnY, btnW, btnH, 12, 12);

                g2d.setColor(new Color(255, 215, 60));
                g2d.setStroke(new BasicStroke(2f));
                g2d.drawRoundRect(btnX, btnY, btnW, btnH, 12, 12);
                g2d.setStroke(new BasicStroke(1f));
                g2d.fillRoundRect(btnX + 8, btnY + 10, 6, btnH - 20, 4, 4);
            } else {
                g2d.setColor(new Color(24, 32, 48, 205));
                g2d.fillRoundRect(btnX, btnY, btnW, btnH, 12, 12);
                g2d.setColor(new Color(65, 85, 115));
                g2d.drawRoundRect(btnX, btnY, btnW, btnH, 12, 12);
            }

            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            g2d.setColor(selected ? Color.YELLOW : new Color(220, 230, 245));
            String label = selected ? ">>  " + options[i] + "  <<" : options[i];
            drawCenteredString(g2d, label, cx, btnY + 31);
        }

        int statusY = cardY + cardH - 78;
        g2d.setColor(new Color(18, 26, 38));
        g2d.fillRoundRect(cardX + 24, statusY, cardW - 48, 56, 10, 10);
        g2d.setColor(new Color(45, 65, 90));
        g2d.drawRoundRect(cardX + 24, statusY, cardW - 48, 56, 10, 10);

        boolean ready = ProfessorAI.isGGUFServerReady() || ProfessorAI.getSelectedModelName().equals(ProfessorAI.NATIVE_LIGHT_MODEL);
        g2d.setColor(ready ? new Color(0, 255, 120) : new Color(255, 190, 40));
        g2d.fillOval(cardX + 38, statusY + 15, 10, 10);

        g2d.setFont(new Font("Courier New", Font.BOLD, 12));
        g2d.setColor(Color.WHITE);
        g2d.drawString("MODELO ATIVO: " + ProfessorAI.getSelectedModelName(), cardX + 56, statusY + 24);

        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        g2d.setColor(new Color(165, 190, 215));
        g2d.drawString("Status: " + ProfessorAI.getEngineStatusText(), cardX + 56, statusY + 43);
    }

    private void drawNameInput(Graphics2D g2d) {
        drawMenuBackdrop(g2d);

        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int cardW = 500;
        int cardH = 260;
        int cardX = cx - cardW / 2;
        int cardY = cy - cardH / 2;

        g2d.setColor(new Color(12, 18, 30, 235));
        g2d.fillRoundRect(cardX, cardY, cardW, cardH, 18, 18);
        g2d.setColor(new Color(0, 200, 255));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(cardX, cardY, cardW, cardH, 18, 18);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setColor(new Color(0, 220, 255));
        g2d.setFont(new Font("Courier New", Font.BOLD, 22));
        drawCenteredString(g2d, "CREDENCIAL DE ACESSO AO LAB-TI", cx, cardY + 48);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        drawCenteredString(g2d, "Digite seu nome de aluno (Até 12 caracteres):", cx, cardY + 90);

        g2d.setColor(new Color(22, 30, 46));
        g2d.fillRoundRect(cx - 160, cardY + 110, 320, 48, 10, 10);
        g2d.setColor(Color.YELLOW);
        g2d.drawRoundRect(cx - 160, cardY + 110, 320, 48, 10, 10);

        g2d.setFont(new Font("Courier New", Font.BOLD, 24));
        String cursor = ((System.currentTimeMillis() / 400) % 2 == 0) ? "_" : " ";
        drawCenteredString(g2d, playerName + cursor, cx, cardY + 142);

        g2d.setColor(new Color(170, 195, 220));
        g2d.setFont(new Font("Arial", Font.PLAIN, 13));
        drawCenteredString(g2d, "[ENTER] Confirmar e Entrar   |   [ESC] Voltar ao Menu", cx, cardY + 215);
    }

    /**
     * Tela de Opções de Vídeo (Tela Cheia, Resolução, FOV) e Seletor de Modelos GGUF da pasta gguf/.
     */
    private void drawOptions(Graphics2D g2d) {
        drawMenuBackdrop(g2d);

        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int cardW = 680;
        int cardH = 490;
        int cardX = cx - cardW / 2;
        int cardY = cy - cardH / 2;

        g2d.setColor(new Color(12, 18, 30, 240));
        g2d.fillRoundRect(cardX, cardY, cardW, cardH, 20, 20);
        g2d.setColor(new Color(0, 200, 255));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(cardX, cardY, cardW, cardH, 20, 20);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setColor(new Color(0, 225, 255));
        g2d.setFont(new Font("Courier New", Font.BOLD, 24));
        drawCenteredString(g2d, "OPÇÕES DE VÍDEO E MODELO DE IA LOCAL", cx, cardY + 42);

        String[] rowTitles = {
                "Modo de Exibição:",
                "Resolução da Janela:",
                "Campo de Visão (FOV):",
                "Modelo IA (pasta gguf/):",
                "Escanear Pasta gguf/:",
                "Retornar:"
        };

        String[] rowValues = {
                isFullScreen ? "<<  TELA CHEIA (FULLSCREEN)  >>" : "<<  MODO JANELA  >>",
                isFullScreen ? "Automático (Nativo do Monitor)" : "<<  " + resolutionLabels[selectedResIndex] + "  >>",
                "<<  " + fovLabels[selectedFovIndex] + "  >>",
                "<<  " + ProfessorAI.getSelectedModelName() + "  >>",
                "[ ATUALIZAR LISTA DE MODELOS .GGUF ]",
                "[ VOLTAR AO MENU PRINCIPAL ]"
        };

        int startY = cardY + 75;
        int rowH = 48;
        int rowSpacing = 56;

        for (int i = 0; i < rowTitles.length; i++) {
            int ry = startY + i * rowSpacing;
            boolean sel = (optionsSelection == i);

            if (sel) {
                GradientPaint rowGrad = new GradientPaint(cardX + 24, ry, new Color(210, 45, 35, 200), cardX + cardW - 24, ry, new Color(110, 20, 25, 200));
                g2d.setPaint(rowGrad);
                g2d.fillRoundRect(cardX + 24, ry, cardW - 48, rowH, 10, 10);
                g2d.setColor(new Color(255, 215, 60));
                g2d.drawRoundRect(cardX + 24, ry, cardW - 48, rowH, 10, 10);
            } else {
                g2d.setColor(new Color(20, 28, 44, 210));
                g2d.fillRoundRect(cardX + 24, ry, cardW - 48, rowH, 10, 10);
                g2d.setColor(new Color(55, 75, 105));
                g2d.drawRoundRect(cardX + 24, ry, cardW - 48, rowH, 10, 10);
            }

            g2d.setFont(new Font("Arial", Font.BOLD, 15));
            g2d.setColor(sel ? Color.YELLOW : new Color(195, 215, 235));
            g2d.drawString(rowTitles[i], cardX + 40, ry + 30);

            g2d.setFont(new Font("Courier New", Font.BOLD, 14));
            g2d.setColor(sel ? Color.WHITE : new Color(0, 230, 180));
            FontMetrics fm = g2d.getFontMetrics();
            int valX = cardX + cardW - 40 - fm.stringWidth(rowValues[i]);
            g2d.drawString(rowValues[i], Math.max(cardX + 245, valX), ry + 30);
        }

        // Barra informativa na parte inferior do painel de opções
        int infoY = cardY + cardH - 68;
        g2d.setColor(new Color(16, 24, 36));
        g2d.fillRoundRect(cardX + 24, infoY, cardW - 48, 50, 8, 8);
        g2d.setColor(new Color(45, 65, 90));
        g2d.drawRoundRect(cardX + 24, infoY, cardW - 48, 50, 8, 8);

        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.setColor(new Color(0, 255, 140));
        g2d.drawString("Status IA: " + ProfessorAI.getEngineStatusText(), cardX + 38, infoY + 20);

        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        g2d.setColor(new Color(175, 195, 215));
        g2d.drawString("Dica: Coloque qualquer arquivo .gguf na pasta 'gguf/' ou escolha 'IA Leve Nativa (0 MB RAM)' se o PC pesar!", cardX + 38, infoY + 39);
    }

    private void drawPauseMenu(Graphics2D g2d) {
        g2d.setColor(new Color(5, 10, 18, 195));
        g2d.fillRect(0, 0, screenWidth, screenHeight);

        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int cardW = 420;
        int cardH = 260;
        int cardX = cx - cardW / 2;
        int cardY = cy - cardH / 2;

        g2d.setColor(new Color(14, 20, 34, 240));
        g2d.fillRoundRect(cardX, cardY, cardW, cardH, 18, 18);
        g2d.setColor(new Color(255, 80, 60));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(cardX, cardY, cardW, cardH, 18, 18);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Courier New", Font.BOLD, 30));
        drawCenteredString(g2d, "JOGO PAUSADO", cx, cardY + 55);

        String[] pOptions = { "CONTINUAR NO LAB", "SAIR PARA O MENU" };
        int btnW = 280;
        int btnH = 44;
        for (int i = 0; i < pOptions.length; i++) {
            int btnX = cx - btnW / 2;
            int btnY = cardY + 95 + (i * 60);
            boolean sel = (i == pauseSelection);
            if (sel) {
                g2d.setColor(new Color(200, 40, 35, 220));
                g2d.fillRoundRect(btnX, btnY, btnW, btnH, 10, 10);
                g2d.setColor(Color.YELLOW);
                g2d.drawRoundRect(btnX, btnY, btnW, btnH, 10, 10);
            } else {
                g2d.setColor(new Color(26, 36, 52));
                g2d.fillRoundRect(btnX, btnY, btnW, btnH, 10, 10);
                g2d.setColor(new Color(75, 95, 125));
                g2d.drawRoundRect(btnX, btnY, btnW, btnH, 10, 10);
            }
            g2d.setColor(sel ? Color.YELLOW : Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            drawCenteredString(g2d, (sel ? ">> " : "") + pOptions[i] + (sel ? " <<" : ""), cx, btnY + 29);
        }
    }

    private void drawCenteredString(Graphics2D g2d, String text, int centerX, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        int x = centerX - (fm.stringWidth(text) / 2);
        g2d.drawString(text, x, y);
    }

    private void tryInteractWithNearbyEntity() {
        Entity closest = null;
        double minDist = 1.9;
        for (Entity ent : entities) {
            double dist = Math.hypot(posX - ent.x, posY - ent.y);
            if (dist < minDist) {
                minDist = dist;
                closest = ent;
            }
        }
        if (closest != null) {
            resetMovementFlags();
            if (closest.type == 3) {
                currentState = State.NPC_CHAT;
            } else if (closest.type == 5) {
                virtualTerminal.updatePlayerName(playerName);
                currentState = State.CMD_VIRTUAL;
            }
        }
    }

    private void handleOptionChange(int row, int dir) {
        if (row == 0) { // Modo Tela Cheia vs Janela
            isFullScreen = !isFullScreen;
            applyVideoSettings(true);
        } else if (row == 1) { // Resolução
            if (!isFullScreen) {
                int count = resolutionLabels.length;
                selectedResIndex = (selectedResIndex + dir + count) % count;
                applyVideoSettings(false);
            }
        } else if (row == 2) { // FOV
            int count = fovLabels.length;
            selectedFovIndex = (selectedFovIndex + dir + count) % count;
            applyFovSetting();
        } else if (row == 3) { // Modelo IA (pasta gguf/)
            ProfessorAI.cycleModel(dir);
        } else if (row == 4) { // Recarregar pasta gguf/
            ProfessorAI.refreshAvailableModels();
        } else if (row == 5) { // Voltar ao menu
            currentState = State.MENU;
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        int my = e.getY();
        int cy = screenHeight / 2;
        if (currentState == State.MENU) {
            int cardY = cy - 440 / 2 - 5;
            int firstBtnY = cardY + 135;
            for (int i = 0; i < 3; i++) {
                int btnY = firstBtnY + i * 64;
                if (my >= btnY && my <= btnY + 48) {
                    menuSelection = i;
                }
            }
        } else if (currentState == State.PAUSED) {
            int cardY = cy - 260 / 2;
            for (int i = 0; i < 2; i++) {
                int btnY = cardY + 95 + (i * 60);
                if (my >= btnY && my <= btnY + 44) {
                    pauseSelection = i;
                }
            }
        } else if (currentState == State.OPTIONS) {
            int cardY = cy - 490 / 2;
            int startY = cardY + 75;
            for (int i = 0; i < 6; i++) {
                int ry = startY + i * 56;
                if (my >= ry && my <= ry + 48) {
                    optionsSelection = i;
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (SwingUtilities.isLeftMouseButton(e)) {
            if (currentState == State.MENU) {
                if (menuSelection == 0) currentState = State.NAME_INPUT;
                else if (menuSelection == 1) {
                    ProfessorAI.refreshAvailableModels();
                    currentState = State.OPTIONS;
                } else if (menuSelection == 2) exitGameCleanly();
            } else if (currentState == State.PAUSED) {
                if (pauseSelection == 0) currentState = State.PLAYING;
                else {
                    resetMovementFlags();
                    currentState = State.MENU;
                }
            } else if (currentState == State.OPTIONS) {
                handleOptionChange(optionsSelection, 1);
            } else if (currentState == State.PLAYING && !isPunching) {
                isPunching = true;
                punchFrame = 0;
            }
        } else if (SwingUtilities.isRightMouseButton(e)) {
            if (currentState == State.OPTIONS) {
                handleOptionChange(optionsSelection, -1);
            } else if (currentState == State.PLAYING) {
                tryInteractWithNearbyEntity();
            }
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (currentState == State.CMD_VIRTUAL) {
            virtualTerminal.handleMouseWheel(e.getWheelRotation());
        } else if (currentState == State.NPC_CHAT) {
            chatScrollOffset = Math.max(0, chatScrollOffset + (e.getWheelRotation() * 2));
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        // Atalho global F11 para alternar Tela Cheia em qualquer momento do jogo
        if (code == KeyEvent.VK_F11) {
            isFullScreen = !isFullScreen;
            applyVideoSettings(true);
            return;
        }

        if (currentState == State.MENU) {
            if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) menuSelection = (menuSelection - 1 + 3) % 3;
            if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) menuSelection = (menuSelection + 1) % 3;
            if (code == KeyEvent.VK_ENTER) {
                if (menuSelection == 0) currentState = State.NAME_INPUT;
                else if (menuSelection == 1) {
                    ProfessorAI.refreshAvailableModels();
                    currentState = State.OPTIONS;
                } else if (menuSelection == 2) exitGameCleanly();
            }
        } else if (currentState == State.NAME_INPUT) {
            if (code == KeyEvent.VK_ESCAPE) {
                currentState = State.MENU;
            } else if (code == KeyEvent.VK_ENTER && !playerName.trim().isEmpty()) {
                virtualTerminal.updatePlayerName(playerName);
                currentState = State.PLAYING;
            } else if (code == KeyEvent.VK_BACK_SPACE && playerName.length() > 0) {
                playerName = playerName.substring(0, playerName.length() - 1);
            } else {
                char c = e.getKeyChar();
                if (playerName.length() < 12 && !Character.isISOControl(c) && (Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                    playerName += c;
                }
            }
        } else if (currentState == State.OPTIONS) {
            if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) optionsSelection = (optionsSelection - 1 + 6) % 6;
            else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) optionsSelection = (optionsSelection + 1) % 6;
            else if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) handleOptionChange(optionsSelection, -1);
            else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) handleOptionChange(optionsSelection, 1);
            else if (code == KeyEvent.VK_ENTER) handleOptionChange(optionsSelection, 1);
            else if (code == KeyEvent.VK_ESCAPE) currentState = State.MENU;
        } else if (currentState == State.PAUSED) {
            if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) pauseSelection = (pauseSelection - 1 + 2) % 2;
            if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) pauseSelection = (pauseSelection + 1) % 2;
            if (code == KeyEvent.VK_ENTER) {
                if (pauseSelection == 0) currentState = State.PLAYING;
                else {
                    resetMovementFlags();
                    currentState = State.MENU;
                }
            } else if (code == KeyEvent.VK_ESCAPE) {
                currentState = State.PLAYING;
            }
        } else if (currentState == State.PLAYING) {
            if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) moveForward = true;
            if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) moveBackward = true;
            if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) rotateLeft = true;
            if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) rotateRight = true;
            if (code == KeyEvent.VK_SPACE && !isPunching) {
                isPunching = true;
                punchFrame = 0;
            }
            if (code == KeyEvent.VK_E) {
                tryInteractWithNearbyEntity();
            }
            if (code == KeyEvent.VK_ESCAPE) {
                resetMovementFlags();
                currentState = State.PAUSED;
            }
        } else if (currentState == State.NPC_CHAT) {
            if (code == KeyEvent.VK_ESCAPE) {
                resetMovementFlags();
                currentState = State.PLAYING;
            } else if (code == KeyEvent.VK_UP) {
                chatScrollOffset = Math.max(0, chatScrollOffset - 1);
            } else if (code == KeyEvent.VK_DOWN) {
                chatScrollOffset++;
            } else if (code == KeyEvent.VK_PAGE_UP) {
                chatScrollOffset = Math.max(0, chatScrollOffset - 5);
            } else if (code == KeyEvent.VK_PAGE_DOWN) {
                chatScrollOffset += 5;
            } else if (code == KeyEvent.VK_ENTER) {
                if (!playerChatInput.trim().isEmpty() && !loadingAI) {
                    callProfessorAI(playerChatInput);
                    playerChatInput = "";
                }
            } else if (code == KeyEvent.VK_BACK_SPACE && playerChatInput.length() > 0) {
                playerChatInput = playerChatInput.substring(0, playerChatInput.length() - 1);
            } else {
                char c = e.getKeyChar();
                if (playerChatInput.length() < 110 && !Character.isISOControl(c) && c != KeyEvent.CHAR_UNDEFINED) {
                    playerChatInput += c;
                }
            }
        } else if (currentState == State.CMD_VIRTUAL) {
            virtualTerminal.handleKeyPressed(e);
        } else if (currentState == State.EASTER_EGG) {
            if (code == KeyEvent.VK_ESCAPE) {
                isPunching = false;
                currentState = State.CMD_VIRTUAL;
                return;
            }
            arcadeGames.keyPressed(code);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) moveForward = false;
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) moveBackward = false;
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) rotateLeft = false;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) rotateRight = false;
        if (currentState == State.EASTER_EGG) {
            arcadeGames.keyReleased(code);
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) {}
}
