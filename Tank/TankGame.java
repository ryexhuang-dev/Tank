import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;
import javax.sound.sampled.*;
import java.io.File;

public class TankGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainMenu::createGUI);
    }
}

/* =========================  美化主菜单  ===================== */
class MainMenu {
    static void createGUI() {
        JFrame f = new JFrame("TANK");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setResizable(false);

        JPanel panel = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(30, 30, 60), 0, getHeight(), Color.BLACK));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setPreferredSize(new Dimension(480, 360));

        JLabel title = new JLabel("TANK", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new Color(255, 215, 0));
                for (int i = -2; i <= 2; i++)
                    for (int j = -2; j <= 2; j++)
                        g2.drawString(getText(), i + getWidth() / 2 - 90, j + 70);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                g2.drawString(getText(), getWidth() / 2 - 90, 70);
                g2.dispose();
            }
        };
        title.setFont(new Font("Arial", Font.BOLD, 72));
        title.setBounds(0, 40, 480, 100);
        panel.add(title);

        JButton pvpBtn = createMenuBtn("PvP", 140);
        pvpBtn.addActionListener(e -> {
            f.dispose();
            TankGameWindow.startPvP();
        });
        panel.add(pvpBtn);

        JButton aiBtn = createMenuBtn("vs AI", 220);
        aiBtn.addActionListener(e -> showComingSoon(f));
        panel.add(aiBtn);

        f.add(panel);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    private static JButton createMenuBtn(String text, int y) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setColor(getForeground());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 25, 25);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        btn.setBounds(140, y, 200, 50);
        btn.setFont(new Font("Arial", Font.BOLD, 26));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(70, 130, 180));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(100, 160, 210)); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(new Color(70, 130, 180)); }
        });
        return btn;
    }

    private static void showComingSoon(JFrame parent) {
        JDialog dlg = new JDialog(parent, "Coming Soon", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JLabel msg = new JLabel("Coming soon…", SwingConstants.CENTER);
        msg.setFont(new Font("Arial", Font.BOLD, 32));
        msg.setPreferredSize(new Dimension(320, 160));
        dlg.add(msg); dlg.pack(); dlg.setLocationRelativeTo(parent);
        new Timer(3000, e -> dlg.dispose()).start();
        dlg.setVisible(true);
    }
}

/* =========================  游戏窗口  ===================== */
class TankGameWindow {
    static void startPvP() {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Tank Trouble");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setResizable(false);
            f.add(new TankPanel());
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}

/* =========================  游戏面板  ===================== */
class TankPanel extends JPanel implements ActionListener {
    public static final int WIDTH = 800, HEIGHT = 600, STREET = 80, WALL_THICK = 20;

    private List<Wall> walls;
    private final Tank tank1, tank2;
    private final Timer timer = new Timer(16, this);
    private String winner = null;
    private boolean waitingRestart = false;

    /* 音效 */
    private static Clip shootSound;
    private static Clip explodeSound;
    private static Clip bgm;
    static {
        shootSound = loadClip("shoot.wav");
        explodeSound = loadClip("explode.wav");
        bgm        = loadClip("bgm.wav");
    }

    public TankPanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.LIGHT_GRAY);
        setFocusable(true);
        walls = MapGenerator.generateMap();
        Point p1 = findSpawn(null);
        Rectangle r1 = new Rectangle(p1.x, p1.y, 30, 30);
        Point p2 = findSpawn(r1);

        tank1 = new Tank(p1.x, p1.y, Color.RED, walls,
        KeyEvent.VK_W, KeyEvent.VK_S, KeyEvent.VK_A, KeyEvent.VK_D,
        KeyEvent.VK_F, "RED");          // 红坦克 F 开火

        tank2 = new Tank(p2.x, p2.y, Color.BLUE, walls,
        KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
        KeyEvent.VK_L, "BLUE");         // 蓝坦克 L 开火

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (waitingRestart && e.getKeyCode() == KeyEvent.VK_SPACE) { restart(); return; }
                tank1.keyPressed(e); tank2.keyPressed(e);
            }
            public void keyReleased(KeyEvent e) { tank1.keyReleased(e); tank2.keyReleased(e); }
        });

        timer.start();
        requestFocusInWindow();
        loopClip(bgm);   // 背景音乐
    }

    /* ------------ 音频工具 ------------ */
    private static Clip loadClip(String file) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new File(file));
            Clip c = AudioSystem.getClip();
            c.open(ais);
            return c;
        } catch (Exception ex) {
            System.out.println("⚠️  音频文件缺失: " + file);
            return null;
        }
    }
    private static void loopClip(Clip c)   { if (c != null) { c.setFramePosition(0); c.loop(Clip.LOOP_CONTINUOUSLY); } }
    public  static void playShootOnce()    { if (shootSound != null) { shootSound.setFramePosition(0); shootSound.start(); } }
    private        void playExplodeOnce()  { if (explodeSound != null) { explodeSound.setFramePosition(0); explodeSound.start(); } }

    /* ------------ 游戏逻辑 ------------ */
    private Point findSpawn(Rectangle avoid) {
        Random rnd = new Random();
        while (true) {
            int x = STREET + rnd.nextInt(WIDTH - 2 * STREET - 30);
            int y = STREET + rnd.nextInt(HEIGHT - 2 * STREET - 30);
            Rectangle r = new Rectangle(x, y, 30, 30);
            boolean ok = true;
            for (Wall w : walls) if (r.intersects(w.getBounds())) { ok = false; break; }
            if (ok && (avoid == null || !r.intersects(avoid))) return new Point(x, y);
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (waitingRestart || winner != null) return;
        tank1.move(); tank2.move();
        tank1.tryFire(); tank2.tryFire();
        updateBullets();
        repaint();
    }

    private void updateBullets() {
        /* 1. move + boundary/wall */
        for (Bullet b : tank1.bullets) {
            b.move(); b.decBirth();
            if (b.outOfBounds()) { b.hit(); continue; }
            for (Wall w : walls)
                if (b.getBounds().intersects(w.getBounds())) {
                    if (w.w > w.h) b.bounceY(); else b.bounceX();
                    break;
                }
        }
        for (Bullet b : tank2.bullets) {
            b.move(); b.decBirth();
            if (b.outOfBounds()) { b.hit(); continue; }
            for (Wall w : walls)
                if (b.getBounds().intersects(w.getBounds())) {
                    if (w.w > w.h) b.bounceY(); else b.bounceX();
                    break;
                }
        }
        /* 2. hit enemy only */
        for (Bullet b : tank2.bullets)
            if (tank1.alive && b.canHit(tank1) && b.getBounds().intersects(tank1.getBounds())) {
                tank1.alive = false; checkWin(); b.hit();
            }
        for (Bullet b : tank1.bullets)
            if (tank2.alive && b.canHit(tank2) && b.getBounds().intersects(tank2.getBounds())) {
                tank2.alive = false; checkWin(); b.hit();
            }
        /* 3. remove dead */
        tank1.bullets.removeIf(Bullet::isDead);
        tank2.bullets.removeIf(Bullet::isDead);
    }

    private void checkWin() {
        if (!tank1.alive) winner = "BLUE WINS!";
        if (!tank2.alive) winner = "RED WINS!";
        if (winner != null) {
            waitingRestart = true;
            playExplodeOnce();
            repaint();
        }
    }

    private void restart() {
    winner = null;
    waitingRestart = false;

    /* 全新随机地图 */
    walls = MapGenerator.generateMap();

    /* 重新找出生点 */
    Point p1 = findSpawn(null);
    Rectangle r1 = new Rectangle(p1.x, p1.y, Tank.SIZE, Tank.SIZE);
    Point p2 = findSpawn(r1);

    /* 重置坦克并传入新地图 */
    tank1.reset(p1.x, p1.y);
    tank2.reset(p2.x, p2.y);
    tank1.bullets.clear();
    tank2.bullets.clear();
    tank1.setWalls(walls);   // 关键：让坦克引用新墙列表
    tank2.setWalls(walls);
}

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        walls.forEach(w -> w.draw(g));
        if (tank1.alive) tank1.draw(g);
        if (tank2.alive) tank2.draw(g);
        tank1.bullets.forEach(b -> b.draw(g));
        tank2.bullets.forEach(b -> b.draw(g));

        if (winner != null) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 60));
            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(winner);
            g.drawString(winner, (getWidth() - sw) / 2, getHeight() / 2);
        }
        if (waitingRestart) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            String tip = "Press SPACE to continue";
            int sw = g.getFontMetrics().stringWidth(tip);
            g.drawString(tip, (getWidth() - sw) / 2, getHeight() / 2 + 80);
        }
    }
}

/* =========================  Tank  ===================== */
class Tank {
    void setWalls(List<Wall> newWalls) { this.walls = newWalls; }
    public static final int SIZE = 30;
    int x, y, spd = 3, size = 30;
    private int upK, dnK, ltK, rtK, fireK;
    private boolean up, dn, lt, rt, fire;
    private int dir = 1;
    private final Color color;
    private final String id;
    public final List<Bullet> bullets = new ArrayList<>();
    boolean alive = true;
    private long lastFire = 0;
    private static final int CD = 300;
    private List<Wall> walls;

    Tank(int x, int y, Color color, List<Wall> walls,
         int upK, int dnK, int ltK, int rtK, int fireK, String id) {
        this.x = x; this.y = y; this.color = color; this.walls = walls;
        this.upK = upK; this.dnK = dnK; this.ltK = ltK; this.rtK = rtK;
        this.fireK = fireK; this.id = id;
    }

    void reset(int x, int y) { this.x = x; this.y = y; alive = true; bullets.clear(); }

    void move() {
        int vx = 0, vy = 0;
        if (up) vy -= spd;
        if (dn) vy += spd;
        if (lt) vx -= spd;
        if (rt) vx += spd;
        if (vx != 0 && vy != 0) {
            vx = (int) Math.round(vx / 1.4142);
            vy = (int) Math.round(vy / 1.4142);
        }
        if (canMove(x + vx, y)) x += vx;
        if (canMove(x, y + vy)) y += vy;

        if (vx > 0 && vy < 0) dir = 1;
        else if (vx > 0 && vy > 0) dir = 3;
        else if (vx < 0 && vy > 0) dir = 5;
        else if (vx < 0 && vy < 0) dir = 7;
        else if (vx > 0) dir = 2;
        else if (vx < 0) dir = 6;
        else if (vy > 0) dir = 4;
        else if (vy < 0) dir = 0;
    }

    private boolean canMove(int nx, int ny) {
        Rectangle r = new Rectangle(nx, ny, size, size);
        for (Wall w : walls) if (r.intersects(w.getBounds())) return false;
        return true;
    }

    void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x + size / 2, y + size / 2);
        g2.setColor(color.darker());
        g2.fillRect(-size / 2, -size / 2, size, size);
        g2.setColor(color);
        g2.fillRect(-size / 2 + 4, -size / 2 + 4, size - 8, size - 8);
        g2.rotate(Math.toRadians(dir * 45));
        g2.setColor(Color.WHITE);
        g2.fillRect(-2, -size / 2 - 10, 4, 12);
        g2.dispose();
    }

    Rectangle getBounds() { return new Rectangle(x, y, size, size); }

    void keyPressed(KeyEvent e)  {
        int c = e.getKeyCode();
        if (c == upK) up = true;
        if (c == dnK) dn = true;
        if (c == ltK) lt = true;
        if (c == rtK) rt = true;
        if (c == fireK) { fire = true; TankPanel.playShootOnce(); }
    }

    void keyReleased(KeyEvent e) {
        int c = e.getKeyCode();
        if (c == upK) up = false;
        if (c == dnK) dn = false;
        if (c == ltK) lt = false;
        if (c == rtK) rt = false;
        if (c == fireK) fire = false;
    }

    void tryFire() {
        if (!fire || !alive) return;
        long now = System.currentTimeMillis();
        if (now - lastFire < CD) return;
        lastFire = now;
        fire = false;

        int cx = x + size / 2;
        int cy = y + size / 2;
        int bx = 0, by = 0;
        switch (dir) {
            case 0 -> by = -6;
            case 1 -> { bx = 4; by = -4; }
            case 2 -> bx = 6;
            case 3 -> { bx = 4; by = 4; }
            case 4 -> by = 6;
            case 5 -> { bx = -4; by = 4; }
            case 6 -> bx = -6;
            case 7 -> { bx = -4; by = -4; }
        }
        bullets.add(new Bullet(cx, cy, bx, by));
    }
}

/* =========================  Bullet  ===================== */
class Bullet {
    private int x, y, dx, dy;
    private int life = 9;
    private int birth = 2;
    private static final int R = 4;

    Bullet(int x, int y, int dx, int dy) {
        this.x = x; this.y = y; this.dx = dx; this.dy = dy;
    }

    void move() { x += dx; y += dy; }

    void draw(Graphics g) {
        g.setColor(Color.YELLOW);
        g.fillOval(x - R, y - R, 2 * R, 2 * R);
    }

    Rectangle getBounds() { return new Rectangle(x - R, y - R, 2 * R, 2 * R); }

    boolean outOfBounds() { return x < 0 || x > TankPanel.WIDTH || y < 0 || y > TankPanel.HEIGHT; }

    void bounceX() { dx = -dx; hit(); }
    void bounceY() { dy = -dy; hit(); }

    void hit() { life--; }
    boolean isDead() { return life <= 0; }
    void decBirth() { if (birth > 0) birth--; }
    boolean canHit(Tank t) { return birth == 0; }
}

/* =========================  Wall / MapGenerator  ===================== */
class Wall {
    final int x, y, w, h;
    Wall(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    void draw(Graphics g) { g.setColor(Color.BLACK); g.fillRect(x, y, w, h); }
    Rectangle getBounds() { return new Rectangle(x, y, w, h); }
}

class MapGenerator {
    static List<Wall> generateMap() {
        List<Wall> walls = new ArrayList<>();
        int W = TankPanel.WIDTH, H = TankPanel.HEIGHT;
        int t = TankPanel.STREET;

        /* ===== 1. 随机行列（大小） ===== */
        Random rnd = new Random();
        int cols = 6 + rnd.nextInt(5);   // 6~10
        int rows = 4 + rnd.nextInt(4);   // 4~7

        /* ===== 2. 重新计算单元格尺寸 ===== */
        int cw = (W - 2 * t) / cols;
        int ch = (H - 2 * t) / rows;
        int[] X = new int[cols + 1];
        int[] Y = new int[rows + 1];
        for (int i = 0; i <= cols; i++) X[i] = t + i * cw;
        for (int j = 0; j <= rows; j++) Y[j] = t + j * ch;

        /* ===== 3. 后面与原流程完全相同 ===== */
        boolean[][] v = new boolean[cols + 1][rows];
        boolean[][] h = new boolean[cols][rows + 1];
        for (int i = 0; i <= cols; i++) java.util.Arrays.fill(v[i], true);
        for (int i = 0; i < cols; i++) java.util.Arrays.fill(h[i], true);

        boolean[][] vis = new boolean[cols][rows];
        int sx1 = 1, sy1 = 1, sx2 = cols - 2, sy2 = rows - 2;
        vis[sx1][sy1] = vis[sx2][sy2] = true;
        Stack<int[]> s = new Stack<>();
        s.push(new int[]{sx1, sy1});
        s.push(new int[]{sx2, sy2});
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!s.isEmpty()) {
            int[] cur = s.peek();
            List<int[]> nbrs = new ArrayList<>();
            for (int[] k : d) {
                int nx = cur[0] + k[0], ny = cur[1] + k[1];
                if (nx >= 0 && nx < cols && ny >= 0 && ny < rows && !vis[nx][ny])
                    nbrs.add(new int[]{nx, ny});
            }
            if (!nbrs.isEmpty()) {
                int[] nb = nbrs.get(rnd.nextInt(nbrs.size()));
                int nx = nb[0], ny = nb[1];
                if (nx == cur[0] + 1) v[cur[0] + 1][cur[1]] = false;
                else if (nx == cur[0] - 1) v[cur[0]][cur[1]] = false;
                else if (ny == cur[1] + 1) h[cur[0]][cur[1] + 1] = false;
                else if (ny == cur[1] - 1) h[cur[0]][cur[1]] = false;
                vis[nx][ny] = true;
                s.push(nb);
            } else s.pop();
        }

        /* ===== 4. 额外拆墙 ===== */
        int extraPaths = (cols * rows) / 8;
        for (int i = 0; i < extraPaths; i++) {
            boolean vertical = rnd.nextBoolean();
            if (vertical) {
                int ix = 1 + rnd.nextInt(cols - 1);
                int jy = 1 + rnd.nextInt(rows - 1);
                if (v[ix][jy]) v[ix][jy] = false;
            } else {
                int ix = 1 + rnd.nextInt(cols - 1);
                int jy = 1 + rnd.nextInt(rows - 1);
                if (h[ix][jy]) h[ix][jy] = false;
            }
        }

        /* ===== 5. 建墙 ===== */
        int hf = TankPanel.WALL_THICK / 2;
        for (int i = 0; i < cols; i++)
            for (int j = 0; j <= rows; j++)
                if (h[i][j]) walls.add(new Wall(X[i] - hf, Y[j] - hf,
                        X[i + 1] - X[i] + TankPanel.WALL_THICK, TankPanel.WALL_THICK));
        for (int i = 0; i <= cols; i++)
            for (int j = 0; j < rows; j++)
                if (v[i][j]) walls.add(new Wall(X[i] - hf, Y[j] - hf,
                        TankPanel.WALL_THICK, Y[j + 1] - Y[j] + TankPanel.WALL_THICK));

        /* ===== 6. 边界 ===== */
        walls.add(new Wall(0, 0, W, TankPanel.WALL_THICK));
        walls.add(new Wall(0, H - TankPanel.WALL_THICK, W, TankPanel.WALL_THICK));
        walls.add(new Wall(0, 0, TankPanel.WALL_THICK, H));
        walls.add(new Wall(W - TankPanel.WALL_THICK, 0, TankPanel.WALL_THICK, H));

        return walls;
    }
}