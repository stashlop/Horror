package com.hollowhalls.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;

/**
 * Port of the Unity MazeGenerator. Cells carry four walls (0=N 1=E 2=S 3=W).
 * The carved cell graph is also flattened into an odd-sized occupancy grid
 * (2W+1 x 2H+1) so the raycaster can walk it with plain DDA.
 */
public class Maze {
    public static final int N = 0, E = 1, S = 2, W = 3;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {1, 0, -1, 0};

    public final int width, height;
    private final boolean[][] wall;   // [cellIndex][dir]
    public final int gw, gh;
    public final boolean[] solid;     // occupancy grid, gw*gh

    public int startX, startY;
    public int goalX, goalY;

    public Maze(int width, int height, Random rng) {
        this.width = width;
        this.height = height;
        this.wall = new boolean[width * height][4];
        for (boolean[] w : wall) { w[0] = w[1] = w[2] = w[3] = true; }

        this.gw = width * 2 + 1;
        this.gh = height * 2 + 1;
        this.solid = new boolean[gw * gh];

        startX = 0;
        startY = 0;
        carve(rng);

        int[] d = bfs(startX, startY);
        int best = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (d[idx(x, y)] > best) { best = d[idx(x, y)]; goalX = x; goalY = y; }
            }
        }
        buildGrid();
    }

    private int idx(int x, int y) { return y * width + x; }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean hasWall(int x, int y, int dir) { return wall[idx(x, y)][dir]; }

    private void carve(Random rng) {
        boolean[] visited = new boolean[width * height];
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        visited[idx(startX, startY)] = true;
        stack.push(new int[]{startX, startY});

        ArrayList<Integer> options = new ArrayList<>(4);
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            options.clear();
            for (int d = 0; d < 4; d++) {
                int nx = cur[0] + DX[d], ny = cur[1] + DY[d];
                if (inBounds(nx, ny) && !visited[idx(nx, ny)]) options.add(d);
            }
            if (options.isEmpty()) { stack.pop(); continue; }

            int dir = options.get(rng.nextInt(options.size()));
            int nx = cur[0] + DX[dir], ny = cur[1] + DY[dir];
            wall[idx(cur[0], cur[1])][dir] = false;
            wall[idx(nx, ny)][(dir + 2) % 4] = false;
            visited[idx(nx, ny)] = true;
            stack.push(new int[]{nx, ny});
        }
    }

    /** Distances from a cell through open passages; -1 where unreachable. */
    public int[] bfs(int fromX, int fromY) {
        int[] dist = new int[width * height];
        for (int i = 0; i < dist.length; i++) dist[i] = -1;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[idx(fromX, fromY)] = 0;
        q.add(new int[]{fromX, fromY});
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int d = 0; d < 4; d++) {
                if (wall[idx(cur[0], cur[1])][d]) continue;
                int nx = cur[0] + DX[d], ny = cur[1] + DY[d];
                if (!inBounds(nx, ny) || dist[idx(nx, ny)] != -1) continue;
                dist[idx(nx, ny)] = dist[idx(cur[0], cur[1])] + 1;
                q.add(new int[]{nx, ny});
            }
        }
        return dist;
    }

    /** One step from (fx,fy) along the shortest open path towards (tx,ty). */
    public int[] nextStepTowards(int fx, int fy, int tx, int ty) {
        if (fx == tx && fy == ty) return new int[]{fx, fy};
        int[] distFromTarget = bfs(tx, ty);
        int bestX = fx, bestY = fy, best = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            if (wall[idx(fx, fy)][d]) continue;
            int nx = fx + DX[d], ny = fy + DY[d];
            if (!inBounds(nx, ny)) continue;
            int dd = distFromTarget[idx(nx, ny)];
            if (dd >= 0 && dd < best) { best = dd; bestX = nx; bestY = ny; }
        }
        return new int[]{bestX, bestY};
    }

    /**
     * Enemy spawn: roughly halfway from the start by BFS distance, so it starts
     * on top of neither the player nor the exit.
     */
    public int[] enemySpawnCell() {
        int[] d = bfs(startX, startY);
        int max = 0;
        for (int v : d) max = Math.max(max, v);
        int target = Math.max(3, max / 2);
        int bestX = goalX, bestY = goalY, bestDiff = Integer.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((x == startX && y == startY) || (x == goalX && y == goalY)) continue;
                int v = d[idx(x, y)];
                if (v < 0) continue;
                int diff = Math.abs(v - target);
                if (diff < bestDiff) { bestDiff = diff; bestX = x; bestY = y; }
            }
        }
        return new int[]{bestX, bestY};
    }

    private void buildGrid() {
        java.util.Arrays.fill(solid, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                solid[g(2 * x + 1, 2 * y + 1)] = false;
                for (int d = 0; d < 4; d++) {
                    if (wall[idx(x, y)][d]) continue;
                    solid[g(2 * x + 1 + DX[d], 2 * y + 1 + DY[d])] = false;
                }
            }
        }
    }

    private int g(int gx, int gy) { return gy * gw + gx; }

    public boolean isSolid(int gx, int gy) {
        if (gx < 0 || gx >= gw || gy < 0 || gy >= gh) return true;
        return solid[gy * gw + gx];
    }

    /** Centre of a cell in grid coordinates. */
    public float cellCenterX(int cx) { return 2 * cx + 1.5f; }
    public float cellCenterY(int cy) { return 2 * cy + 1.5f; }

    public int gridToCellX(float gx) { return (int) Math.floor((gx - 0.5f) / 2f); }
    public int gridToCellY(float gy) { return (int) Math.floor((gy - 0.5f) / 2f); }
}
