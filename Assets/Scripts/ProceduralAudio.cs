using UnityEngine;

namespace HollowHalls
{
    public class ProceduralAudio : MonoBehaviour
    {
        public EnemyAI Enemy;
        public float MaxHeartbeatDistance = 12f;

        AudioSource ambient;
        AudioSource heartbeat;
        AudioSource stinger;

        const int SampleRate = 44100;

        void Awake()
        {
            ambient = gameObject.AddComponent<AudioSource>();
            heartbeat = gameObject.AddComponent<AudioSource>();
            stinger = gameObject.AddComponent<AudioSource>();

            ambient.clip = GenerateDrone(6f);
            ambient.loop = true;
            ambient.volume = 0.5f;
            ambient.spatialBlend = 0f;

            heartbeat.clip = GenerateHeartbeat(1.1f);
            heartbeat.loop = true;
            heartbeat.volume = 0f;
            heartbeat.spatialBlend = 0f;

            stinger.clip = GenerateStinger(0.7f);
            stinger.loop = false;
            stinger.volume = 0.9f;
            stinger.spatialBlend = 0f;
        }

        void Start()
        {
            ambient.Play();
            heartbeat.Play();
        }

        void Update()
        {
            if (Enemy == null || GameManager.Instance == null || GameManager.Instance.State != GameState.Playing)
            {
                heartbeat.volume = Mathf.Lerp(heartbeat.volume, 0f, Time.deltaTime * 2f);
                return;
            }

            float dist = Enemy.DistanceToPlayer();
            float t = 1f - Mathf.Clamp01(dist / MaxHeartbeatDistance);
            heartbeat.volume = Mathf.Lerp(0.05f, 0.9f, t);
            heartbeat.pitch = Mathf.Lerp(0.85f, 1.9f, t);
        }

        public void PlayStinger()
        {
            stinger.Play();
            heartbeat.volume = 0f;
        }

        AudioClip GenerateDrone(float duration)
        {
            int samples = Mathf.CeilToInt(duration * SampleRate);
            float[] data = new float[samples];
            float f1 = 54f, f2 = 58f, f3 = 27f;
            for (int i = 0; i < samples; i++)
            {
                float t = (float)i / SampleRate;
                float v = 0.35f * Mathf.Sin(2f * Mathf.PI * f1 * t)
                        + 0.25f * Mathf.Sin(2f * Mathf.PI * f2 * t)
                        + 0.2f * Mathf.Sin(2f * Mathf.PI * f3 * t)
                        + 0.05f * (Random.value * 2f - 1f);
                float fadeSamples = SampleRate * 0.3f;
                float fade = Mathf.Clamp01(Mathf.Min(i, samples - 1 - i) / fadeSamples);
                data[i] = v * 0.4f * fade;
            }
            var clip = AudioClip.Create("Drone", samples, 1, SampleRate, false);
            clip.SetData(data, 0);
            return clip;
        }

        AudioClip GenerateHeartbeat(float duration)
        {
            int samples = Mathf.CeilToInt(duration * SampleRate);
            float[] data = new float[samples];

            void Thump(float startTime, float len)
            {
                int start = Mathf.FloorToInt(startTime * SampleRate);
                int len_s = Mathf.FloorToInt(len * SampleRate);
                for (int i = 0; i < len_s && start + i < samples; i++)
                {
                    float t = (float)i / SampleRate;
                    float envelope = Mathf.Exp(-t * 22f);
                    float v = Mathf.Sin(2f * Mathf.PI * 60f * t) * envelope;
                    data[start + i] += v;
                }
            }

            Thump(0.0f, 0.25f);
            Thump(0.22f, 0.25f);

            return AudioClipFrom(data, samples);
        }

        AudioClip GenerateStinger(float duration)
        {
            int samples = Mathf.CeilToInt(duration * SampleRate);
            float[] data = new float[samples];
            for (int i = 0; i < samples; i++)
            {
                float t = (float)i / SampleRate;
                float envelope = Mathf.Exp(-t * 4.5f);
                float freq = Mathf.Lerp(1400f, 90f, t / duration);
                float tone = Mathf.Sin(2f * Mathf.PI * freq * t);
                float noise = (Random.value * 2f - 1f);
                data[i] = Mathf.Clamp((tone * 0.6f + noise * 0.5f) * envelope, -1f, 1f);
            }
            return AudioClipFrom(data, samples);
        }

        AudioClip AudioClipFrom(float[] data, int samples)
        {
            var clip = AudioClip.Create("Clip", samples, 1, SampleRate, false);
            clip.SetData(data, 0);
            return clip;
        }
    }
}
