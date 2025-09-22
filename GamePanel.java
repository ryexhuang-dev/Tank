import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class GamePanel {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("坦克动荡");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(new TankPanel());
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}

/*--------------------------------------------------------
 *  游戏面板
 *------------------------------------------------------*/
class TankPanel extends JPanel implements ActionListener {
    public static final int WIDTH  = 800;
    public static final int HEIGHT = 600;
    public static final int STREET = 80;       // 街道宽度
    public static final int WALL_THICK = 20;   // 墙厚度

    private Tank tank1, tank2;
    private final List<Wall> walls;
    private final Timer timer;
    private String winner = null;

    public TankPanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.LIGHT_GRAY); // 街道灰色
        setFocusable(true);

        walls = MapGenerator.generateMap();

        // 生成坦克出生点
        Point spawn1 = findSpawn(null);
        Rectangle redRect = new Rectangle(spawn1.x, spawn1.y, 30, 30);
        Point spawn2 = findSpawn(redRect);

        tank1 = new Tank(spawn1.x, spawn1.y, Color.RED, walls,
                KeyEvent.VK_W, KeyEvent.VK_S,
                KeyEvent.VK_A, KeyEvent.VK_D,
                KeyEvent.VK_SPACE, "红");

        tank2 = new Tank(spawn2.x, spawn2.y, Color.BLUE, walls,
                KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                KeyEvent.VK_NUMPAD0, "蓝");

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e)  {
                tank1.keyPressed(e);  tank2.keyPressed(e);
            }
            @Override public void keyReleased(KeyEvent e) {
                tank1.keyReleased(e); tank2.keyReleased(e);
            }
        });

        timer = new Timer(16, this);
        timer.start();

        requestFocusInWindow(); 
    }

    // 随机生成坦克出生点，不与 walls 或 avoid 碰撞
    private Point findSpawn(Rectangle avoid) {
        Random rand = new Random();
        while (true) {
            int x = STREET + rand.nextInt(WIDTH - 2 * STREET - 30);
            int y = STREET + rand.nextInt(HEIGHT - 2 * STREET - 30);
            Rectangle rect = new Rectangle(x, y, 30, 30);
            boolean collide = false;
            for (Wall w : walls) {
                if (rect.intersects(w.getBounds())) { collide = true; break; }
            }
            if (avoid != null && rect.intersects(avoid)) collide = true;
            if (!collide) return new Point(x, y);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (winner != null) return;
        tank1.move(); tank2.move();
        tank1.tryFire(); tank2.tryFire();
        updateBullets();
        repaint();
    }

    private void updateBullets() {
        List<Bullet> all = new ArrayList<>();
        all.addAll(tank1.bullets);
        all.addAll(tank2.bullets);

        for (Bullet b : all) {
            b.move();
            if (b.outOfBounds()) { b.kill(); continue; }

            Rectangle bb = b.getBounds();
            for (Wall w : walls) {
                if (bb.intersects(w.getBounds())) {
                    if (w.w > w.h) b.bounceY(); else b.bounceX();
                    break;
                }
            }
            if (tank1.alive && tank2.bullets.contains(b) && tank1.getBounds().intersects(bb)) {
                tank1.alive = false; b.kill(); checkWin();
            }
            if (tank2.alive && tank1.bullets.contains(b) && tank2.getBounds().intersects(bb)) {
                tank2.alive = false; b.kill(); checkWin();
            }
        }
        tank1.bullets.removeIf(Bullet::isDead);
        tank2.bullets.removeIf(Bullet::isDead);
    }

    private void checkWin() {
        if (!tank1.alive) winner = "蓝方获胜！";
        if (!tank2.alive) winner = "红方获胜！";
        if (winner != null) {
            repaint();
            Timer t = new Timer(3000, e -> restart());
            t.setRepeats(false);
            t.start();
        }
    }

    private void restart() {
        winner = null;
        Point spawn1 = findSpawn(null);
        Rectangle redRect = new Rectangle(spawn1.x, spawn1.y, 30, 30);
        Point spawn2 = findSpawn(redRect);
        tank1.reset(spawn1.x, spawn1.y);
        tank2.reset(spawn2.x, spawn2.y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        for (Wall w : walls) w.draw(g);
        if (tank1.alive) tank1.draw(g);
        if (tank2.alive) tank2.draw(g);
        tank1.bullets.forEach(b -> b.draw(g));
        tank2.bullets.forEach(b -> b.draw(g));

        if (winner != null) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("微软雅黑", Font.BOLD, 60));
            FontMetrics fm = g.getFontMetrics();
            int sw = fm.stringWidth(winner);
            g.drawString(winner, (getWidth() - sw) / 2, getHeight() / 2);
        }
    }
}

/*  Tank */
class Tank {
    int x, y, spd = 3, size = 30;
    private int upK, dnK, ltK, rtK, fireK;
    private boolean up, dn, lt, rt, fire;
    private int dir = 0;
    private final Color color;
    private final String id;
    public final List<Bullet> bullets = new ArrayList<>();
    boolean alive = true;
    private long lastFire = 0;
    private static final int CD = 300;
    private final List<Wall> walls;

    Tank(int x, int y, Color color, List<Wall> walls,
         int upK, int dnK, int ltK, int rtK, int fireK, String id) {
        this.x = x; this.y = y; this.color = color; this.walls = walls;
        this.upK = upK; this.dnK = dnK; this.ltK = ltK; this.rtK = rtK;
        this.fireK = fireK; this.id = id;
    }

    void reset(int x, int y) {
        this.x = x; this.y = y; alive = true;
        bullets.clear();
    }

    void move() {
        int nx = x, ny = y;
        if (up) { if (dir != 0) { dir = 0; return; } ny -= spd; }
        if (dn) { if (dir != 2) { dir = 2; return; } ny += spd; }
        if (lt) { if (dir != 3) { dir = 3; return; } nx -= spd; }
        if (rt) { if (dir != 1) { dir = 1; return; } nx += spd; }
        if (canMove(nx, ny)) { x = nx; y = ny; }
    }

    private boolean canMove(int nx, int ny) {
        Rectangle next = new Rectangle(nx, ny, size, size);
        for (Wall w : walls) if (next.intersects(w.getBounds())) return false;
        return true;
    }

    void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        g2.setColor(color.darker());
        g2.fillRect(0, 0, size, size);
        g2.setColor(color);
        g2.fillRect(4, 4, size - 8, size - 8);

        g2.setColor(Color.WHITE);
        switch (dir) {
            case 0 -> g2.fillRect(size / 2 - 2, -10, 4, 12);
            case 1 -> g2.fillRect(size, size / 2 - 2, 12, 4);
            case 2 -> g2.fillRect(size / 2 - 2, size, 4, 12);
            case 3 -> g2.fillRect(-10, size / 2 - 2, 12, 4);
        }
        g2.dispose();
    }

    Rectangle getBounds() { return new Rectangle(x, y, size, size); }

    void keyPressed(KeyEvent e)  {
        int c = e.getKeyCode();
        if (c == upK) up = true;
        if (c == dnK) dn = true;
        if (c == ltK) lt = true;
        if (c == rtK) rt = true;
        if (c == fireK) fire = true;
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
        lastFire = now; fire = false;

        int cx = x + size / 2;
        int cy = y + size / 2;
        int bx = 0, by = -6;
        switch (dir) {
            case 1 -> { bx = 6; by = 0; }
            case 2 -> { bx = 0; by = 6; }
            case 3 -> { bx = -6; by = 0; }
        }
        bullets.add(new Bullet(cx, cy, bx, by, 4));
    }
}

/*  Bullet */
class Bullet {
    private int x, y, dx, dy, bounce;
    private static final int R = 4;
    Bullet(int x, int y, int dx, int dy, int bounce) {
        this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.bounce = bounce;
    }
    void move()  { x += dx; y += dy; }
    void draw(Graphics g) { g.setColor(Color.YELLOW); g.fillOval(x - R, y - R, 2 * R, 2 * R); }
    Rectangle getBounds() { return new Rectangle(x - R, y - R, 2 * R, 2 * R); }
    boolean outOfBounds() { return x < 0 || x > TankPanel.WIDTH || y < 0 || y > TankPanel.HEIGHT; }
    void bounceX() { dx = -dx; bounce--; }
    void bounceY() { dy = -dy; bounce--; }
    void kill() { bounce = -1; }
    boolean isDead() { return bounce < 0; }
}

/*  Wall + MapGenerator */
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
        int t = TankPanel.STREET, cols = 8, rows = 6;
        int cw = (W - 2 * t) / cols, ch = (H - 2 * t) / rows;
        int[] X = new int[cols + 1], Y = new int[rows + 1];
        for (int i = 0; i <= cols; i++) X[i] = t + i * cw;
        for (int j = 0; j <= rows; j++) Y[j] = t + j * ch;

        boolean[][] v = new boolean[cols + 1][rows];
        boolean[][] h = new boolean[cols][rows + 1];
        for (int i = 0; i <= cols; i++) java.util.Arrays.fill(v[i], true);
        for (int i = 0; i < cols; i++)  java.util.Arrays.fill(h[i], true);

        boolean[][] vis = new boolean[cols][rows];
        int sx1 = 1, sy1 = 1;
        int sx2 = cols - 2, sy2 = rows - 2;
        vis[sx1][sy1] = true;
        vis[sx2][sy2] = true;

        Stack<int[]> s = new Stack<>();
        s.push(new int[]{sx1, sy1});
        s.push(new int[]{sx2, sy2});

        Random r = new Random();
        int[][] d = {{1,0},{-1,0},{0,1},{0,-1}};

        while (!s.isEmpty()) {
            int[] cur = s.peek();
            List<int[]> nbrs = new ArrayList<>();
            for (int[] k : d) {
                int nx = cur[0] + k[0], ny = cur[1] + k[1];
                if (nx >= 0 && nx < cols && ny >= 0 && ny < rows && !vis[nx][ny])
                    nbrs.add(new int[]{nx, ny});
            }
            if (!nbrs.isEmpty()) {
                int[] nb = nbrs.get(r.nextInt(nbrs.size()));
                int nx = nb[0], ny = nb[1];
                if (nx == cur[0] + 1) v[cur[0] + 1][cur[1]] = false;
                else if (nx == cur[0] - 1) v[cur[0]][cur[1]] = false;
                else if (ny == cur[1] + 1) h[cur[0]][cur[1] + 1] = false;
                else if (ny == cur[1] - 1) h[cur[0]][cur[1]] = false;
                vis[nx][ny] = true; s.push(nb);
            } else s.pop();
        }

        int hf = TankPanel.WALL_THICK / 2;
        for (int i = 0; i < cols; i++)
            for (int j = 0; j <= rows; j++)
                if (h[i][j]) walls.add(new Wall(X[i] - hf, Y[j] - hf,
                        X[i + 1] - X[i] + TankPanel.WALL_THICK, TankPanel.WALL_THICK));
        for (int i = 0; i <= cols; i++)
            for (int j = 0; j < rows; j++)
                if (v[i][j]) walls.add(new Wall(X[i] - hf, Y[j] - hf,
                        TankPanel.WALL_THICK, Y[j + 1] - Y[j] + TankPanel.WALL_THICK));

        walls.add(new Wall(0, 0, W, TankPanel.WALL_THICK));
        walls.add(new Wall(0, H - TankPanel.WALL_THICK, W, TankPanel.WALL_THICK));
        walls.add(new Wall(0, 0, TankPanel.WALL_THICK, H));
        walls.add(new Wall(W - TankPanel.WALL_THICK, 0, TankPanel.WALL_THICK, H));
        return walls;
    }
}
