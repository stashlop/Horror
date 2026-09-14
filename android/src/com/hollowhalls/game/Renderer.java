package com.hollowhalls.game;

/**
 * Software raycaster. Deliberately free of any android.* import so the exact
 * rendering code that ships in the APK can also be driven from a desktop JVM
 * (see tools/RenderProbe.java) to dump frames as PNGs for inspection.
 */
public class Renderer {

    public static final float WALL_H = 1.5f;
    public static final float EYE_H = 0.8f;
    public static final float LIGHT_RANGE = 7f;
    public static final float FOG_DENSITY = 0.085f;
    public static final float FOV_SCALE = 0.80f;     // ~77 deg horizontal
    private static final float CONE_INNER = 0.30f;   // tan of inner cone angle
    private static final float CONE_OUTER = 0.62f;   // tan of outer cone angle

    private Maze maze;
    public int rw, rh;
    public int[] pixels;
    private float[] zbuffer;

    public Renderer(Maze maze) {
        this.maze = maze;
    }

    public void setMaze(Maze maze) {
        this.maze = maze;
    }

    public void resize(int rw, int rh) {
        this.rw = rw;
        this.rh = rh;
        this.pixels = new int[rw * rh];
        this.zbuffer = new float[rw];
    }

    public boolean ready() {
        return pixels != null;
    }

    public void render(float px, float py, float ang, float pitchDeg,
                       boolean flashlightOn, float elapsed,
                       float enemyX, float enemyY, boolean enemyActive) {
        final int W = rw, H = rh;
        final float dirX = (float) Math.cos(ang), dirY = (float) Math.sin(ang);
        final float planeX = dirY * FOV_SCALE, planeY = -dirX * FOV_SCALE;
        final float projDist = (W * 0.5f) / FOV_SCALE;
        float horizon = H * 0.5f + (float) Math.tan(Math.toRadians(pitchDeg)) * projDist;
        if (horizon < -H) horizon = -H;
        if (horizon > 2 * H) horizon = 2 * H;

        // Flicker with the same character as the Unity spot light.
        float flick = (float) (Math.sin(elapsed * 4.0) * 0.5
                + Math.sin(elapsed * 9.37 + 1.3) * 0.3
                + Math.sin(elapsed * 17.1 + 2.7) * 0.2);
        float lampPower = flashlightOn ? (2.2f + flick * 0.35f) / 2.2f : 0f;

        for (int x = 0; x < W; x++) {
            float cameraX = 2f * x / W - 1f;
            float rayX = dirX + planeX * cameraX;
            float rayY = dirY + planeY * cameraX;
            float th = cameraX * FOV_SCALE;
            float th2 = th * th;

            int mapX = (int) Math.floor(px), mapY = (int) Math.floor(py);
            float deltaX = rayX == 0f ? 1e30f : Math.abs(1f / rayX);
            float deltaY = rayY == 0f ? 1e30f : Math.abs(1f / rayY);
            int stepX, stepY;
            float sideDistX, sideDistY;

            if (rayX < 0) { stepX = -1; sideDistX = (px - mapX) * deltaX; }
            else { stepX = 1; sideDistX = (mapX + 1f - px) * deltaX; }
            if (rayY < 0) { stepY = -1; sideDistY = (py - mapY) * deltaY; }
            else { stepY = 1; sideDistY = (mapY + 1f - py) * deltaY; }

            boolean hitSideY = false;
            int guard = 0;
            while (guard++ < 512) {
                if (sideDistX < sideDistY) { sideDistX += deltaX; mapX += stepX; hitSideY = false; }
                else { sideDistY += deltaY; mapY += stepY; hitSideY = true; }
                if (maze.isSolid(mapX, mapY)) break;
            }

            float perpDist = hitSideY
                    ? (mapY - py + (1 - stepY) * 0.5f) / (rayY == 0f ? 1e-6f : rayY)
                    : (mapX - px + (1 - stepX) * 0.5f) / (rayX == 0f ? 1e-6f : rayX);
            if (perpDist < 0.02f) perpDist = 0.02f;
            zbuffer[x] = perpDist;

            int lineTop = (int) (horizon - (WALL_H - EYE_H) * projDist / perpDist);
            int lineBottom = (int) (horizon + EYE_H * projDist / perpDist);

            // Ceiling
            int ceilEnd = Math.min(lineTop, H);
            for (int y = 0; y < ceilEnd; y++) {
                float denom = horizon - y;
                float d = denom > 0.5f ? (WALL_H - EYE_H) * projDist / denom : 60f;
                float tv = denom / projDist;
                pixels[y * W + x] = shade(18, 17, 20, d, th2 + tv * tv, lampPower);
            }
            // Wall -- north/south faces a touch darker, the usual raycaster trick
            int wallStart = Math.max(lineTop, 0);
            int wallEnd = Math.min(lineBottom, H);
            int wr = hitSideY ? 58 : 72, wg = hitSideY ? 56 : 70, wb = hitSideY ? 54 : 66;
            for (int y = wallStart; y < wallEnd; y++) {
                float tv = (horizon - y) / projDist;
                pixels[y * W + x] = shade(wr, wg, wb, perpDist, th2 + tv * tv, lampPower);
            }
            // Floor
            int floorStart = Math.max(lineBottom, 0);
            for (int y = floorStart; y < H; y++) {
                float denom = y - horizon;
                float d = denom > 0.5f ? EYE_H * projDist / denom : 60f;
                float tv = denom / projDist;
                float fx = px + rayX * d, fy = py + rayY * d;
                boolean checker = ((((int) (fx * 1.5f)) + ((int) (fy * 1.5f))) & 1) == 0;
                int r = checker ? 66 : 56, g = checker ? 60 : 51, b = checker ? 50 : 43;
                pixels[y * W + x] = shade(r, g, b, d, th2 + tv * tv, lampPower);
            }
        }

        drawGoalGlow(px, py, dirX, dirY, planeX, planeY, projDist, horizon);
        if (enemyActive) {
            drawEnemy(px, py, enemyX, enemyY, dirX, dirY, planeX, planeY, projDist, horizon, lampPower);
        }
    }

    /**
     * Dim ambient everywhere, plus the flashlight cone, all attenuated by
     * exponential fog -- the Unity scene's look, done per pixel.
     */
    private int shade(int r, int g, int b, float dist, float tan2, float lampPower) {
        float light = 0.055f;
        if (lampPower > 0f) {
            float cone;
            if (tan2 <= CONE_INNER * CONE_INNER) {
                cone = 1f;
            } else if (tan2 >= CONE_OUTER * CONE_OUTER) {
                cone = 0f;
            } else {
                float t = (float) ((Math.sqrt(tan2) - CONE_INNER) / (CONE_OUTER - CONE_INNER));
                cone = 1f - t * t;
            }
            if (cone > 0f) {
                float falloff = 1f - dist / LIGHT_RANGE;
                if (falloff > 0f) light += lampPower * cone * falloff * falloff * 1.35f;
            }
        }
        float m = light * (float) Math.exp(-FOG_DENSITY * dist);
        int rr = (int) (r * m), gg = (int) (g * m), bb = (int) (b * m);
        if (rr > 255) rr = 255;
        if (gg > 255) gg = 255;
        if (bb > 255) bb = 255;
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    /** The exit: an additive halo you can see glowing from down a corridor. */
    private void drawGoalGlow(float px, float py, float dirX, float dirY,
                              float planeX, float planeY, float projDist, float horizon) {
        float relX = maze.cellCenterX(maze.goalX) - px;
        float relY = maze.cellCenterY(maze.goalY) - py;
        float invDet = 1f / (planeX * dirY - dirX * planeY);
        float tx = invDet * (dirY * relX - dirX * relY);
        float ty = invDet * (-planeY * relX + planeX * relY);
        if (ty <= 0.05f) return;

        int screenX = (int) ((rw * 0.5f) * (1f + tx / ty));
        int rad = Math.max(2, (int) Math.abs(projDist * 0.55f / ty));
        int centerY = (int) (horizon + (EYE_H - 0.55f) * projDist / ty);

        int x0 = Math.max(0, screenX - rad), x1 = Math.min(rw - 1, screenX + rad);
        int y0 = Math.max(0, centerY - rad), y1 = Math.min(rh - 1, centerY + rad);
        float fog = (float) Math.exp(-FOG_DENSITY * ty * 0.5f);

        for (int x = x0; x <= x1; x++) {
            if (ty >= zbuffer[x]) continue;
            float dx = (x - screenX) / (float) rad;
            for (int y = y0; y <= y1; y++) {
                float dy = (y - centerY) / (float) rad;
                float dd = dx * dx + dy * dy;
                if (dd > 1f) continue;
                float a = (1f - dd);
                a = a * a * fog;
                int idx = y * rw + x;
                int c = pixels[idx];
                int r = Math.min(255, ((c >> 16) & 0xFF) + (int) (235 * a));
                int g = Math.min(255, ((c >> 8) & 0xFF) + (int) (205 * a));
                int b = Math.min(255, (c & 0xFF) + (int) (90 * a));
                pixels[idx] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    /** The thing hunting you: a near-black silhouette with two faint eyes. */
    private void drawEnemy(float px, float py, float enemyX, float enemyY,
                           float dirX, float dirY, float planeX, float planeY,
                           float projDist, float horizon, float lampPower) {
        float relX = enemyX - px, relY = enemyY - py;
        float invDet = 1f / (planeX * dirY - dirX * planeY);
        float tx = invDet * (dirY * relX - dirX * relY);
        float ty = invDet * (-planeY * relX + planeX * relY);
        if (ty <= 0.05f) return;

        final float FIG_H = 1.15f;
        int screenX = (int) ((rw * 0.5f) * (1f + tx / ty));
        int bottomY = (int) (horizon + EYE_H * projDist / ty);
        int topY = (int) (horizon - (FIG_H - EYE_H) * projDist / ty);
        int figH = bottomY - topY;
        if (figH < 2) return;
        int halfW = Math.max(1, (int) (figH * 0.21f));

        float fog = (float) Math.exp(-FOG_DENSITY * ty);
        float lit = 0.06f;
        if (lampPower > 0f) {
            float t = Math.abs(tx / ty) * FOV_SCALE;
            float cone = t <= CONE_INNER ? 1f
                    : (t >= CONE_OUTER ? 0f : 1f - (t - CONE_INNER) / (CONE_OUTER - CONE_INNER));
            float falloff = Math.max(0f, 1f - ty / LIGHT_RANGE);
            lit += lampPower * cone * falloff * falloff * 0.9f;
        }
        // Stays darker than the lit wall behind it, so it reads as a silhouette.
        int body = (int) Math.min(255f, 28f * lit * fog * 2.2f);
        int col = 0xFF000000 | (body << 16) | (body << 8) | Math.min(255, body + 2);

        int headH = Math.max(1, (int) (figH * 0.22f));
        for (int x = Math.max(0, screenX - halfW); x <= Math.min(rw - 1, screenX + halfW); x++) {
            if (ty >= zbuffer[x]) continue;
            float nx = (x - screenX) / (float) halfW;
            for (int y = Math.max(0, topY); y <= Math.min(rh - 1, bottomY); y++) {
                float ny = (y - topY) / (float) figH;
                boolean inside;
                if (ny < 0.22f) {
                    float hy = (ny - 0.11f) / 0.11f;
                    inside = nx * nx / 0.36f + hy * hy < 1f;
                } else {
                    float taper = 0.55f + 0.45f * (ny - 0.22f) / 0.78f;
                    inside = Math.abs(nx) < taper;
                }
                if (inside) pixels[y * rw + x] = col;
            }
        }

        int eyeY = topY + (int) (headH * 0.5f);
        int eyeOff = Math.max(1, halfW / 3);
        int glow = (int) Math.min(255f, 210f * fog);
        int size = Math.max(0, halfW / 5);
        for (int s = -1; s <= 1; s += 2) {
            int ex = screenX + s * eyeOff;
            if (ex < 0 || ex >= rw) continue;
            if (ty >= zbuffer[ex]) continue;
            for (int dx = -size; dx <= size; dx++) {
                for (int dy = -size; dy <= size; dy++) {
                    int xx = ex + dx, yy = eyeY + dy;
                    if (xx < 0 || xx >= rw || yy < 0 || yy >= rh) continue;
                    pixels[yy * rw + xx] = 0xFF000000 | (glow << 16) | ((glow / 4) << 8) | (glow / 6);
                }
            }
        }
    }
}
