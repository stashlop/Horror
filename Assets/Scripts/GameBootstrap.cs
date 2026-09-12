using UnityEngine;

namespace HollowHalls
{
    public static class GameBootstrap
    {
        const int MazeWidth = 9;
        const int MazeHeight = 9;
        const float CellSize = 4f;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        static void Init()
        {
            RenderSettings.ambientLight = new Color(0.02f, 0.02f, 0.03f);
            RenderSettings.fog = true;
            RenderSettings.fogColor = new Color(0.01f, 0.01f, 0.015f);
            RenderSettings.fogMode = FogMode.Exponential;
            RenderSettings.fogDensity = 0.045f;

            // Game manager first so other scripts can find GameManager.Instance immediately.
            var gmGO = new GameObject("GameManager");
            var gameManager = gmGO.AddComponent<GameManager>();

            // Maze
            var mazeGO = new GameObject("Maze");
            var maze = mazeGO.AddComponent<MazeGenerator>();
            maze.Generate(MazeWidth, MazeHeight, CellSize);

            // Player
            var playerGO = new GameObject("Player");
            playerGO.transform.position = maze.CellToWorld(maze.StartCell) + Vector3.up * 1f;
            var controller = playerGO.AddComponent<CharacterController>();
            controller.height = 1.8f;
            controller.radius = 0.35f;
            controller.center = new Vector3(0, 0.9f, 0);

            var cameraPivot = new GameObject("CameraPivot").transform;
            cameraPivot.SetParent(playerGO.transform);
            cameraPivot.localPosition = new Vector3(0, 1.6f, 0);

            var camGO = new GameObject("PlayerCamera");
            camGO.transform.SetParent(cameraPivot);
            camGO.transform.localPosition = Vector3.zero;
            camGO.transform.localRotation = Quaternion.identity;
            var cam = camGO.AddComponent<Camera>();
            cam.nearClipPlane = 0.05f;
            cam.farClipPlane = 60f;
            camGO.AddComponent<AudioListener>();

            var flashlightGO = new GameObject("Flashlight");
            flashlightGO.transform.SetParent(camGO.transform);
            flashlightGO.transform.localPosition = Vector3.zero;
            flashlightGO.transform.localRotation = Quaternion.identity;
            flashlightGO.AddComponent<Light>();
            flashlightGO.AddComponent<Flashlight>();

            var fpsController = playerGO.AddComponent<TouchFPSController>();
            fpsController.CameraPivot = cameraPivot;

            // Enemy - spawn it a fair distance from the player so the opening moments are safe.
            var enemyGO = GameObject.CreatePrimitive(PrimitiveType.Capsule);
            enemyGO.name = "Enemy";
            var enemyMat = new Material(Shader.Find("Standard"));
            enemyMat.color = new Color(0.02f, 0.02f, 0.02f);
            enemyGO.GetComponent<Renderer>().material = enemyMat;
            Object.Destroy(enemyGO.GetComponent<Collider>());
            var enemyAI = enemyGO.AddComponent<EnemyAI>();
            enemyAI.Maze = maze;
            enemyAI.Player = playerGO.transform;

            Vector2Int spawnCell = FindEnemySpawnCell(maze);
            enemyAI.Activate(spawnCell);

            // Audio
            var audioGO = new GameObject("ProceduralAudio");
            var proceduralAudio = audioGO.AddComponent<ProceduralAudio>();
            proceduralAudio.Enemy = enemyAI;
            gameManager.Audio = proceduralAudio;
        }

        // Pick a cell roughly midway between start and goal by BFS distance, so the
        // enemy doesn't start on top of either the player or the exit.
        static Vector2Int FindEnemySpawnCell(MazeGenerator maze)
        {
            var distFromStart = maze.BfsDistances(maze.StartCell);
            int maxDist = 0;
            foreach (var kv in distFromStart) maxDist = Mathf.Max(maxDist, kv.Value);

            Vector2Int best = maze.GoalCell;
            int bestDiff = int.MaxValue;
            int target = Mathf.Max(3, maxDist / 2);
            foreach (var kv in distFromStart)
            {
                if (kv.Key == maze.StartCell || kv.Key == maze.GoalCell) continue;
                int diff = Mathf.Abs(kv.Value - target);
                if (diff < bestDiff)
                {
                    bestDiff = diff;
                    best = kv.Key;
                }
            }
            return best;
        }
    }
}
