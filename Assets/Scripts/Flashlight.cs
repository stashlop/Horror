using UnityEngine;

namespace HollowHalls
{
    [RequireComponent(typeof(Light))]
    public class Flashlight : MonoBehaviour
    {
        public float BaseIntensity = 2.2f;
        public float FlickerAmount = 0.35f;
        public float FlickerSpeed = 4f;

        Light lamp;
        float noiseSeed;

        // Screen-space toggle zone: top-right corner square, in pixels from the corner.
        const float ToggleZoneSize = 140f;

        void Awake()
        {
            lamp = GetComponent<Light>();
            lamp.type = LightType.Spot;
            lamp.range = 14f;
            lamp.spotAngle = 55f;
            lamp.color = new Color(1f, 0.96f, 0.85f);
            lamp.intensity = BaseIntensity;
            lamp.shadows = LightShadows.Soft;
            noiseSeed = Random.Range(0f, 100f);
        }

        void Update()
        {
            if (!lamp.enabled) return;

            float n = Mathf.PerlinNoise(noiseSeed, Time.time * FlickerSpeed);
            lamp.intensity = BaseIntensity + (n - 0.5f) * FlickerAmount;

            CheckToggleTap();
        }

        void CheckToggleTap()
        {
            if (GameManager.Instance == null || GameManager.Instance.State != GameState.Playing)
                return;

            for (int i = 0; i < Input.touchCount; i++)
            {
                Touch t = Input.GetTouch(i);
                if (t.phase != TouchPhase.Began) continue;
                if (t.position.x > Screen.width - ToggleZoneSize && t.position.y > Screen.height - ToggleZoneSize)
                {
                    lamp.enabled = !lamp.enabled;
                }
            }
        }
    }
}
