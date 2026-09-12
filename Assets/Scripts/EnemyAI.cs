using UnityEngine;

namespace HollowHalls
{
    public class EnemyAI : MonoBehaviour
    {
        public MazeGenerator Maze;
        public Transform Player;
        public float Speed = 2.6f;
        public float RepathInterval = 0.4f;
        public float CatchDistance = 1.4f;

        Vector2Int currentCell;
        Vector2Int targetCell;
        float repathTimer;
        bool active = false;

        public void Activate(Vector2Int spawnCell)
        {
            currentCell = spawnCell;
            targetCell = spawnCell;
            transform.position = Maze.CellToWorld(spawnCell) + Vector3.up * 0.9f;
            active = true;
        }

        void Update()
        {
            if (!active || GameManager.Instance == null || GameManager.Instance.State != GameState.Playing)
                return;

            repathTimer -= Time.deltaTime;
            if (repathTimer <= 0f)
            {
                repathTimer = RepathInterval;
                Vector2Int playerCell = WorldToCell(Player.position);
                targetCell = Maze.NextStepTowards(currentCell, playerCell);
            }

            Vector3 targetWorld = Maze.CellToWorld(targetCell) + Vector3.up * 0.9f;
            transform.position = Vector3.MoveTowards(transform.position, targetWorld, Speed * Time.deltaTime);
            transform.LookAt(new Vector3(targetWorld.x, transform.position.y, targetWorld.z));

            if (Vector3.Distance(transform.position, targetWorld) < 0.05f)
                currentCell = targetCell;

            float distToPlayer = Vector3.Distance(
                new Vector3(transform.position.x, 0, transform.position.z),
                new Vector3(Player.position.x, 0, Player.position.z));

            if (distToPlayer <= CatchDistance)
            {
                active = false;
                GameManager.Instance.OnPlayerCaught();
            }
        }

        public float DistanceToPlayer()
        {
            if (Player == null) return 999f;
            return Vector3.Distance(transform.position, Player.position);
        }

        Vector2Int WorldToCell(Vector3 pos)
        {
            return new Vector2Int(
                Mathf.RoundToInt(pos.x / Maze.CellSize),
                Mathf.RoundToInt(pos.z / Maze.CellSize));
        }
    }
}
