package raycastingti;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Motor completo dos Mini-Games Arcade executáveis pelo Terminal CMD (pasta Downloads).
 *
 * Jogos incluídos e 100% funcionais:
 * 1. pacman.exe - Pac-Man clássico com labirinto real, pílulas, power pellets e 4 fantasmas coloridos.
 * 2. tetris.exe - Tetris real em grade 10x18 com as 7 peças oficiais, rotação, ghost piece, next piece e limpeza de linhas.
 * 3. doom.exe   - DOOM 2D Arena Shooter com ondas de demônios, projéteis, barra de vida e partículas.
 * 4. dino.exe   - Chrome Dino Runner completo com pulo, agachamento, cactos variados, pterodáctilos e ciclo dia/noite.
 * 5. snake.exe  - Jogo da Cobrinha clássico em grade com crescimento, maçãs douradas e velocidade progressiva.
 * 6. matrix.exe - Simulador interativo de chuva digital Matrix.
 */
public class ArcadeGames {

    public enum GameType { PACMAN, TETRIS, DOOM2D, DINO, SNAKE, MATRIX }

    private GameType activeGame = GameType.PACMAN;
    private final Random rand = new Random();
    private boolean gameOver = false;
    private boolean victory = false;
    private int score = 0;
    private final int[] highScores = new int[GameType.values().length];
    private int tickCount = 0;

    // =========================================================================
    // 1. ESTADO DO PAC-MAN (Labirinto 19x11)
    // =========================================================================
    private static final int PAC_COLS = 19;
    private static final int PAC_ROWS = 11;
    // 1 = Parede, 0 = Pílula comum, 2 = Power Pellet, 3 = Vazio
    private static final int[][] PAC_TEMPLATE = {
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
        {1,2,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,2,1},
        {1,0,1,1,0,1,1,1,0,1,0,1,1,1,0,1,1,0,1},
        {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
        {1,0,1,1,0,1,0,1,1,3,1,1,0,1,0,1,1,0,1},
        {1,0,0,0,0,1,0,1,3,3,3,1,0,1,0,0,0,0,1},
        {1,0,1,1,0,1,0,1,1,1,1,1,0,1,0,1,1,0,1},
        {1,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,1},
        {1,0,1,1,0,1,1,1,0,1,0,1,1,1,0,1,1,0,1},
        {1,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,1},
        {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
    };
    private final int[][] pacGrid = new int[PAC_ROWS][PAC_COLS];
    private double pacX, pacY;
    private int pacDirX, pacDirY, pacNextDirX, pacNextDirY;
    private int pacLives = 3;
    private int pacPelletsRemaining = 0;
    private int pacPowerTimer = 0;
    private final double[] ghostX = new double[4];
    private final double[] ghostY = new double[4];
    private final int[] ghostDX = new int[4];
    private final int[] ghostDY = new int[4];
    private final Color[] ghostColors = {
        new Color(255, 50, 50),   // Blinky
        new Color(255, 150, 210), // Pinky
        new Color(0, 230, 255),   // Inky
        new Color(255, 165, 40)   // Clyde
    };

    // =========================================================================
    // 2. ESTADO DO TETRIS (Grade 10x18)
    // =========================================================================
    private static final int TET_COLS = 10;
    private static final int TET_ROWS = 18;
    private final int[][] tetBoard = new int[TET_ROWS][TET_COLS];
    private int tetCurPiece, tetNextPiece, tetRotation, tetX, tetY;
    private int tetDropTimer = 0;
    private int tetLinesCleared = 0;
    private static final int[][][][] TET_SHAPES = {
        // I
        { {{0,1},{1,1},{2,1},{3,1}}, {{2,0},{2,1},{2,2},{2,3}}, {{0,2},{1,2},{2,2},{3,2}}, {{1,0},{1,1},{1,2},{1,3}} },
        // O
        { {{1,0},{2,0},{1,1},{2,1}}, {{1,0},{2,0},{1,1},{2,1}}, {{1,0},{2,0},{1,1},{2,1}}, {{1,0},{2,0},{1,1},{2,1}} },
        // T
        { {{1,0},{0,1},{1,1},{2,1}}, {{1,0},{1,1},{2,1},{1,2}}, {{0,1},{1,1},{2,1},{1,2}}, {{1,0},{0,1},{1,1},{1,2}} },
        // S
        { {{1,0},{2,0},{0,1},{1,1}}, {{1,0},{1,1},{2,1},{2,2}}, {{1,1},{2,1},{0,2},{1,2}}, {{0,0},{0,1},{1,1},{1,2}} },
        // Z
        { {{0,0},{1,0},{1,1},{2,1}}, {{2,0},{1,1},{2,1},{1,2}}, {{0,1},{1,1},{1,2},{2,2}}, {{1,0},{0,1},{1,1},{0,2}} },
        // J
        { {{0,0},{0,1},{1,1},{2,1}}, {{1,0},{2,0},{1,1},{1,2}}, {{0,1},{1,1},{2,1},{2,2}}, {{1,0},{1,1},{0,2},{1,2}} },
        // L
        { {{2,0},{0,1},{1,1},{2,1}}, {{1,0},{1,1},{1,2},{2,2}}, {{0,1},{1,1},{2,1},{0,2}}, {{0,0},{1,0},{1,1},{1,2}} }
    };
    private static final Color[] TET_COLORS = {
        Color.BLACK,
        new Color(0, 240, 240),   // I - Ciano
        new Color(240, 240, 0),   // O - Amarelo
        new Color(170, 50, 255),  // T - Roxo
        new Color(50, 230, 80),   // S - Verde
        new Color(255, 60, 60),   // Z - Vermelho
        new Color(60, 100, 255),  // J - Azul
        new Color(255, 160, 30)   // L - Laranja
    };

    // =========================================================================
    // 3. ESTADO DO DOOM 2D ARENA SHOOTER
    // =========================================================================
    private static class DoomEnemy {
        double x, y, vx, vy;
        int hp, maxHp, type; // 0 = Imp, 1 = Pinky Demon, 2 = Cacodemon
        int shootCooldown;
    }
    private static class DoomBullet {
        double x, y, vx, vy;
        boolean fromPlayer;
        Color color;
    }
    private static class DoomParticle {
        double x, y, vx, vy;
        int life;
        Color color;
    }
    private double doomPlayerX;
    private int doomPlayerHP = 100;
    private int doomWave = 1;
    private int doomShootCooldown = 0;
    private boolean doomMoveLeft, doomMoveRight, doomShooting;
    private final List<DoomEnemy> doomEnemies = new ArrayList<>();
    private final List<DoomBullet> doomBullets = new ArrayList<>();
    private final List<DoomParticle> doomParticles = new ArrayList<>();

    // =========================================================================
    // 4. ESTADO DO GOOGLE DINO RUNNER
    // =========================================================================
    private static class DinoObstacle {
        double x;
        int yOffset; // 0 = chão, >0 = aéreo (Pterodáctilo)
        int width, height;
        int type; // 0 = cacto simples, 1 = cacto triplo, 2 = pterodáctilo
    }
    private double dinoY, dinoVelY;
    private boolean dinoJumping, dinoDucking;
    private final List<DinoObstacle> dinoObstacles = new ArrayList<>();
    private int dinoSpawnTimer = 0;
    private double dinoSpeed = 6.5;

    // =========================================================================
    // 5. ESTADO DO SNAKE (Jogo da Cobrinha)
    // =========================================================================
    private static final int SNAKE_COLS = 26;
    private static final int SNAKE_ROWS = 15;
    private final List<Point> snakeBody = new ArrayList<>();
    private int snakeDX = 1, snakeDY = 0, snakeNextDX = 1, snakeNextDY = 0;
    private Point snakeFood = new Point(10, 7);
    private boolean snakeGoldenFood = false;

    // =========================================================================
    // 6. ESTADO DO MATRIX DIGITAL RAIN
    // =========================================================================
    private final int[] matrixDrops = new int[40];
    private final int[] matrixSpeeds = new int[40];

    public GameType getActiveGame() {
        return activeGame;
    }

    public void startGame(GameType type) {
        this.activeGame = type;
        this.score = 0;
        this.gameOver = false;
        this.victory = false;
        this.tickCount = 0;

        switch (type) {
            case PACMAN:
                initPacman(true);
                break;
            case TETRIS:
                initTetris();
                break;
            case DOOM2D:
                initDoom2D();
                break;
            case DINO:
                initDino();
                break;
            case SNAKE:
                initSnake();
                break;
            case MATRIX:
                initMatrix();
                break;
        }
    }

    private void initPacman(boolean resetLives) {
        if (resetLives) {
            pacLives = 3;
            pacPelletsRemaining = 0;
            for (int r = 0; r < PAC_ROWS; r++) {
                for (int c = 0; c < PAC_COLS; c++) {
                    pacGrid[r][c] = PAC_TEMPLATE[r][c];
                    if (pacGrid[r][c] == 0 || pacGrid[r][c] == 2) {
                        pacPelletsRemaining++;
                    }
                }
            }
        }
        pacX = 9.0;
        pacY = 7.0;
        pacDirX = 0;
        pacDirY = 0;
        pacNextDirX = -1;
        pacNextDirY = 0;
        pacPowerTimer = 0;

        double[][] starts = { {9, 3}, {8, 5}, {9, 5}, {10, 5} };
        int[][] dirs = { {1, 0}, {0, -1}, {0, -1}, {-1, 0} };
        for (int i = 0; i < 4; i++) {
            ghostX[i] = starts[i][0];
            ghostY[i] = starts[i][1];
            ghostDX[i] = dirs[i][0];
            ghostDY[i] = dirs[i][1];
        }
    }

    private void initTetris() {
        for (int r = 0; r < TET_ROWS; r++) {
            for (int c = 0; c < TET_COLS; c++) {
                tetBoard[r][c] = 0;
            }
        }
        tetLinesCleared = 0;
        tetNextPiece = rand.nextInt(7);
        spawnNextTetromino();
    }

    private void spawnNextTetromino() {
        tetCurPiece = tetNextPiece;
        tetNextPiece = rand.nextInt(7);
        tetRotation = 0;
        tetX = 3;
        tetY = 0;
        if (!canTetrominoFit(tetCurPiece, tetRotation, tetX, tetY)) {
            gameOver = true;
        }
    }

    private void initDoom2D() {
        doomPlayerX = 280;
        doomPlayerHP = 100;
        doomWave = 1;
        doomShootCooldown = 0;
        doomMoveLeft = false;
        doomMoveRight = false;
        doomShooting = false;
        doomEnemies.clear();
        doomBullets.clear();
        doomParticles.clear();
        spawnDoomWave();
    }

    private void spawnDoomWave() {
        int count = 3 + doomWave * 2;
        for (int i = 0; i < count; i++) {
            DoomEnemy en = new DoomEnemy();
            en.x = 40 + rand.nextInt(500);
            en.y = 55 + rand.nextInt(90);
            en.type = (doomWave >= 3 && i % 4 == 0) ? 2 : (i % 3 == 0 ? 1 : 0);
            en.maxHp = (en.type == 2) ? 45 : (en.type == 1 ? 30 : 20);
            en.hp = en.maxHp;
            en.vx = (rand.nextBoolean() ? 1 : -1) * (1.8 + rand.nextDouble() * 1.5);
            en.vy = (en.type == 1) ? 0.65 : 0.22;
            en.shootCooldown = 30 + rand.nextInt(60);
            doomEnemies.add(en);
        }
    }

    private void initDino() {
        dinoY = 0;
        dinoVelY = 0;
        dinoJumping = false;
        dinoDucking = false;
        dinoObstacles.clear();
        dinoSpawnTimer = 40;
        dinoSpeed = 6.5;
    }

    private void initSnake() {
        snakeBody.clear();
        snakeBody.add(new Point(6, 7));
        snakeBody.add(new Point(5, 7));
        snakeBody.add(new Point(4, 7));
        snakeDX = 1;
        snakeDY = 0;
        snakeNextDX = 1;
        snakeNextDY = 0;
        spawnSnakeFood();
    }

    private void spawnSnakeFood() {
        while (true) {
            int fx = rand.nextInt(SNAKE_COLS);
            int fy = rand.nextInt(SNAKE_ROWS);
            Point p = new Point(fx, fy);
            if (!snakeBody.contains(p)) {
                snakeFood = p;
                snakeGoldenFood = (rand.nextInt(5) == 0);
                break;
            }
        }
    }

    private void initMatrix() {
        for (int i = 0; i < matrixDrops.length; i++) {
            matrixDrops[i] = rand.nextInt(30);
            matrixSpeeds[i] = 1 + rand.nextInt(3);
        }
    }

    public void update() {
        tickCount++;
        if (gameOver || victory) return;

        switch (activeGame) {
            case PACMAN:
                updatePacman();
                break;
            case TETRIS:
                updateTetris();
                break;
            case DOOM2D:
                updateDoom2D();
                break;
            case DINO:
                updateDino();
                break;
            case SNAKE:
                updateSnake();
                break;
            case MATRIX:
                break;
        }

        int idx = activeGame.ordinal();
        if (score > highScores[idx]) {
            highScores[idx] = score;
        }
    }

    private void updatePacman() {
        if (pacPowerTimer > 0) pacPowerTimer--;

        double speed = 0.125;
        boolean nearCenter = Math.abs(pacX - Math.round(pacX)) < 0.13 && Math.abs(pacY - Math.round(pacY)) < 0.13;

        if (nearCenter) {
            int cx = (int) Math.round(pacX);
            int cy = (int) Math.round(pacY);

            if (pacGrid[cy][cx] == 0) {
                pacGrid[cy][cx] = 3;
                score += 10;
                pacPelletsRemaining--;
            } else if (pacGrid[cy][cx] == 2) {
                pacGrid[cy][cx] = 3;
                score += 50;
                pacPelletsRemaining--;
                pacPowerTimer = 320;
            }

            if (pacPelletsRemaining <= 0) {
                score += 500;
                initPacman(true);
                return;
            }

            if ((pacNextDirX != 0 || pacNextDirY != 0) && isPacWalkable(cx + pacNextDirX, cy + pacNextDirY, false)) {
                pacX = cx;
                pacY = cy;
                pacDirX = pacNextDirX;
                pacDirY = pacNextDirY;
            } else if (!isPacWalkable(cx + pacDirX, cy + pacDirY, false)) {
                pacX = cx;
                pacY = cy;
                pacDirX = 0;
                pacDirY = 0;
            }
        }

        pacX += pacDirX * speed;
        pacY += pacDirY * speed;

        double gSpeed = (pacPowerTimer > 0) ? 0.0625 : 0.10;
        for (int i = 0; i < 4; i++) {
            boolean gCenter = Math.abs(ghostX[i] - Math.round(ghostX[i])) < 0.11 && Math.abs(ghostY[i] - Math.round(ghostY[i])) < 0.11;
            if (gCenter) {
                int gx = (int) Math.round(ghostX[i]);
                int gy = (int) Math.round(ghostY[i]);
                ghostX[i] = gx;
                ghostY[i] = gy;

                List<int[]> validDirs = new ArrayList<>();
                int[][] allDirs = { {1,0}, {-1,0}, {0,1}, {0,-1} };
                for (int[] d : allDirs) {
                    if (d[0] == -ghostDX[i] && d[1] == -ghostDY[i]) continue;
                    if (isPacWalkable(gx + d[0], gy + d[1], true)) {
                        validDirs.add(d);
                    }
                }
                if (validDirs.isEmpty()) {
                    for (int[] d : allDirs) {
                        if (isPacWalkable(gx + d[0], gy + d[1], true)) validDirs.add(d);
                    }
                }
                if (!validDirs.isEmpty()) {
                    int[] chosen = validDirs.get(rand.nextInt(validDirs.size()));
                    if (i == 0 && rand.nextInt(100) < 70) {
                        double bestMetric = (pacPowerTimer > 0) ? -1 : 1e9;
                        for (int[] d : validDirs) {
                            double dist = Math.hypot((gx + d[0]) - pacX, (gy + d[1]) - pacY);
                            if (pacPowerTimer > 0 && dist > bestMetric) {
                                bestMetric = dist;
                                chosen = d;
                            } else if (pacPowerTimer == 0 && dist < bestMetric) {
                                bestMetric = dist;
                                chosen = d;
                            }
                        }
                    }
                    ghostDX[i] = chosen[0];
                    ghostDY[i] = chosen[1];
                }
            }
            ghostX[i] += ghostDX[i] * gSpeed;
            ghostY[i] += ghostDY[i] * gSpeed;

            if (Math.hypot(pacX - ghostX[i], pacY - ghostY[i]) < 0.65) {
                if (pacPowerTimer > 0) {
                    score += 200;
                    ghostX[i] = 9;
                    ghostY[i] = 5;
                    ghostDX[i] = 0;
                    ghostDY[i] = -1;
                } else {
                    pacLives--;
                    if (pacLives <= 0) {
                        gameOver = true;
                    } else {
                        initPacman(false);
                    }
                    return;
                }
            }
        }
    }

    private boolean isPacWalkable(int c, int r, boolean isGhost) {
        if (r < 0 || r >= PAC_ROWS || c < 0 || c >= PAC_COLS) return false;
        if (pacGrid[r][c] == 1) return false;
        if (!isGhost && r == 5 && c >= 8 && c <= 10) return false;
        return true;
    }

    private void updateTetris() {
        int dropInterval = Math.max(8, 32 - (tetLinesCleared / 3) * 3);
        tetDropTimer++;
        if (tetDropTimer >= dropInterval) {
            tetDropTimer = 0;
            if (canTetrominoFit(tetCurPiece, tetRotation, tetX, tetY + 1)) {
                tetY++;
            } else {
                lockTetromino();
            }
        }
    }

    private boolean canTetrominoFit(int piece, int rot, int px, int py) {
        int[][] blocks = TET_SHAPES[piece][rot];
        for (int[] b : blocks) {
            int cx = px + b[0];
            int cy = py + b[1];
            if (cx < 0 || cx >= TET_COLS || cy >= TET_ROWS) return false;
            if (cy >= 0 && tetBoard[cy][cx] != 0) return false;
        }
        return true;
    }

    private void lockTetromino() {
        int[][] blocks = TET_SHAPES[tetCurPiece][tetRotation];
        for (int[] b : blocks) {
            int cx = tetX + b[0];
            int cy = tetY + b[1];
            if (cy >= 0 && cy < TET_ROWS && cx >= 0 && cx < TET_COLS) {
                tetBoard[cy][cx] = tetCurPiece + 1;
            }
        }

        int clearedNow = 0;
        for (int r = TET_ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < TET_COLS; c++) {
                if (tetBoard[r][c] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                clearedNow++;
                for (int rr = r; rr > 0; rr--) {
                    System.arraycopy(tetBoard[rr - 1], 0, tetBoard[rr], 0, TET_COLS);
                }
                for (int c = 0; c < TET_COLS; c++) tetBoard[0][c] = 0;
                r++;
            }
        }

        if (clearedNow > 0) {
            int[] points = { 0, 100, 300, 500, 800 };
            score += points[Math.min(4, clearedNow)];
            tetLinesCleared += clearedNow;
        } else {
            score += 10;
        }

        spawnNextTetromino();
    }

    private void updateDoom2D() {
        if (doomMoveLeft) doomPlayerX = Math.max(25, doomPlayerX - 5.5);
        if (doomMoveRight) doomPlayerX = Math.min(545, doomPlayerX + 5.5);

        if (doomShootCooldown > 0) doomShootCooldown--;
        if (doomShooting && doomShootCooldown == 0) {
            DoomBullet b = new DoomBullet();
            b.x = doomPlayerX + 14;
            b.y = 285;
            b.vx = 0;
            b.vy = -10.5;
            b.fromPlayer = true;
            b.color = new Color(0, 255, 220);
            doomBullets.add(b);
            doomShootCooldown = 10;
        }

        for (Iterator<DoomEnemy> it = doomEnemies.iterator(); it.hasNext(); ) {
            DoomEnemy en = it.next();
            en.x += en.vx;
            en.y += en.vy;
            if (en.x < 20 || en.x > 550) en.vx *= -1;

            if (en.y > 270) {
                doomPlayerHP -= 18;
                en.y = 65;
                spawnExplosion(doomPlayerX + 15, 290, Color.RED, 12);
                if (doomPlayerHP <= 0) {
                    doomPlayerHP = 0;
                    gameOver = true;
                    return;
                }
            }

            en.shootCooldown--;
            if (en.shootCooldown <= 0 && en.type != 1) {
                en.shootCooldown = Math.max(35, 95 - doomWave * 6) + rand.nextInt(40);
                DoomBullet fb = new DoomBullet();
                fb.x = en.x + 14;
                fb.y = en.y + 24;
                double angle = Math.atan2(290 - fb.y, (doomPlayerX + 15) - fb.x);
                double spd = (en.type == 2) ? 4.8 : 3.8;
                fb.vx = Math.cos(angle) * spd;
                fb.vy = Math.sin(angle) * spd;
                fb.fromPlayer = false;
                fb.color = (en.type == 2) ? new Color(255, 80, 255) : new Color(255, 120, 20);
                doomBullets.add(fb);
            }
        }

        for (Iterator<DoomBullet> bit = doomBullets.iterator(); bit.hasNext(); ) {
            DoomBullet b = bit.next();
            b.x += b.vx;
            b.y += b.vy;
            if (b.y < 35 || b.y > 330 || b.x < 10 || b.x > 590) {
                bit.remove();
                continue;
            }

            if (b.fromPlayer) {
                for (Iterator<DoomEnemy> eit = doomEnemies.iterator(); eit.hasNext(); ) {
                    DoomEnemy en = eit.next();
                    if (b.x >= en.x - 4 && b.x <= en.x + 30 && b.y >= en.y && b.y <= en.y + 28) {
                        en.hp -= 15;
                        spawnExplosion(b.x, b.y, Color.ORANGE, 6);
                        bit.remove();
                        if (en.hp <= 0) {
                            score += (en.type + 1) * 50;
                            spawnExplosion(en.x + 14, en.y + 14, new Color(255, 60, 40), 16);
                            eit.remove();
                        }
                        break;
                    }
                }
            } else {
                if (b.x >= doomPlayerX && b.x <= doomPlayerX + 30 && b.y >= 285 && b.y <= 315) {
                    doomPlayerHP -= 12;
                    spawnExplosion(b.x, b.y, Color.RED, 10);
                    bit.remove();
                    if (doomPlayerHP <= 0) {
                        doomPlayerHP = 0;
                        gameOver = true;
                        return;
                    }
                }
            }
        }

        for (Iterator<DoomParticle> pit = doomParticles.iterator(); pit.hasNext(); ) {
            DoomParticle p = pit.next();
            p.x += p.vx;
            p.y += p.vy;
            p.life--;
            if (p.life <= 0) pit.remove();
        }

        if (doomEnemies.isEmpty()) {
            doomWave++;
            doomPlayerHP = Math.min(100, doomPlayerHP + 25);
            score += 200;
            spawnDoomWave();
        }
    }

    private void spawnExplosion(double x, double y, Color color, int count) {
        for (int i = 0; i < count; i++) {
            DoomParticle p = new DoomParticle();
            p.x = x;
            p.y = y;
            double ang = rand.nextDouble() * Math.PI * 2;
            double spd = 1.0 + rand.nextDouble() * 3.5;
            p.vx = Math.cos(ang) * spd;
            p.vy = Math.sin(ang) * spd;
            p.life = 10 + rand.nextInt(14);
            p.color = color;
            doomParticles.add(p);
        }
    }

    private void updateDino() {
        if (tickCount % 5 == 0) score++;
        dinoSpeed = Math.min(13.0, 6.5 + (score / 120.0));

        if (dinoJumping) {
            dinoY += dinoVelY;
            dinoVelY += (dinoDucking ? 1.4 : 0.72);
            if (dinoY >= 0) {
                dinoY = 0;
                dinoVelY = 0;
                dinoJumping = false;
            }
        }

        dinoSpawnTimer--;
        if (dinoSpawnTimer <= 0) {
            DinoObstacle obs = new DinoObstacle();
            obs.x = 570;
            int r = rand.nextInt(100);
            if (score > 80 && r < 30) {
                obs.type = 2;
                obs.width = 30;
                obs.height = 20;
                obs.yOffset = rand.nextBoolean() ? 30 : 5;
            } else if (r < 65) {
                obs.type = 0;
                obs.width = 18;
                obs.height = 36;
                obs.yOffset = 0;
            } else {
                obs.type = 1;
                obs.width = 34;
                obs.height = 32;
                obs.yOffset = 0;
            }
            dinoObstacles.add(obs);
            dinoSpawnTimer = (int) (45 + rand.nextInt(45) - Math.min(20, dinoSpeed * 1.5));
        }

        int dinoH = (dinoDucking && !dinoJumping) ? 24 : 42;
        int dinoW = (dinoDucking && !dinoJumping) ? 32 : 24;
        double dinoTop = 270 - dinoH + dinoY;
        double dinoBottom = 270 + dinoY;
        double dinoLeft = 75;
        double dinoRight = 75 + dinoW;

        for (Iterator<DinoObstacle> it = dinoObstacles.iterator(); it.hasNext(); ) {
            DinoObstacle obs = it.next();
            obs.x -= dinoSpeed;
            if (obs.x + obs.width < 10) {
                it.remove();
                continue;
            }

            double obsBottom = 270 - obs.yOffset;
            double obsTop = obsBottom - obs.height;
            double obsLeft = obs.x + 3;
            double obsRight = obs.x + obs.width - 3;

            if (dinoRight > obsLeft && dinoLeft < obsRight && dinoBottom > obsTop + 3 && dinoTop < obsBottom - 3) {
                gameOver = true;
                return;
            }
        }
    }

    private void updateSnake() {
        int moveEvery = Math.max(4, 8 - (score / 120));
        if (tickCount % moveEvery != 0) return;

        snakeDX = snakeNextDX;
        snakeDY = snakeNextDY;

        Point head = snakeBody.get(0);
        int nx = head.x + snakeDX;
        int ny = head.y + snakeDY;

        if (nx < 0 || nx >= SNAKE_COLS || ny < 0 || ny >= SNAKE_ROWS) {
            gameOver = true;
            return;
        }

        Point newHead = new Point(nx, ny);
        for (int i = 0; i < snakeBody.size() - 1; i++) {
            if (snakeBody.get(i).equals(newHead)) {
                gameOver = true;
                return;
            }
        }

        snakeBody.add(0, newHead);
        if (newHead.equals(snakeFood)) {
            score += snakeGoldenFood ? 50 : 15;
            spawnSnakeFood();
        } else {
            snakeBody.remove(snakeBody.size() - 1);
        }
    }

    public void keyPressed(int code) {
        if ((gameOver || victory) && (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_R || code == KeyEvent.VK_SPACE)) {
            startGame(activeGame);
            return;
        }
        if (code == KeyEvent.VK_R) {
            startGame(activeGame);
            return;
        }

        switch (activeGame) {
            case PACMAN:
                if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) { pacNextDirX = -1; pacNextDirY = 0; }
                else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) { pacNextDirX = 1; pacNextDirY = 0; }
                else if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) { pacNextDirX = 0; pacNextDirY = -1; }
                else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) { pacNextDirX = 0; pacNextDirY = 1; }
                break;

            case TETRIS:
                if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) {
                    if (canTetrominoFit(tetCurPiece, tetRotation, tetX - 1, tetY)) tetX--;
                } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) {
                    if (canTetrominoFit(tetCurPiece, tetRotation, tetX + 1, tetY)) tetX++;
                } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                    if (canTetrominoFit(tetCurPiece, tetRotation, tetX, tetY + 1)) {
                        tetY++;
                        score += 1;
                    }
                } else if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                    int nextRot = (tetRotation + 1) % 4;
                    if (canTetrominoFit(tetCurPiece, nextRot, tetX, tetY)) tetRotation = nextRot;
                    else if (canTetrominoFit(tetCurPiece, nextRot, tetX - 1, tetY)) { tetX--; tetRotation = nextRot; }
                    else if (canTetrominoFit(tetCurPiece, nextRot, tetX + 1, tetY)) { tetX++; tetRotation = nextRot; }
                } else if (code == KeyEvent.VK_SPACE) {
                    while (canTetrominoFit(tetCurPiece, tetRotation, tetX, tetY + 1)) {
                        tetY++;
                        score += 2;
                    }
                    lockTetromino();
                }
                break;

            case DOOM2D:
                if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) doomMoveLeft = true;
                if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) doomMoveRight = true;
                if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_UP || code == KeyEvent.VK_W) doomShooting = true;
                break;

            case DINO:
                if ((code == KeyEvent.VK_SPACE || code == KeyEvent.VK_UP || code == KeyEvent.VK_W) && !dinoJumping) {
                    dinoJumping = true;
                    dinoVelY = -12.5;
                }
                if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                    dinoDucking = true;
                }
                break;

            case SNAKE:
                if ((code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) && snakeDX == 0) { snakeNextDX = -1; snakeNextDY = 0; }
                else if ((code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) && snakeDX == 0) { snakeNextDX = 1; snakeNextDY = 0; }
                else if ((code == KeyEvent.VK_UP || code == KeyEvent.VK_W) && snakeDY == 0) { snakeNextDX = 0; snakeNextDY = -1; }
                else if ((code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) && snakeDY == 0) { snakeNextDX = 0; snakeNextDY = 1; }
                break;

            case MATRIX:
                break;
        }
    }

    public void keyReleased(int code) {
        if (activeGame == GameType.DOOM2D) {
            if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) doomMoveLeft = false;
            if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) doomMoveRight = false;
            if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_UP || code == KeyEvent.VK_W) doomShooting = false;
        } else if (activeGame == GameType.DINO) {
            if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) dinoDucking = false;
        }
    }

    public void render(Graphics2D g2d, int screenWidth, int screenHeight) {
        int boxW = 620;
        int boxH = 390;
        int boxLeft = (screenWidth - boxW) / 2;
        int boxTop = (screenHeight - boxH) / 2 - 20;

        g2d.setColor(new Color(8, 12, 22, 250));
        g2d.fillRoundRect(boxLeft, boxTop, boxW, boxH, 14, 14);
        g2d.setColor(new Color(0, 215, 255));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(boxLeft, boxTop, boxW, boxH, 14, 14);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setColor(new Color(18, 28, 48));
        g2d.fillRoundRect(boxLeft + 2, boxTop + 2, boxW - 4, 34, 12, 12);
        g2d.setColor(new Color(0, 200, 255));
        g2d.drawLine(boxLeft, boxTop + 36, boxLeft + boxW, boxTop + 36);

        g2d.setFont(new Font("Courier New", Font.BOLD, 15));
        g2d.setColor(Color.YELLOW);
        int hi = highScores[activeGame.ordinal()];
        g2d.drawString("SCORE: " + score + "   |   RECORDE: " + hi, boxLeft + 16, boxTop + 24);

        switch (activeGame) {
            case PACMAN:
                renderPacman(g2d, boxLeft, boxTop, boxW, boxH);
                break;
            case TETRIS:
                renderTetris(g2d, boxLeft, boxTop, boxW, boxH);
                break;
            case DOOM2D:
                renderDoom2D(g2d, boxLeft, boxTop, boxW, boxH);
                break;
            case DINO:
                renderDino(g2d, boxLeft, boxTop, boxW, boxH);
                break;
            case SNAKE:
                renderSnake(g2d, boxLeft, boxTop, boxW, boxH);
                break;
            case MATRIX:
                renderMatrix(g2d, boxLeft, boxTop, boxW, boxH);
                break;
        }

        if (gameOver) {
            g2d.setColor(new Color(0, 0, 0, 195));
            g2d.fillRect(boxLeft + 10, boxTop + 40, boxW - 20, boxH - 72);
            g2d.setColor(new Color(255, 65, 65));
            g2d.setFont(new Font("Courier New", Font.BOLD, 34));
            drawCentered(g2d, "FIM DE JOGO!", boxLeft + boxW / 2, boxTop + boxH / 2 - 20);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            drawCentered(g2d, "Pontuação Final: " + score + "   (Recorde: " + hi + ")", boxLeft + boxW / 2, boxTop + boxH / 2 + 15);
            g2d.setColor(Color.YELLOW);
            g2d.setFont(new Font("Courier New", Font.BOLD, 14));
            drawCentered(g2d, "Pressione [ENTER] ou [R] para Jogar Novamente  |  [ESC] Voltar ao CMD", boxLeft + boxW / 2, boxTop + boxH / 2 + 52);
        }

        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        g2d.setColor(new Color(175, 195, 220));
        drawCentered(g2d, "[ESC] Fechar executável e retornar ao Prompt de Comando   |   [R] Reiniciar partida", boxLeft + boxW / 2, boxTop + boxH - 10);
    }

    private void renderPacman(Graphics2D g2d, int bx, int by, int bw, int bh) {
        g2d.setColor(Color.CYAN);
        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        g2d.drawString("[PACMAN.EXE] Vidas: " + "♥ ".repeat(Math.max(0, pacLives)), bx + bw - 210, by + 24);

        int cell = 26;
        int startX = bx + (bw - PAC_COLS * cell) / 2;
        int startY = by + 48;

        for (int r = 0; r < PAC_ROWS; r++) {
            for (int c = 0; c < PAC_COLS; c++) {
                int cx = startX + c * cell;
                int cy = startY + r * cell;
                if (pacGrid[r][c] == 1) {
                    g2d.setColor(new Color(20, 45, 140));
                    g2d.fillRoundRect(cx + 1, cy + 1, cell - 2, cell - 2, 6, 6);
                    g2d.setColor(new Color(60, 120, 255));
                    g2d.drawRoundRect(cx + 1, cy + 1, cell - 2, cell - 2, 6, 6);
                } else if (pacGrid[r][c] == 0) {
                    g2d.setColor(new Color(255, 220, 160));
                    g2d.fillOval(cx + cell / 2 - 3, cy + cell / 2 - 3, 6, 6);
                } else if (pacGrid[r][c] == 2) {
                    if ((tickCount / 10) % 2 == 0) {
                        g2d.setColor(Color.WHITE);
                        g2d.fillOval(cx + cell / 2 - 6, cy + cell / 2 - 6, 12, 12);
                    }
                }
            }
        }

        int px = (int) (startX + pacX * cell + 2);
        int py = (int) (startY + pacY * cell + 2);
        int mouth = ((tickCount / 4) % 2 == 0) ? 38 : 8;
        int angle = 0;
        if (pacDirX < 0) angle = 180;
        else if (pacDirY < 0) angle = 90;
        else if (pacDirY > 0) angle = 270;
        g2d.setColor(Color.YELLOW);
        g2d.fillArc(px, py, cell - 4, cell - 4, angle + mouth, 360 - mouth * 2);

        for (int i = 0; i < 4; i++) {
            int gx = (int) (startX + ghostX[i] * cell + 2);
            int gy = (int) (startY + ghostY[i] * cell + 2);
            if (pacPowerTimer > 0) {
                g2d.setColor((pacPowerTimer < 80 && (tickCount / 6) % 2 == 0) ? Color.WHITE : new Color(30, 80, 255));
            } else {
                g2d.setColor(ghostColors[i]);
            }
            g2d.fillArc(gx, gy, cell - 4, cell - 4, 0, 180);
            g2d.fillRect(gx, gy + (cell - 4) / 2, cell - 4, (cell - 4) / 2);
            g2d.setColor(Color.WHITE);
            g2d.fillOval(gx + 4, gy + 5, 5, 5);
            g2d.fillOval(gx + 13, gy + 5, 5, 5);
            g2d.setColor(Color.BLUE);
            g2d.fillOval(gx + 6, gy + 7, 2, 2);
            g2d.fillOval(gx + 15, gy + 7, 2, 2);
        }
    }

    private void renderTetris(Graphics2D g2d, int bx, int by, int bw, int bh) {
        g2d.setColor(Color.CYAN);
        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        g2d.drawString("[TETRIS.EXE] Linhas: " + tetLinesCleared, bx + bw - 210, by + 24);

        int cell = 17;
        int boardW = TET_COLS * cell;
        int boardH = TET_ROWS * cell;
        int boardX = bx + 140;
        int boardY = by + 46;

        g2d.setColor(new Color(14, 18, 28));
        g2d.fillRect(boardX, boardY, boardW, boardH);
        g2d.setColor(new Color(30, 40, 60));
        for (int r = 0; r <= TET_ROWS; r++) g2d.drawLine(boardX, boardY + r * cell, boardX + boardW, boardY + r * cell);
        for (int c = 0; c <= TET_COLS; c++) g2d.drawLine(boardX + c * cell, boardY, boardX + c * cell, boardY + boardH);

        for (int r = 0; r < TET_ROWS; r++) {
            for (int c = 0; c < TET_COLS; c++) {
                int id = tetBoard[r][c];
                if (id != 0) {
                    drawTetBlock(g2d, boardX + c * cell, boardY + r * cell, cell, TET_COLORS[id]);
                }
            }
        }

        int ghostY = tetY;
        while (canTetrominoFit(tetCurPiece, tetRotation, tetX, ghostY + 1)) {
            ghostY++;
        }
        g2d.setColor(new Color(255, 255, 255, 60));
        for (int[] b : TET_SHAPES[tetCurPiece][tetRotation]) {
            int cx = tetX + b[0];
            int cy = ghostY + b[1];
            if (cy >= 0) {
                g2d.drawRect(boardX + cx * cell + 1, boardY + cy * cell + 1, cell - 3, cell - 3);
            }
        }

        for (int[] b : TET_SHAPES[tetCurPiece][tetRotation]) {
            int cx = tetX + b[0];
            int cy = tetY + b[1];
            if (cy >= 0) {
                drawTetBlock(g2d, boardX + cx * cell, boardY + cy * cell, cell, TET_COLORS[tetCurPiece + 1]);
            }
        }

        int sideX = boardX + boardW + 30;
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Courier New", Font.BOLD, 14));
        g2d.drawString("PRÓXIMA PEÇA:", sideX, boardY + 20);
        g2d.setColor(new Color(18, 24, 38));
        g2d.fillRoundRect(sideX, boardY + 30, 110, 80, 8, 8);
        g2d.setColor(new Color(0, 190, 255));
        g2d.drawRoundRect(sideX, boardY + 30, 110, 80, 8, 8);

        for (int[] b : TET_SHAPES[tetNextPiece][0]) {
            drawTetBlock(g2d, sideX + 22 + b[0] * cell, boardY + 48 + b[1] * cell, cell, TET_COLORS[tetNextPiece + 1]);
        }

        g2d.setColor(new Color(180, 205, 230));
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Controles do Tetris:", sideX, boardY + 145);
        g2d.drawString("• Esq / Dir : Mover", sideX, boardY + 168);
        g2d.drawString("• Cima / W  : Girar peça", sideX, boardY + 188);
        g2d.drawString("• Baixo / S : Descer rápido", sideX, boardY + 208);
        g2d.drawString("• ESPAÇO    : Queda direta", sideX, boardY + 228);
    }

    private void drawTetBlock(Graphics2D g2d, int x, int y, int size, Color c) {
        g2d.setColor(c);
        g2d.fillRect(x + 1, y + 1, size - 2, size - 2);
        g2d.setColor(c.brighter());
        g2d.drawLine(x + 1, y + 1, x + size - 2, y + 1);
        g2d.drawLine(x + 1, y + 1, x + 1, y + size - 2);
    }

    private void renderDoom2D(Graphics2D g2d, int bx, int by, int bw, int bh) {
        g2d.setColor(Color.CYAN);
        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        g2d.drawString("[DOOM.EXE] ONDA " + doomWave + "  |  HP: " + doomPlayerHP + "%", bx + bw - 230, by + 24);

        g2d.setColor(new Color(60, 20, 20));
        g2d.fillRect(bx + 20, by + 44, 140, 10);
        g2d.setColor(doomPlayerHP > 35 ? new Color(0, 235, 110) : Color.RED);
        g2d.fillRect(bx + 20, by + 44, (int) (1.4 * doomPlayerHP), 10);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(bx + 20, by + 44, 140, 10);

        for (DoomEnemy en : doomEnemies) {
            int ex = bx + (int) en.x;
            int ey = by + (int) en.y;
            if (en.type == 2) g2d.setColor(new Color(220, 40, 180));
            else if (en.type == 1) g2d.setColor(new Color(255, 110, 90));
            else g2d.setColor(new Color(210, 50, 30));

            g2d.fillRoundRect(ex, ey, 28, 26, 8, 8);
            g2d.setColor(Color.YELLOW);
            g2d.fillRect(ex + 5, ey + 7, 5, 5);
            g2d.fillRect(ex + 18, ey + 7, 5, 5);

            g2d.setColor(Color.RED);
            g2d.fillRect(ex, ey - 5, (int) (28.0 * en.hp / en.maxHp), 3);
        }

        for (DoomBullet b : doomBullets) {
            g2d.setColor(b.color);
            g2d.fillOval(bx + (int) b.x - 3, by + (int) b.y - 5, 7, 10);
        }

        for (DoomParticle p : doomParticles) {
            g2d.setColor(p.color);
            g2d.fillRect(bx + (int) p.x, by + (int) p.y, 3, 3);
        }

        int px = bx + (int) doomPlayerX;
        int py = by + 290;
        g2d.setColor(new Color(50, 205, 80));
        g2d.fillRoundRect(px, py, 30, 28, 6, 6);
        g2d.setColor(new Color(0, 240, 255));
        g2d.fillRect(px + 7, py + 5, 16, 6);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(px + 12, py - 8, 6, 10);
    }

    private void renderDino(Graphics2D g2d, int bx, int by, int bw, int bh) {
        g2d.setColor(Color.CYAN);
        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        g2d.drawString("[DINO.EXE] Cima/ESPAÇO: Pular | Baixo: Agachar", bx + bw - 375, by + 24);

        boolean nightMode = ((score / 400) % 2 == 1);
        g2d.setColor(nightMode ? new Color(12, 16, 30) : new Color(235, 242, 250));
        g2d.fillRect(bx + 15, by + 45, bw - 30, bh - 85);

        int groundY = by + 270;
        g2d.setColor(nightMode ? Color.LIGHT_GRAY : new Color(70, 75, 85));
        g2d.drawLine(bx + 25, groundY, bx + bw - 25, groundY);

        for (int i = 0; i < 12; i++) {
            int gx = bx + 30 + ((i * 55 - (int) (tickCount * dinoSpeed)) % (bw - 60) + (bw - 60)) % (bw - 60);
            g2d.fillRect(gx, groundY + 4 + (i % 3) * 2, 4, 2);
        }

        int dinoH = (dinoDucking && !dinoJumping) ? 24 : 42;
        int dinoW = (dinoDucking && !dinoJumping) ? 34 : 24;
        int dx = bx + 75;
        int dy = (int) (groundY - dinoH + dinoY);
        g2d.setColor(nightMode ? new Color(0, 240, 160) : new Color(55, 62, 72));
        g2d.fillRoundRect(dx, dy, dinoW, dinoH - 6, 6, 6);
        int legOffset = ((tickCount / 4) % 2 == 0) ? 0 : 4;
        g2d.fillRect(dx + 4, dy + dinoH - 6, 5, 6 - legOffset);
        g2d.fillRect(dx + 14, dy + dinoH - 6, 5, 2 + legOffset);
        g2d.setColor(nightMode ? Color.BLACK : Color.WHITE);
        g2d.fillRect(dx + dinoW - 7, dy + 5, 4, 4);

        for (DinoObstacle obs : dinoObstacles) {
            int ox = bx + (int) obs.x;
            int oy = groundY - obs.yOffset - obs.height;
            if (obs.type == 2) {
                g2d.setColor(new Color(220, 70, 60));
                int wingY = ((tickCount / 6) % 2 == 0) ? oy - 4 : oy + 6;
                g2d.fillRoundRect(ox, oy + 4, obs.width, 10, 4, 4);
                g2d.fillPolygon(new int[]{ox + 8, ox + 20, ox + 14}, new int[]{oy + 6, oy + 6, wingY}, 3);
            } else {
                g2d.setColor(new Color(35, 165, 75));
                g2d.fillRoundRect(ox, oy, obs.width, obs.height, 6, 6);
            }
        }
    }

    private void renderSnake(Graphics2D g2d, int bx, int by, int bw, int bh) {
        g2d.setColor(Color.CYAN);
        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        g2d.drawString("[SNAKE.EXE] Tamanho: " + snakeBody.size(), bx + bw - 210, by + 24);

        int cell = 20;
        int gridW = SNAKE_COLS * cell;
        int gridH = SNAKE_ROWS * cell;
        int gx = bx + (bw - gridW) / 2;
        int gy = by + 48;

        g2d.setColor(new Color(12, 20, 32));
        g2d.fillRect(gx, gy, gridW, gridH);
        g2d.setColor(new Color(35, 55, 80));
        g2d.drawRect(gx, gy, gridW, gridH);

        g2d.setColor(snakeGoldenFood ? Color.YELLOW : new Color(255, 60, 60));
        g2d.fillOval(gx + snakeFood.x * cell + 2, gy + snakeFood.y * cell + 2, cell - 4, cell - 4);

        for (int i = 0; i < snakeBody.size(); i++) {
            Point p = snakeBody.get(i);
            g2d.setColor(i == 0 ? new Color(0, 255, 140) : new Color(0, 185, 100));
            g2d.fillRoundRect(gx + p.x * cell + 1, gy + p.y * cell + 1, cell - 2, cell - 2, 5, 5);
        }
    }

    private void renderMatrix(Graphics2D g2d, int bx, int by, int bw, int bh) {
        g2d.setColor(Color.GREEN);
        g2d.setFont(new Font("Courier New", Font.BOLD, 13));
        g2d.drawString("[MATRIX.EXE] Simulação Neural Ativa", bx + bw - 290, by + 24);

        g2d.setColor(Color.BLACK);
        g2d.fillRect(bx + 10, by + 42, bw - 20, bh - 75);

        g2d.setFont(new Font("Courier New", Font.BOLD, 14));
        for (int i = 0; i < matrixDrops.length; i++) {
            int x = bx + 20 + i * 14;
            int y = by + 55 + (matrixDrops[i] * 14) % (bh - 90);
            g2d.setColor(new Color(180, 255, 180));
            char ch = (char) (33 + ((tickCount + i * 7) % 90));
            g2d.drawString(String.valueOf(ch), x, y);
            for (int tail = 1; tail <= 6; tail++) {
                int ty = y - tail * 14;
                if (ty > by + 48) {
                    g2d.setColor(new Color(0, Math.max(30, 230 - tail * 35), 40));
                    char tch = (char) (33 + ((tickCount + i * 13 + tail) % 90));
                    g2d.drawString(String.valueOf(tch), x, ty);
                }
            }
            if (tickCount % matrixSpeeds[i] == 0) {
                matrixDrops[i]++;
            }
        }
    }

    private void drawCentered(Graphics2D g2d, String text, int cx, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }
}
