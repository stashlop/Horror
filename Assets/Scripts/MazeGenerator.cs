using System.Collections.Generic;
using UnityEngine;

namespace HollowHalls
{
    // A single maze cell. Walls: 0=North(+z) 1=East(+x) 2=South(-z) 3=West(-x)
    public class MazeCell
    {
        public bool[] Wall = { true, true, true, true };
        public bool Visited = false;
    }

    public class MazeGenerator : MonoBehaviour
    {
        public int Width { get; private set; }
        public int Height { get; private set; }
        public float CellSize { get; private set; }

        private MazeCell[,] cells;

        public Vector2Int StartCell { get; private set; }
        public Vector2Int GoalCell { get; private set; }

        static readonly Vector2Int[] Dirs = {
            new Vector2Int(0, 1),  // N
            new Vector2Int(1, 0),  // E
            new Vector2Int(0, -1), // S
            new Vector2Int(-1, 0), // W
        };

        public void Generate(int width, int height, float cellSize)
        {
            Width = width;
            Height = height;
            CellSize = cellSize;
            cells = new MazeCell[width, height];
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                    cells[x, y] = new MazeCell();

            StartCell = new Vector2Int(0, 0);
            Carve(StartCell);
            GoalCell = FindFarthestCell(StartCell);

            BuildGeometry();
        }

        void Carve(Vector2Int start)
        {
            var stack = new Stack<Vector2Int>();
            cells[start.x, start.y].Visited = true;
            stack.Push(start);

            while (stack.Count > 0)
            {
                var current = stack.Peek();
                var neighbors = new List<int>();
                for (int d = 0; d < 4; d++)
                {
                    var n = current + Dirs[d];
                    if (InBounds(n) && !cells[n.x, n.y].Visited)
                        neighbors.Add(d);
                }

                if (neighbors.Count == 0)
                {
                    stack.Pop();
                    continue;
                }

                int dir = neighbors[Random.Range(0, neighbors.Count)];
                var next = current + Dirs[dir];
                cells[current.x, current.y].Wall[dir] = false;
                cells[next.x, next.y].Wall[(dir + 2) % 4] = false;
                cells[next.x, next.y].Visited = true;
                stack.Push(next);
            }
        }

        bool InBounds(Vector2Int c) => c.x >= 0 && c.x < Width && c.y >= 0 && c.y < Height;

        // BFS to find the cell farthest from 'from' - used to place the exit far from the start.
        Vector2Int FindFarthestCell(Vector2Int from)
        {
            var dist = BfsDistances(from);
            var best = from;
            int bestDist = -1;
            foreach (var kv in dist)
            {
                if (kv.Value > bestDist)
                {
                    bestDist = kv.Value;
                    best = kv.Key;
                }
            }
            return best;
        }

        public Dictionary<Vector2Int, int> BfsDistances(Vector2Int from)
        {
            var dist = new Dictionary<Vector2Int, int> { { from, 0 } };
            var queue = new Queue<Vector2Int>();
            queue.Enqueue(from);
            while (queue.Count > 0)
            {
                var cur = queue.Dequeue();
                for (int d = 0; d < 4; d++)
                {
                    if (cells[cur.x, cur.y].Wall[d]) continue; // wall blocks passage
                    var n = cur + Dirs[d];
                    if (!InBounds(n) || dist.ContainsKey(n)) continue;
                    dist[n] = dist[cur] + 1;
                    queue.Enqueue(n);
                }
            }
            return dist;
        }

        // Returns the next cell to step towards when walking from 'from' to 'to' through open passages.
        public Vector2Int NextStepTowards(Vector2Int from, Vector2Int to)
        {
            if (from == to) return from;
            var distFromTarget = BfsDistances(to);
            var best = from;
            int bestDist = int.MaxValue;
            for (int d = 0; d < 4; d++)
            {
                if (cells[from.x, from.y].Wall[d]) continue;
                var n = from + Dirs[d];
                if (InBounds(n) && distFromTarget.TryGetValue(n, out int dd) && dd < bestDist)
                {
                    bestDist = dd;
                    best = n;
                }
            }
            return best;
        }

        public Vector3 CellToWorld(Vector2Int c)
        {
            return new Vector3(c.x * CellSize, 0f, c.y * CellSize);
        }

        void BuildGeometry()
        {
            var wallMat = new Material(Shader.Find("Standard"));
            wallMat.color = new Color(0.05f, 0.05f, 0.06f);
            wallMat.SetFloat("_Glossiness", 0.05f);

            var floorMat = new Material(Shader.Find("Standard"));
            floorMat.color = new Color(0.08f, 0.07f, 0.06f);
            floorMat.SetFloat("_Glossiness", 0.0f);

            float wallHeight = 3f;
            float wallThickness = 0.2f;

            Transform root = new GameObject("MazeGeometry").transform;

            for (int x = 0; x < Width; x++)
            {
                for (int y = 0; y < Height; y++)
                {
                    var cell = cells[x, y];
                    Vector3 basePos = CellToWorld(new Vector2Int(x, y));

                    if (cell.Wall[0]) // North edge
                        MakeWall(root, basePos + new Vector3(0, wallHeight / 2f, CellSize / 2f),
                            new Vector3(CellSize + wallThickness, wallHeight, wallThickness), wallMat);

                    if (cell.Wall[3]) // West edge
                        MakeWall(root, basePos + new Vector3(-CellSize / 2f, wallHeight / 2f, 0),
                            new Vector3(wallThickness, wallHeight, CellSize + wallThickness), wallMat);

                    if (y == 0 && cell.Wall[2]) // South boundary
                        MakeWall(root, basePos + new Vector3(0, wallHeight / 2f, -CellSize / 2f),
                            new Vector3(CellSize + wallThickness, wallHeight, wallThickness), wallMat);

                    if (x == Width - 1 && cell.Wall[1]) // East boundary
                        MakeWall(root, basePos + new Vector3(CellSize / 2f, wallHeight / 2f, 0),
                            new Vector3(wallThickness, wallHeight, CellSize + wallThickness), wallMat);
                }
            }

            // Floor
            var floor = GameObject.CreatePrimitive(PrimitiveType.Cube);
            floor.name = "Floor";
            floor.transform.SetParent(root);
            floor.transform.position = new Vector3((Width - 1) * CellSize / 2f, -0.25f, (Height - 1) * CellSize / 2f);
            floor.transform.localScale = new Vector3(Width * CellSize + 2f, 0.5f, Height * CellSize + 2f);
            floor.GetComponent<Renderer>().material = floorMat;

            // Ceiling
            var ceiling = GameObject.CreatePrimitive(PrimitiveType.Cube);
            ceiling.name = "Ceiling";
            ceiling.transform.SetParent(root);
            ceiling.transform.position = new Vector3((Width - 1) * CellSize / 2f, wallHeight + 0.25f, (Height - 1) * CellSize / 2f);
            ceiling.transform.localScale = new Vector3(Width * CellSize + 2f, 0.5f, Height * CellSize + 2f);
            ceiling.GetComponent<Renderer>().material = wallMat;

            // Goal marker + trigger
            var goalGO = new GameObject("GoalTrigger");
            goalGO.transform.SetParent(root);
            goalGO.transform.position = CellToWorld(GoalCell) + Vector3.up * 1f;
            var col = goalGO.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(CellSize * 0.8f, 2f, CellSize * 0.8f);
            goalGO.AddComponent<GoalTrigger>();

            var glow = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            glow.transform.SetParent(goalGO.transform);
            glow.transform.localPosition = Vector3.zero;
            glow.transform.localScale = Vector3.one * 0.6f;
            Object.Destroy(glow.GetComponent<Collider>());
            var glowMat = new Material(Shader.Find("Standard"));
            glowMat.color = Color.yellow;
            glowMat.EnableKeyword("_EMISSION");
            glowMat.SetColor("_EmissionColor", Color.yellow * 2f);
            glow.GetComponent<Renderer>().material = glowMat;
            var goalLight = goalGO.AddComponent<Light>();
            goalLight.type = LightType.Point;
            goalLight.color = Color.yellow;
            goalLight.range = 6f;
            goalLight.intensity = 1.2f;
        }

        void MakeWall(Transform parent, Vector3 pos, Vector3 scale, Material mat)
        {
            var wall = GameObject.CreatePrimitive(PrimitiveType.Cube);
            wall.name = "Wall";
            wall.transform.SetParent(parent);
            wall.transform.position = pos;
            wall.transform.localScale = scale;
            wall.GetComponent<Renderer>().material = mat;
        }
    }

    public class GoalTrigger : MonoBehaviour
    {
        void OnTriggerEnter(Collider other)
        {
            if (other.GetComponent<CharacterController>() != null)
            {
                GameManager.Instance?.OnPlayerReachedGoal();
            }
        }
    }
}
