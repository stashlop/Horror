package com.hollowhalls.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Random;

/**
 * Hollow Halls, rendered with a software raycaster into a low-resolution pixel
 * buffer that is blitted up to the screen. Gameplay constants are the Unity
 * ones halved, because one grid square here is two Unity world units.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // --- tuning (grid units; 1 grid square = 2 Unity world units) ----------
    private static final int MAZE_W = 9, MAZE_H = 9;
    private static final float MOVE_SPEED = 1.6f;
    private static final float ENEMY_SPEED = 1.3f;
    private static final float ENEMY_REPATH = 0.4f;
    private static final float CATCH_DIST = 0.7f;
    private static final float HEARTBEAT_RANGE = 6f;
    private static final float PLAYER_RADIUS = 0.22f;
    private static final float LOOK_SENS = 0.15f;

    private static final int STATE_TITLE = 0, STATE_PLAYING = 1, STATE_CAUGHT = 2, STATE_WIN = 3;

    // --- render targets ---------------------------------------------------
    private Renderer renderer;
    private Bitmap frame;
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();
    private final Paint blitPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint();

    // --- world state ------------------------------------------------------
    private Maze maze;
    private final Random rng = new Random();
    private float px, py, ang, pitchDeg;
    private float enemyX, enemyY;
    private int enemyCellX, enemyCellY, enemyTargetX, enemyTargetY;
    private float repathTimer;
    private boolean enemyActive;
    private boolean flashlightOn = true;
    private int state = STATE_TITLE;
    private float flashAlpha = 0f;
    private float elapsed = 0f;

    // --- input ------------------------------------------------------------
    private int moveFinger = -1, lookFinger = -1;
    private float moveOriginX, moveOriginY, lastLookX, lastLookY;
    private volatile float moveInX, moveInY;
    private volatile float lookDX, lookDY;
    private volatile boolean tapPending;
    private volatile boolean toggleLightPending;

    private final GameAudio audio = new GameAudio();
    private Thread thread;
    private volatile boolean running;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        blitPaint.setFilterBitmap(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        newGame();
    }

    private void newGame() {
        maze = new Maze(MAZE_W, MAZE_H, rng);
        if (renderer == null) renderer = new Renderer(maze);
        else renderer.setMaze(maze);
        px = maze.cellCenterX(maze.startX);
        py = maze.cellCenterY(maze.startY);
        ang = 0f;
        pitchDeg = 0f;
        flashlightOn = true;
        flashAlpha = 0f;

        int[] spawn = maze.enemySpawnCell();
        enemyCellX = enemyTargetX = spawn[0];
        enemyCellY = enemyTargetY = spawn[1];
        enemyX = maze.cellCenterX(spawn[0]);
        enemyY = maze.cellCenterY(spawn[1]);
        repathTimer = 0f;
        enemyActive = true;
    }

    // --- surface lifecycle -------------------------------------------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        audio.start();
        running = true;
        thread = new Thread(this, "HollowHallsLoop");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        int rw = Math.max(120, Math.min(360, width));
        int rh = Math.max(120, Math.round(rw * (float) height / (float) width));
        renderer.resize(rw, rh);
        frame = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888);
        srcRect.set(0, 0, rw, rh);
        dstRect.set(0, 0, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try {
            if (thread != null) thread.join(1000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        thread = null;
        audio.release();
    }

    // --- main loop ---------------------------------------------------------

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            if (dt > 0.1f) dt = 0.1f;

            update(dt);

            SurfaceHolder holder = getHolder();
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    renderFrame(canvas);
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    private void update(float dt) {
        elapsed += dt;
        flashAlpha = Math.max(0f, flashAlpha - dt * 1.2f);

        if (tapPending) {
            tapPending = false;
            if (state == STATE_TITLE) {
                state = STATE_PLAYING;
            } else if (state == STATE_CAUGHT || state == STATE_WIN) {
                newGame();
                state = STATE_PLAYING;
            }
        }
        if (toggleLightPending) {
            toggleLightPending = false;
            if (state == STATE_PLAYING) flashlightOn = !flashlightOn;
        }

        if (state == STATE_PLAYING) {
            updateLook();
            updateMove(dt);
            updateEnemy(dt);
            checkGoal();
        }

        float d = enemyActive ? dist(px, py, enemyX, enemyY) : 999f;
        audio.updateProximity(d, HEARTBEAT_RANGE, state == STATE_PLAYING);
    }

    private void updateLook() {
        float dx = lookDX, dy = lookDY;
        lookDX = 0f;
        lookDY = 0f;
        ang -= dx * LOOK_SENS * (float) Math.PI / 180f;
        pitchDeg -= dy * LOOK_SENS;
        if (pitchDeg > 45f) pitchDeg = 45f;
        if (pitchDeg < -45f) pitchDeg = -45f;
    }

    private void updateMove(float dt) {
        float mx = moveInX, my = moveInY;
        float mag = (float) Math.sqrt(mx * mx + my * my);
        if (mag > 1f) { mx /= mag; my /= mag; }
        if (mag < 0.001f) return;

        float dirX = (float) Math.cos(ang), dirY = (float) Math.sin(ang);
        float rightX = dirY, rightY = -dirX;

        float vx = (dirX * my + rightX * mx) * MOVE_SPEED * dt;
        float vy = (dirY * my + rightY * mx) * MOVE_SPEED * dt;

        if (!blocked(px + vx + Math.signum(vx) * PLAYER_RADIUS, py)) px += vx;
        if (!blocked(px, py + vy + Math.signum(vy) * PLAYER_RADIUS)) py += vy;
    }

    private boolean blocked(float x, float y) {
        return maze.isSolid((int) Math.floor(x), (int) Math.floor(y));
    }

    private void updateEnemy(float dt) {
        if (!enemyActive) return;

        repathTimer -= dt;
        if (repathTimer <= 0f) {
            repathTimer = ENEMY_REPATH;
            int pcx = clampCellX(maze.gridToCellX(px));
            int pcy = clampCellY(maze.gridToCellY(py));
            int[] step = maze.nextStepTowards(enemyCellX, enemyCellY, pcx, pcy);
            enemyTargetX = step[0];
            enemyTargetY = step[1];
        }

        float tx = maze.cellCenterX(enemyTargetX), ty = maze.cellCenterY(enemyTargetY);
        float dx = tx - enemyX, dy = ty - enemyY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float stepLen = ENEMY_SPEED * dt;
        if (d <= stepLen) {
            enemyX = tx;
            enemyY = ty;
            enemyCellX = enemyTargetX;
            enemyCellY = enemyTargetY;
        } else {
            enemyX += dx / d * stepLen;
            enemyY += dy / d * stepLen;
        }

        if (dist(px, py, enemyX, enemyY) <= CATCH_DIST) {
            enemyActive = false;
            state = STATE_CAUGHT;
            flashAlpha = 1f;
            audio.playStinger();
        }
    }

    private void checkGoal() {
        float gx = maze.cellCenterX(maze.goalX), gy = maze.cellCenterY(maze.goalY);
        if (Math.abs(px - gx) < 0.8f && Math.abs(py - gy) < 0.8f) {
            state = STATE_WIN;
            enemyActive = false;
        }
    }

    private int clampCellX(int v) { return Math.max(0, Math.min(maze.width - 1, v)); }
    private int clampCellY(int v) { return Math.max(0, Math.min(maze.height - 1, v)); }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // --- rendering ---------------------------------------------------------

    private void renderFrame(Canvas canvas) {
        if (renderer == null || !renderer.ready()) return;
        renderer.render(px, py, ang, pitchDeg, flashlightOn, elapsed, enemyX, enemyY, enemyActive);
        frame.setPixels(renderer.pixels, 0, renderer.rw, 0, 0, renderer.rw, renderer.rh);
        canvas.drawBitmap(frame, srcRect, dstRect, blitPaint);
        drawOverlay(canvas);
    }

    // --- 2D overlay --------------------------------------------------------

    private void drawOverlay(Canvas canvas) {
        int w = canvas.getWidth(), h = canvas.getHeight();

        if (state == STATE_PLAYING) {
            // faint flashlight affordance in the top-right toggle zone
            fillPaint.setColor(flashlightOn ? Color.argb(55, 255, 240, 200) : Color.argb(40, 120, 120, 120));
            float cx = w - dp(46), cy = dp(46);
            canvas.drawCircle(cx, cy, dp(15), fillPaint);
        } else {
            int shade = state == STATE_CAUGHT ? Color.argb(217, 102, 0, 0) : Color.argb(191, 0, 0, 0);
            canvas.drawColor(shade);

            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(w * 0.085f);
            String title = state == STATE_TITLE ? "HOLLOW HALLS"
                    : state == STATE_CAUGHT ? "IT FOUND YOU" : "YOU ESCAPED";
            canvas.drawText(title, w / 2f, h * 0.3f, textPaint);

            textPaint.setFakeBoldText(false);
            textPaint.setColor(Color.argb(255, 217, 217, 217));
            textPaint.setTextSize(w * 0.042f);
            String[] lines = state == STATE_TITLE
                    ? new String[]{
                    "Left side of the screen: walk",
                    "Right side: drag to look",
                    "Tap the top-right corner: flashlight",
                    "",
                    "Something hunts these halls.",
                    "Find the glowing exit before it finds you.",
                    "",
                    "TAP TO BEGIN"}
                    : new String[]{state == STATE_CAUGHT ? "tap to try again" : "tap to play again"};

            float y = state == STATE_TITLE ? h * 0.45f : h * 0.45f;
            for (String line : lines) {
                canvas.drawText(line, w / 2f, y, textPaint);
                y += textPaint.getTextSize() * 1.55f;
            }
        }

        if (flashAlpha > 0f) {
            canvas.drawColor(Color.argb((int) (255 * Math.min(1f, flashAlpha)), 255, 255, 255));
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    // --- touch input --------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int w = getWidth(), h = getHeight();
        float toggleZone = dp(92);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = event.getActionIndex();
                int id = event.getPointerId(i);
                float x = event.getX(i), y = event.getY(i);

                if (state != STATE_PLAYING) { tapPending = true; return true; }

                if (x > w - toggleZone && y < toggleZone) {
                    toggleLightPending = true;
                    return true;
                }
                if (x < w / 2f) {
                    if (moveFinger == -1) { moveFinger = id; moveOriginX = x; moveOriginY = y; }
                } else {
                    if (lookFinger == -1) { lookFinger = id; lastLookX = x; lastLookY = y; }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    float x = event.getX(i), y = event.getY(i);
                    if (id == moveFinger) {
                        float maxRadius = dp(80);
                        float dx = (x - moveOriginX) / maxRadius;
                        float dy = (moveOriginY - y) / maxRadius;   // screen y is inverted
                        moveInX = Math.max(-1f, Math.min(1f, dx));
                        moveInY = Math.max(-1f, Math.min(1f, dy));
                    } else if (id == lookFinger) {
                        lookDX += x - lastLookX;
                        lookDY += y - lastLookY;
                        lastLookX = x;
                        lastLookY = y;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int id = event.getPointerId(event.getActionIndex());
                if (id == moveFinger || action == MotionEvent.ACTION_CANCEL) {
                    moveFinger = -1;
                    moveInX = 0f;
                    moveInY = 0f;
                }
                if (id == lookFinger || action == MotionEvent.ACTION_CANCEL) {
                    lookFinger = -1;
                }
                break;
            }
            default:
                break;
        }
        return true;
    }
}
