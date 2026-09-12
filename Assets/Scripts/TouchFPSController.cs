using UnityEngine;

namespace HollowHalls
{
    [RequireComponent(typeof(CharacterController))]
    public class TouchFPSController : MonoBehaviour
    {
        public Transform CameraPivot;
        public float MoveSpeed = 3.2f;
        public float LookSensitivity = 0.15f;
        public float Gravity = -18f;

        CharacterController controller;
        float pitch = 0f;
        float verticalVelocity = 0f;

        int moveFingerId = -1;
        Vector2 moveOrigin;
        int lookFingerId = -1;
        Vector2 lastLookPos;

        void Awake()
        {
            controller = GetComponent<CharacterController>();
        }

        void Update()
        {
            if (GameManager.Instance == null || GameManager.Instance.State != GameState.Playing)
                return;

            Vector2 moveInput = Vector2.zero;
            Vector2 lookDelta = Vector2.zero;

            if (Input.touchCount > 0)
            {
                for (int i = 0; i < Input.touchCount; i++)
                {
                    Touch t = Input.GetTouch(i);
                    bool isLeftHalf = t.position.x < Screen.width / 2f;

                    if (isLeftHalf)
                    {
                        if (t.phase == TouchPhase.Began && moveFingerId == -1)
                        {
                            moveFingerId = t.fingerId;
                            moveOrigin = t.position;
                        }
                        else if (t.fingerId == moveFingerId)
                        {
                            if (t.phase == TouchPhase.Ended || t.phase == TouchPhase.Canceled)
                            {
                                moveFingerId = -1;
                            }
                            else
                            {
                                Vector2 diff = t.position - moveOrigin;
                                float maxRadius = 120f;
                                moveInput = new Vector2(
                                    Mathf.Clamp(diff.x / maxRadius, -1f, 1f),
                                    Mathf.Clamp(diff.y / maxRadius, -1f, 1f));
                            }
                        }
                    }
                    else
                    {
                        if (t.phase == TouchPhase.Began && lookFingerId == -1)
                        {
                            lookFingerId = t.fingerId;
                            lastLookPos = t.position;
                        }
                        else if (t.fingerId == lookFingerId)
                        {
                            if (t.phase == TouchPhase.Ended || t.phase == TouchPhase.Canceled)
                            {
                                lookFingerId = -1;
                            }
                            else
                            {
                                lookDelta = t.position - lastLookPos;
                                lastLookPos = t.position;
                            }
                        }
                    }
                }
            }
#if UNITY_EDITOR
            else
            {
                moveInput = new Vector2(Input.GetAxis("Horizontal"), Input.GetAxis("Vertical"));
                if (Input.GetMouseButton(0))
                {
                    lookDelta = new Vector2(Input.GetAxis("Mouse X"), Input.GetAxis("Mouse Y")) * 25f;
                }
            }
#endif

            // Look
            transform.Rotate(Vector3.up, lookDelta.x * LookSensitivity);
            pitch = Mathf.Clamp(pitch - lookDelta.y * LookSensitivity, -75f, 75f);
            if (CameraPivot != null)
                CameraPivot.localRotation = Quaternion.Euler(pitch, 0f, 0f);

            // Move
            Vector3 move = transform.right * moveInput.x + transform.forward * moveInput.y;
            if (move.sqrMagnitude > 1f) move.Normalize();

            if (controller.isGrounded)
                verticalVelocity = -0.5f;
            else
                verticalVelocity += Gravity * Time.deltaTime;

            Vector3 finalMove = move * MoveSpeed + Vector3.up * verticalVelocity;
            controller.Move(finalMove * Time.deltaTime);
        }
    }
}
