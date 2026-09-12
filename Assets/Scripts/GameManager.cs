using UnityEngine;
using UnityEngine.SceneManagement;

namespace HollowHalls
{
    public enum GameState { Title, Playing, Caught, Win }

    public class GameManager : MonoBehaviour
    {
        public static GameManager Instance { get; private set; }

        public GameState State { get; private set; } = GameState.Title;

        public ProceduralAudio Audio;

        float flashAlpha = 0f;
        GUIStyle titleStyle;
        GUIStyle bodyStyle;

        void Awake()
        {
            Instance = this;
        }

        void Update()
        {
            if (State == GameState.Title && AnyTapBegan())
            {
                State = GameState.Playing;
            }
            else if ((State == GameState.Caught || State == GameState.Win) && AnyTapBegan())
            {
                SceneManager.LoadScene(SceneManager.GetActiveScene().buildIndex);
            }

            flashAlpha = Mathf.Max(0f, flashAlpha - Time.deltaTime * 1.2f);
        }

        bool AnyTapBegan()
        {
            if (Input.touchCount > 0)
            {
                for (int i = 0; i < Input.touchCount; i++)
                    if (Input.GetTouch(i).phase == TouchPhase.Began) return true;
                return false;
            }
#if UNITY_EDITOR
            return Input.GetMouseButtonDown(0);
#else
            return false;
#endif
        }

        public void OnPlayerCaught()
        {
            if (State != GameState.Playing) return;
            State = GameState.Caught;
            flashAlpha = 1f;
            Audio?.PlayStinger();
        }

        public void OnPlayerReachedGoal()
        {
            if (State != GameState.Playing) return;
            State = GameState.Win;
        }

        void EnsureStyles()
        {
            if (titleStyle != null) return;
            titleStyle = new GUIStyle(GUI.skin.label)
            {
                fontSize = Mathf.RoundToInt(Screen.width * 0.06f),
                alignment = TextAnchor.MiddleCenter,
                fontStyle = FontStyle.Bold,
                normal = { textColor = Color.white }
            };
            bodyStyle = new GUIStyle(GUI.skin.label)
            {
                fontSize = Mathf.RoundToInt(Screen.width * 0.028f),
                alignment = TextAnchor.MiddleCenter,
                normal = { textColor = new Color(0.85f, 0.85f, 0.85f) }
            };
        }

        void OnGUI()
        {
            EnsureStyles();
            Rect full = new Rect(0, 0, Screen.width, Screen.height);

            if (State == GameState.Title)
            {
                GUI.DrawTexture(full, Texture2D.blackTexture, ScaleMode.StretchToFill, false, 0, new Color(0, 0, 0, 0.75f), 0, 0);
                GUI.Label(new Rect(0, Screen.height * 0.25f, Screen.width, Screen.height * 0.12f), "HOLLOW HALLS", titleStyle);
                GUI.Label(new Rect(Screen.width * 0.1f, Screen.height * 0.42f, Screen.width * 0.8f, Screen.height * 0.35f),
                    "Left side of the screen: walk\nRight side: drag to look\nTap the top-right corner: flashlight\n\nSomething hunts these halls.\nFind the glowing exit before it finds you.\n\nTAP TO BEGIN",
                    bodyStyle);
            }
            else if (State == GameState.Caught)
            {
                GUI.DrawTexture(full, Texture2D.blackTexture, ScaleMode.StretchToFill, false, 0, new Color(0.4f, 0, 0, 0.85f), 0, 0);
                GUI.Label(new Rect(0, Screen.height * 0.4f, Screen.width, Screen.height * 0.12f), "IT FOUND YOU", titleStyle);
                GUI.Label(new Rect(0, Screen.height * 0.55f, Screen.width, Screen.height * 0.1f), "tap to try again", bodyStyle);
            }
            else if (State == GameState.Win)
            {
                GUI.DrawTexture(full, Texture2D.blackTexture, ScaleMode.StretchToFill, false, 0, new Color(0, 0, 0, 0.75f), 0, 0);
                GUI.Label(new Rect(0, Screen.height * 0.4f, Screen.width, Screen.height * 0.12f), "YOU ESCAPED", titleStyle);
                GUI.Label(new Rect(0, Screen.height * 0.55f, Screen.width, Screen.height * 0.1f), "tap to play again", bodyStyle);
            }

            if (flashAlpha > 0f)
            {
                GUI.DrawTexture(full, Texture2D.whiteTexture, ScaleMode.StretchToFill, false, 0, new Color(1f, 1f, 1f, flashAlpha), 0, 0);
            }
        }
    }
}
