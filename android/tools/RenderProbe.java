import com.hollowhalls.game.Maze;
import com.hollowhalls.game.Renderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Desktop harness: drives the shipping Renderer and Maze on a plain JVM and
 * dumps frames as PNGs, so the raycaster can be eyeballed without a device.
 *
 *   javac -d out $(find ../src -name '*.java' ! -name 'GameView.java' \
 *        ! -name 'MainActivity.java' ! -name 'GameAudio.java') RenderProbe.java
 *   java -cp out RenderProbe frames/
 */
public class RenderProbe {
    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "frames");
        outDir.mkdirs();

        Maze maze = new Maze(9, 9, new Random(7));
        System.out.println("start cell " + maze.startX + "," + maze.startY
                + "  goal cell " + maze.goalX + "," + maze.goalY);
        int[] spawn = maze.enemySpawnCell();
        System.out.println("enemy spawn " + spawn[0] + "," + spawn[1]);
        printMap(maze, spawn);

        Renderer r = new Renderer(maze);
        r.resize(360, 640);

        float px = maze.cellCenterX(maze.startX), py = maze.cellCenterY(maze.startY);

        // Look around from the start cell.
        for (int i = 0; i < 4; i++) {
            float ang = (float) (i * Math.PI / 2);
            r.render(px, py, ang, 0f, true, 1.0f, maze.cellCenterX(spawn[0]),
                    maze.cellCenterY(spawn[1]), true);
            write(r, new File(outDir, "start_" + (i * 90) + "deg.png"));
        }

        // Flashlight off, same view.
        r.render(px, py, 0f, 0f, false, 1.0f, maze.cellCenterX(spawn[0]),
                maze.cellCenterY(spawn[1]), true);
        write(r, new File(outDir, "lights_off.png"));

        // Enemy looming two squares directly ahead.
        r.render(px, py, 0f, 0f, true, 1.0f, px + 2.0f, py, true);
        write(r, new File(outDir, "enemy_close.png"));
        r.render(px, py, 0f, 0f, true, 1.0f, px + 4.5f, py, true);
        write(r, new File(outDir, "enemy_far.png"));

        // Looking at the exit from whichever neighbouring cell is actually open,
        // so no wall sits between the camera and the glow.
        float gx = maze.cellCenterX(maze.goalX), gy = maze.cellCenterY(maze.goalY);
        int[] dx = {0, 1, 0, -1}, dy = {1, 0, -1, 0};
        for (int d = 0; d < 4; d++) {
            if (maze.hasWall(maze.goalX, maze.goalY, d)) continue;
            float camX = maze.cellCenterX(maze.goalX + dx[d]);
            float camY = maze.cellCenterY(maze.goalY + dy[d]);
            float facing = (float) Math.atan2(gy - camY, gx - camX);
            r.render(camX, camY, facing, 0f, true, 1.0f, gx + 30f, gy, false);
            write(r, new File(outDir, "goal_ahead.png"));
            System.out.println("goal viewed from cell offset dir=" + d);
            break;
        }

        // Pitched up and down, to check the horizon shift.
        r.render(px, py, 0f, 30f, true, 1.0f, px + 3f, py, true);
        write(r, new File(outDir, "pitch_up.png"));
        r.render(px, py, 0f, -30f, true, 1.0f, px + 3f, py, true);
        write(r, new File(outDir, "pitch_down.png"));

        System.out.println("frames written to " + outDir.getAbsolutePath());
    }

    static void write(Renderer r, File f) throws Exception {
        BufferedImage img = new BufferedImage(r.rw, r.rh, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, r.rw, r.rh, r.pixels, 0, r.rw);
        ImageIO.write(img, "png", f);
    }

    /** ASCII map so the carve and the grid flattening can be checked by eye. */
    static void printMap(Maze m, int[] spawn) {
        for (int gy = m.gh - 1; gy >= 0; gy--) {
            StringBuilder sb = new StringBuilder();
            for (int gx = 0; gx < m.gw; gx++) {
                int cx = (gx - 1) / 2, cy = (gy - 1) / 2;
                boolean cellCenter = (gx % 2 == 1) && (gy % 2 == 1);
                if (m.isSolid(gx, gy)) sb.append('#');
                else if (cellCenter && cx == m.startX && cy == m.startY) sb.append('S');
                else if (cellCenter && cx == m.goalX && cy == m.goalY) sb.append('G');
                else if (cellCenter && cx == spawn[0] && cy == spawn[1]) sb.append('E');
                else sb.append('.');
            }
            System.out.println(sb);
        }
    }
}
