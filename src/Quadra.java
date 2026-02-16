import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import javax.sound.midi.*;

public class Quadra extends JPanel implements ActionListener {

    private enum State {
        MENU, PLAYING, GAME_OVER, INSTRUCTIONS, CREDITS
    }
    private State currentState = State.MENU;

    private final int BLOCK_SIZE = 30;
    private final int BOARD_WIDTH = 10;
    private final int BOARD_HEIGHT = 20;
    private final int PANEL_WIDTH = 600;
    private final int PANEL_HEIGHT = BOARD_HEIGHT * BLOCK_SIZE;

    private int menuOption = 0;
    private final String[] menuOptions = {"JOGAR", "TUTORIAL", "CREDITOS", "SAIR"};
    
    private int gameOverOption = 0;
    private final String[] gameOverOptions = {"TENTAR NOVAMENTE", "VOLTAR AO MENU"};

    private Timer gameLoop;
    private long lastDropTime;
    private int dropInterval = 500;

    private boolean isPaused = false;
    private int score = 0;
    private int highScore = 0;
    private int level = 1;
    private int linesClearedTotal = 0;
    private int comboCount = -1;

    private int[][] board = new int[BOARD_HEIGHT][BOARD_WIDTH];

    private int currentPieceType;
    private int[][] currentPieceShape;
    private int nextPieceType;
    private int[][] nextPieceShape;

    private int holdPieceType = 0;
    private int[][] holdPieceShape = null;
    private boolean canHold = true;

    private int curX = 0;
    private int curY = 0;

    private ArrayList<Integer> bag = new ArrayList<>();
    private ArrayList<Particle> particles = new ArrayList<>();
    private float rainbowHue = 0;

    private final Color[] pieceColors = {
        new Color(0,0,0),
        new Color(0, 240, 240),
        new Color(240, 0, 0),
        new Color(0, 240, 0),
        new Color(160, 0, 240),
        new Color(240, 240, 0),
        new Color(240, 160, 0),
        new Color(0, 0, 240)
    };

    private final int[][][] TETROMINOS = {
        {{0,0,0,0}, {0,0,0,0}, {0,0,0,0}, {0,0,0,0}},
        {{1,1,1,1}, {0,0,0,0}, {0,0,0,0}, {0,0,0,0}},
        {{1,1,0,0}, {0,1,1,0}, {0,0,0,0}, {0,0,0,0}},
        {{0,1,1,0}, {1,1,0,0}, {0,0,0,0}, {0,0,0,0}},
        {{0,1,0,0}, {1,1,1,0}, {0,0,0,0}, {0,0,0,0}},
        {{1,1,0,0}, {1,1,0,0}, {0,0,0,0}, {0,0,0,0}},
        {{0,0,1,0}, {1,1,1,0}, {0,0,0,0}, {0,0,0,0}},
        {{1,0,0,0}, {1,1,1,0}, {0,0,0,0}, {0,0,0,0}}
    };

    public static class AudioPlayer {
        private static Sequencer sequencer;
        private static Synthesizer synthesizer;
        private static MidiChannel[] channels;
        
        public static void init() {
            try {
                synthesizer = MidiSystem.getSynthesizer();
                synthesizer.open();
                channels = synthesizer.getChannels();
                channels[0].programChange(80);

                sequencer = MidiSystem.getSequencer();
                sequencer.open();
                
                Sequence sequence = createTetrisTheme();
                sequencer.setSequence(sequence);
                sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        public static void startMusic() {
            if (sequencer != null && !sequencer.isRunning()) sequencer.start();
        }
        
        public static void stopMusic() {
            if (sequencer != null && sequencer.isRunning()) sequencer.stop();
        }

        public static void playMove() { playNote(60, 50); }
        public static void playRotate() { playNote(75, 50); }
        public static void playDrop() { playNote(40, 100); }
        public static void playHold() { playNote(65, 100); }
        public static void playClear() { 
            new Thread(() -> {
                playNote(72, 80);
                try { Thread.sleep(50); } catch(Exception e){}
                playNote(76, 80);
                try { Thread.sleep(50); } catch(Exception e){}
                playNote(79, 150);
            }).start();
        }
        public static void playGameOver() {
            stopMusic();
            new Thread(() -> {
                playNote(55, 300);
                try { Thread.sleep(250); } catch(Exception e){}
                playNote(51, 300);
                try { Thread.sleep(250); } catch(Exception e){}
                playNote(48, 800);
            }).start();
        }

        private static void playNote(int note, int duration) {
            if (channels == null) return;
            new Thread(() -> {
                channels[0].noteOn(note, 90);
                try { Thread.sleep(duration); } catch (InterruptedException e) {}
                channels[0].noteOff(note);
            }).start();
        }

        private static Sequence createTetrisTheme() throws InvalidMidiDataException {
            Sequence seq = new Sequence(Sequence.PPQ, 4);
            Track track = seq.createTrack();
            
            track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 19, 0), 0));
            int[] notes = {
                76, 71, 72, 74, 72, 71, 69, 69, 72, 76, 74, 72, 71, 71, 72, 74, 76, 72, 69, 69,
                74, 77, 81, 79, 77, 76, 72, 76, 74, 72, 71, 71, 72, 74, 76, 72, 69, 69
            };
            int[] durs = {
                4, 2, 2, 4, 2, 2, 4, 2, 2, 4, 2, 2, 4, 2, 2, 4, 4, 4, 4, 4,
                6, 2, 4, 2, 2, 6, 2, 4, 2, 2, 4, 2, 2, 4, 4, 4, 4, 4
            };

            int currentTick = 0;
            for (int i = 0; i < notes.length; i++) {
                addNote(track, 0, notes[i], currentTick, durs[i]);
                currentTick += durs[i];
            }
            return seq;
        }

        private static void addNote(Track track, int ch, int key, long tick, int dur) throws InvalidMidiDataException {
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, ch, key, 100), tick));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, ch, key, 100), tick + dur));
        }
    }

    private class Particle {
        float x, y, vx, vy, life = 1.0f;
        Color color;
        int size;

        public Particle(int x, int y, Color c) {
            this.x = x; this.y = y; this.color = c;
            this.size = (int)(Math.random() * 6) + 2;
            this.vx = (float) (Math.random() * 4 - 2);
            this.vy = (float) (Math.random() * 4 - 2);
        }
        public boolean update() {
            x += vx; y += vy; vy += 0.2; life -= 0.03;
            return life > 0;
        }
        public void draw(Graphics2D g) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, life)));
            g.setColor(color);
            g.fillRect((int)x, (int)y, size, size);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
    }

    public Quadra() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new TAdapter());
        
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (currentState == State.PLAYING && !isPaused) {
                    isPaused = true;
                    repaint();
                }
            }
        });

        loadHighScore();
        AudioPlayer.init();
        AudioPlayer.startMusic();
        gameLoop = new Timer(16, this);
        gameLoop.start();
    }

    private void loadHighScore() {
        try {
            File f = new File("highscore.txt");
            if (!f.exists()) return;
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) highScore = Integer.parseInt(line);
            reader.close();
        } catch (Exception e) {}
    }

    private void saveHighScore() {
        if (score > highScore) {
            highScore = score;
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter("highscore.txt"));
                writer.write(String.valueOf(highScore));
                writer.close();
            } catch (Exception e) {}
        }
    }

    private void startGame() {
        for (int i = 0; i < BOARD_HEIGHT; i++)
            for (int j = 0; j < BOARD_WIDTH; j++)
                board[i][j] = 0;

        score = 0;
        level = 1;
        linesClearedTotal = 0;
        comboCount = -1;
        isPaused = false;
        particles.clear();
        bag.clear();
        holdPieceType = 0;
        holdPieceShape = null;
        canHold = true;
        dropInterval = 500;
        lastDropTime = System.currentTimeMillis();

        nextPieceType = getNextFromBag();
        nextPieceShape = copyShape(TETROMINOS[nextPieceType]);
        spawnPiece();
        currentState = State.PLAYING;
    }

    private int getNextFromBag() {
        if (bag.isEmpty()) {
            for (int i = 1; i <= 7; i++) bag.add(i);
            Collections.shuffle(bag);
        }
        return bag.remove(0);
    }

    private int[][] copyShape(int[][] shape) {
        int[][] newShape = new int[4][4];
        for(int i=0; i<4; i++) System.arraycopy(shape[i], 0, newShape[i], 0, 4);
        return newShape;
    }

    private void spawnPiece() {
        currentPieceType = nextPieceType;
        currentPieceShape = nextPieceShape;
        nextPieceType = getNextFromBag();
        nextPieceShape = copyShape(TETROMINOS[nextPieceType]);
        curX = BOARD_WIDTH / 2 - 2;
        curY = 0;
        canHold = true;

        if (!checkCollision(currentPieceShape, curX, curY)) {
            currentState = State.GAME_OVER;
            saveHighScore();
            AudioPlayer.playGameOver();
            gameOverOption = 0;
        }
    }

    private void holdPiece() {
        if (!canHold || isPaused || currentState != State.PLAYING) return;
        AudioPlayer.playHold();
        if (holdPieceType == 0) {
            holdPieceType = currentPieceType;
            holdPieceShape = copyShape(TETROMINOS[holdPieceType]);
            spawnPiece();
        } else {
            int tempType = currentPieceType;
            currentPieceType = holdPieceType;
            currentPieceShape = copyShape(TETROMINOS[currentPieceType]);
            holdPieceType = tempType;
            holdPieceShape = copyShape(TETROMINOS[holdPieceType]);
            curX = BOARD_WIDTH / 2 - 2;
            curY = 0;
        }
        canHold = false;
        repaint();
    }

    private boolean checkCollision(int[][] shape, int x, int y) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (shape[i][j] == 0) continue;
                int boardX = x + j;
                int boardY = y + i;
                if (boardX < 0 || boardX >= BOARD_WIDTH || boardY >= BOARD_HEIGHT) return false;
                if (boardY >= 0 && board[boardY][boardX] != 0) return false;
            }
        }
        return true;
    }

    private void rotate() {
        if (currentPieceType == 5) return;
        int[][] rotated = new int[4][4];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) rotated[j][3 - i] = currentPieceShape[i][j];

        if (checkCollision(rotated, curX, curY)) {
            currentPieceShape = rotated;
            AudioPlayer.playRotate();
        } else if (checkCollision(rotated, curX - 1, curY)) {
            curX--; currentPieceShape = rotated;
            AudioPlayer.playRotate();
        } else if (checkCollision(rotated, curX + 1, curY)) {
            curX++; currentPieceShape = rotated;
            AudioPlayer.playRotate();
        }
        repaint();
    }

    private void movePiece(int dx, int dy) {
        if (currentState != State.PLAYING || isPaused) return;
        if (checkCollision(currentPieceShape, curX + dx, curY + dy)) {
            curX += dx; curY += dy;
            if (dx != 0) AudioPlayer.playMove();
            repaint();
        } else if (dy > 0) {
            lockPiece();
        }
    }

    private void hardDrop() {
        if (currentState != State.PLAYING || isPaused) return;
        while (checkCollision(currentPieceShape, curX, curY + 1)) curY++;
        createExplosion((curX + 2) * BLOCK_SIZE, (curY + 2) * BLOCK_SIZE, pieceColors[currentPieceType]);
        lockPiece();
        repaint();
    }

    private void createExplosion(int x, int y, Color c) {
        for(int i=0; i<15; i++) particles.add(new Particle(x, y, c));
    }

    private void lockPiece() {
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) if (currentPieceShape[i][j] != 0) {
            int finalY = curY + i;
            if(finalY >= 0) board[finalY][curX + j] = currentPieceType;
        }
        AudioPlayer.playDrop();
        checkLines();
        spawnPiece();
    }

    private void checkLines() {
        int[][] newBoard = new int[BOARD_HEIGHT][BOARD_WIDTH];
        int currentRowDestino = BOARD_HEIGHT - 1;
        int lines = 0;

        for (int i = BOARD_HEIGHT - 1; i >= 0; i--) {
            boolean lineIsFull = true;
            for (int j = 0; j < BOARD_WIDTH; j++) if (board[i][j] == 0) { lineIsFull = false; break; }

            if (!lineIsFull) {
                System.arraycopy(board[i], 0, newBoard[currentRowDestino], 0, BOARD_WIDTH);
                currentRowDestino--;
            } else {
                lines++;
                createExplosion((BOARD_WIDTH * BLOCK_SIZE) / 2, i * BLOCK_SIZE, Color.WHITE);
            }
        }

        if (lines > 0) {
            board = newBoard;
            linesClearedTotal += lines;
            comboCount++;
            int bonusCombo = comboCount * 50 * level;
            int pontosBase = lines == 1 ? 40 : lines == 2 ? 100 : lines == 3 ? 300 : 1200;
            score += (pontosBase * level) + bonusCombo;
            level = 1 + (linesClearedTotal / 10);
            dropInterval = Math.max(100, 500 - (level - 1) * 50);
            if (score > highScore) highScore = score;
            AudioPlayer.playClear();
            if (comboCount > 0) createExplosion(PANEL_WIDTH/2, PANEL_HEIGHT/2, Color.YELLOW);
            repaint();
        } else {
            comboCount = -1;
        }
    }

    private void updateParticles() {
        Iterator<Particle> it = particles.iterator();
        while(it.hasNext()) { if(!it.next().update()) it.remove(); }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        rainbowHue += 0.01f;
        if (rainbowHue > 1.0f) rainbowHue = 0;
        updateParticles();
        if (currentState == State.PLAYING && !isPaused) {
            long now = System.currentTimeMillis();
            if (now - lastDropTime > dropInterval) {
                movePiece(0, 1);
                lastDropTime = now;
            }
        }
        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double scale = Math.min((double) getWidth() / PANEL_WIDTH, (double) getHeight() / PANEL_HEIGHT);
        double xOffset = (getWidth() - (PANEL_WIDTH * scale)) / 2.0;
        double yOffset = (getHeight() - (PANEL_HEIGHT * scale)) / 2.0;

        g2.translate(xOffset, yOffset);
        g2.scale(scale, scale);

        g2.setColor(new Color(15, 15, 20));
        g2.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        if (currentState == State.MENU) drawMenu(g2);
        else if (currentState == State.INSTRUCTIONS) drawInstructions(g2);
        else if (currentState == State.CREDITS) drawCredits(g2);
        else {
            drawGame(g2);
            if (currentState == State.GAME_OVER) drawGameOverScreen(g2);
            else if (isPaused) drawPauseScreen(g2);
        }
        for(Particle p : particles) p.draw(g2);
    }

    private void drawMenu(Graphics2D g) {
        g.setColor(new Color(20, 20, 30));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        g.setColor(new Color(255, 255, 255, 10));
        int offset = (int)(System.currentTimeMillis() / 100) % BLOCK_SIZE;
        for(int i=0; i<PANEL_WIDTH; i+=BLOCK_SIZE) g.drawLine(i, 0, i, PANEL_HEIGHT);
        for(int i=offset; i<PANEL_HEIGHT; i+=BLOCK_SIZE) g.drawLine(0, i, PANEL_WIDTH, i);

        g.setFont(new Font("Segoe UI", Font.BOLD, 55));
        g.setColor(Color.getHSBColor(rainbowHue, 0.8f, 1.0f));
        double floatY = Math.sin(System.currentTimeMillis() * 0.003) * 10;
        drawCenteredString(g, "Quadra", (int)(150 + floatY));
        g.setColor(new Color(0,0,0,100));
        drawCenteredString(g, "Quadra", (int)(155 + floatY));

        g.setFont(new Font("Consolas", Font.BOLD, 20));
        g.setColor(Color.ORANGE);
        drawCenteredString(g, "Recorde Atual: " + highScore, 220);

        g.setFont(new Font("Segoe UI", Font.BOLD, 25));
        for (int i = 0; i < menuOptions.length; i++) {
            int yPos = 300 + (i * 45);
            if (i == menuOption) {
                g.setColor(Color.YELLOW);
                drawCenteredString(g, "> " + menuOptions[i] + " <", yPos);
            } else {
                g.setColor(Color.GRAY);
                drawCenteredString(g, menuOptions[i], yPos);
            }
        }
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(Color.WHITE);
        drawCenteredString(g, "Use Setas e Espaco para selecionar", PANEL_HEIGHT - 40);
    }

    private void drawGame(Graphics2D g) {
        g.setColor(new Color(30, 30, 40));
        g.fillRect(0, 0, BOARD_WIDTH * BLOCK_SIZE, PANEL_HEIGHT);

        for (int i = 0; i < BOARD_HEIGHT; i++)
            for (int j = 0; j < BOARD_WIDTH; j++)
                if (board[i][j] != 0) drawBlock(g, j*BLOCK_SIZE, i*BLOCK_SIZE, pieceColors[board[i][j]], BLOCK_SIZE);

        if(currentState == State.PLAYING) {
            int ghostY = curY;
            while(checkCollision(currentPieceShape, curX, ghostY+1)) ghostY++;
            g.setColor(new Color(255,255,255,30));
            for(int i=0; i<4; i++) for(int j=0; j<4; j++) if(currentPieceShape[i][j] != 0)
                g.fillRect((curX+j)*BLOCK_SIZE, (ghostY+i)*BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
            for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) if (currentPieceShape[i][j] != 0)
                drawBlock(g, (curX+j)*BLOCK_SIZE, (curY+i)*BLOCK_SIZE, pieceColors[currentPieceType], BLOCK_SIZE);
        }

        g.setColor(new Color(255,255,255,10));
        for(int i=0; i<=BOARD_WIDTH; i++) g.drawLine(i*BLOCK_SIZE, 0, i*BLOCK_SIZE, PANEL_HEIGHT);
        for(int i=0; i<=BOARD_HEIGHT; i++) g.drawLine(0, i*BLOCK_SIZE, BOARD_WIDTH*BLOCK_SIZE, i*BLOCK_SIZE);
        drawSidePanel(g);
    }

    private void drawSidePanel(Graphics2D g) {
        int x = BOARD_WIDTH * BLOCK_SIZE + 40;
        int previewScale = 25;

        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.drawString("GUARDADA (C):", x, 40);
        g.drawRect(x, 50, 4 * previewScale, 4 * previewScale);
        if (holdPieceType != 0) {
            for(int i=0; i<4; i++) for(int j=0; j<4; j++) if(holdPieceShape[i][j] != 0)
                drawBlock(g, x + (j*previewScale), 50 + (i*previewScale), canHold ? pieceColors[holdPieceType] : Color.GRAY, previewScale);
        }

        int yStats = 180;
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Consolas", Font.PLAIN, 16));
        g.drawString("Recorde: " + Math.max(score, highScore), x, yStats);
        g.setColor(Color.WHITE);
        g.drawString("Score: " + score, x, yStats + 25);
        g.drawString("Level: " + level, x, yStats + 50);
        g.drawString("Linhas: " + linesClearedTotal, x, yStats + 75);
        if (comboCount > 0) {
            g.setColor(Color.CYAN);
            g.setFont(new Font("Segoe UI", Font.BOLD, 20));
            g.drawString("COMBO x" + comboCount, x, yStats + 110);
            g.setFont(new Font("Consolas", Font.PLAIN, 16));
        }
        int yNext = yStats + 130;
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.drawString("PROXIMA:", x, yNext);
        for(int i=0; i<4; i++) for(int j=0; j<4; j++) if(nextPieceShape[i][j] != 0)
            drawBlock(g, x + (j*previewScale), yNext + 10 + (i*previewScale), pieceColors[nextPieceType], previewScale);
    }

    private void drawGameOverScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        g.setColor(Color.RED);
        g.setFont(new Font("Segoe UI", Font.BOLD, 40));
        drawCenteredString(g, "GAME OVER", 200);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        drawCenteredString(g, "Score Final: " + score, 250);
        if (score >= highScore && score > 0) {
            g.setColor(Color.YELLOW);
            drawCenteredString(g, "NOVO RECORDE!", 280);
        }
        g.setFont(new Font("Segoe UI", Font.BOLD, 25));
        for (int i = 0; i < gameOverOptions.length; i++) {
            if (i == gameOverOption) {
                g.setColor(Color.GREEN);
                drawCenteredString(g, "> " + gameOverOptions[i] + " <", 350 + (i * 50));
            } else {
                g.setColor(Color.GRAY);
                drawCenteredString(g, gameOverOptions[i], 350 + (i * 50));
            }
        }
    }

    private void drawPauseScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Segoe UI", Font.BOLD, 40));
        drawCenteredString(g, "PAUSADO", PANEL_HEIGHT / 2);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        drawCenteredString(g, "Pressione 'P' para Retomar", PANEL_HEIGHT / 2 + 40);
    }

    private void drawInstructions(Graphics2D g) {
        g.setColor(new Color(20, 20, 30));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        g.setColor(Color.CYAN);
        g.setFont(new Font("Segoe UI", Font.BOLD, 30));
        drawCenteredString(g, "TUTORIAL", 100);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        int y = 200;
        drawCenteredString(g, "SETAS: Mover e Girar", y); y+=30;
        drawCenteredString(g, "ESPACO: Queda Instantanea", y); y+=30;
        drawCenteredString(g, "TECLA 'C': Guardar Peca (Hold)", y); y+=30;
        drawCenteredString(g, "P: Pausar o jogo", y); y+=30;
        g.setColor(Color.YELLOW);
        drawCenteredString(g, "Pressione ESPACO para voltar", PANEL_HEIGHT - 100);
    }

    private void drawCredits(Graphics2D g) {
        g.setColor(new Color(20, 20, 30));
        g.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        g.setColor(Color.MAGENTA);
        g.setFont(new Font("Segoe UI", Font.BOLD, 30));
        drawCenteredString(g, "CREDITOS", 80);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        int y = 180;
        g.setColor(Color.ORANGE);
        drawCenteredString(g, "Criador Original do Tetris:", y);
        g.setColor(Color.WHITE);
        drawCenteredString(g, "Alexey Pajitnov (1984)", y + 25);
        y += 80;
        g.setColor(Color.CYAN);
        drawCenteredString(g, "Desenvolvimento Java & Swing:", y);
        g.setColor(Color.WHITE);
        drawCenteredString(g, "joaorizzo0112 (Github)", y + 25);

        drawCenteredString(g, "Pressione ESPACO para voltar", PANEL_HEIGHT - 50);
    }

    private void drawCenteredString(Graphics g, String text, int y) {
        FontMetrics metrics = g.getFontMetrics(g.getFont());
        int x = (PANEL_WIDTH - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    private void drawBlock(Graphics2D g, int x, int y, Color c, int size) {
        g.setColor(c);
        g.fillRect(x+1, y+1, size-2, size-2);
        g.setColor(new Color(255,255,255,100));
        g.fillRect(x+1, y+1, size-2, size/3);
    }

    private class TAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();
            if (currentState == State.MENU) {
                if (key == KeyEvent.VK_UP) { menuOption--; if (menuOption < 0) menuOption = menuOptions.length - 1; }
                else if (key == KeyEvent.VK_DOWN) { menuOption++; if (menuOption >= menuOptions.length) menuOption = 0; }
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    AudioPlayer.playMove();
                    if (menuOption == 0) startGame();
                    else if (menuOption == 1) currentState = State.INSTRUCTIONS;
                    else if (menuOption == 2) currentState = State.CREDITS;
                    else if (menuOption == 3) System.exit(0);
                }
            } else if (currentState == State.GAME_OVER) {
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_DOWN) {
                    gameOverOption = (gameOverOption == 0) ? 1 : 0;
                    AudioPlayer.playMove();
                } else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    if (gameOverOption == 0) startGame(); else { currentState = State.MENU; menuOption = 0; }
                }
            } else if (currentState == State.INSTRUCTIONS || currentState == State.CREDITS) {
                if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ESCAPE) currentState = State.MENU;
            } else if (currentState == State.PLAYING) {
                if (key == KeyEvent.VK_P) { isPaused = !isPaused; repaint(); return; }
                if (isPaused) return;
                switch (key) {
                    case KeyEvent.VK_LEFT: movePiece(-1, 0); break;
                    case KeyEvent.VK_RIGHT: movePiece(1, 0); break;
                    case KeyEvent.VK_DOWN: movePiece(0, 1); break;
                    case KeyEvent.VK_UP: rotate(); break;
                    case KeyEvent.VK_SPACE: hardDrop(); break;
                    case KeyEvent.VK_C: holdPiece(); break;
                }
            }
            repaint();
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Quadra");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new Quadra());
        frame.pack();
        
        frame.setMinimumSize(new Dimension(400, 400));
        frame.setLocationRelativeTo(null);
        
        frame.setVisible(true);
    }
}