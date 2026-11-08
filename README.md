# T-Engine 2D — Professional Unity-Like Android 2D Game Engine & Visual Studio in Kotlin

**T-Engine** is a complete, modular, hardware-accelerated **2D Game Engine and Visual Scene Editor (Studio)** built **100% from scratch in Kotlin** for Android.

Designed around a familiar **Unity-inspired workflow** (`Scene`, `GameObject`, `Transform2D`, `Component`, `Behaviour`, `Prefab`, `SpriteRenderer`, `Animator2D`, `Tilemap`, `ParticleSystem2D`, `Light2D`, `Rigidbody2D`, `Collider2D`, `UICanvas`, and an on-device **Visual Editor** with **Play / Pause / Step / Stop** mode), T-Engine empowers developers to author, inspect, test, profile, and ship 60–120 FPS 2D Android games directly in Kotlin.

---

## Key Engine Capabilities

| Subsystem | Highlights |
| :--- | :--- |
| **ECS & Scene Graph** | Unity-style `Scene`, `SceneManager`, hierarchical `GameObject` & cached `Transform2D` matrices, `Behaviour` (`onAwake`, `onEnable`, `onStart`, `onFixedUpdate`, `onUpdate`, `onLateUpdate`, `onCollision*2D`, `onTrigger*2D`, `onDrawGizmos`), `Prefab` templates, and zero-dependency RFC 8259 JSON `SceneSerializer`. |
| **OpenGL ES 2D Renderer** | Dynamic quad `SpriteBatch` (up to 4,096 quads/batch), custom GLSL ES Unlit & **Dynamic 8-Light 2D Lit Sprite Shaders**, `SortingLayer` + `orderInLayer` + Y-sorting, frustum culling, `ShapeRenderer`, `TrailRenderer2D`, `ParallaxLayer`, and automatic EGL context recovery on Android pause/resume. |
| **Sprite Animation & Tilemaps** | `SpriteAtlas` grid slicer, `Animator2D` state machine (`LOOP`, `ONCE`, `PING_PONG`, Bool/Float/Trigger conditional transitions & frame events), sparse `Tilemap` with **greedy 2D rectangle collider merging**. |
| **Zero-GC Particle System** | Structure-of-Arrays (`FloatArray`) `ParticleSystem2D` supporting `POINT`, `CIRCLE`, `CONE`, and `BOX` emitters, bursts, drag, gravity, size curves, `ColorGradient`, and additive/alpha blending. |
| **From-Scratch 2D Physics** | Fixed-timestep `PhysicsWorld2D`, 32×32 Layer Collision Matrix, $O(N)$ `SpatialHashGrid` broadphase, analytical Circle/OBB/Convex-Polygon **Separating Axis Theorem (SAT) + 2-point Sutherland-Hodgman clipping**, **Sequential Impulse Solver** with Coulomb friction & Baumgarte stabilization, `DistanceJoint2D`, `SpringJoint2D`, and `raycast` / `overlapCircle` / `overlapAABB`. |
| **Input & In-Game UI** | Thread-safe 10-finger multi-touch `Input`, `GestureRecognizer2D`, gamepad & keyboard bindings, accelerometer tilt, resolution-scaled `UICanvas` (`UIVirtualJoystick`, `UIButton`, `UIProgressBar`, `UILabel`, `UIPanel`). |
| **AI, Tweens & Audio** | Binary-heap `AStarPathfinder` with line-of-sight string-pulling, `BehaviorTree` + `Blackboard`, typed `StateMachine`, fluent `TweenManager` (25 Robert Penner easings), spatial `AudioSource2D`, Android `SoundPool` + `MediaPlayer` + **Procedural 16-bit PCM WAV Synthesizer**. |
| **T-Engine 2D Studio** | On-device dark-mode visual IDE (`:tengine-editor`): recursive **Scene Hierarchy**, interactive **Move / Rotate / Scale Transform Gizmos**, **Tilemap Brush**, live **Component Inspector**, **Undo / Redo**, **Play / Pause / Step / Stop** snapshot workflow, and live **Sparkline Frame Profiler & Console**. |

---

## Multi-Module Architecture

```text
T-Engine/
├── tengine-core/      # Pure Kotlin platform-independent 2D Engine Core & JUnit4 Test Suite
├── tengine-android/   # Android OpenGL ES 2.0/3.0 Backend, Multi-Touch, SoundPool/Synth Audio, Dynamic Fonts, Storage
├── tengine-editor/    # T-Engine 2D Studio: Unity-like Visual Scene Editor, Gizmos, Inspector & Live Profiler
├── app/               # T-Engine Hub + Studio Host + 3 Full Playable Showcase 2D Games
└── docs/              # Architecture Deep-Dive, Scripting Guide & Studio User Manual
```

---

## Included Playable 2D Showcase Games (`:app`)

1. **Neon Knight: Cyber Citadel** — Full 2D Action Platformer featuring a merged-collider `Tilemap`, Coyote-Time + Jump-Buffering + Air-Dash character controller, dynamic 2D Point Lights, `BehaviorTree` ledge-aware patrolling/chasing slimes, collectible gems, parallax towers, and virtual touch controls.
2. **2D Physics & Joint Sandbox Lab** — Interactive rigid-body laboratory showcasing a 5-tier crate pyramid (2-point SAT contact stability), `DistanceJoint2D` wrecking pendulum, `SpringJoint2D` convex polygon dancer, Zero-G toggle, and touch-to-fire impulse orbs.
3. **Star Swarm Survivor** — Fast-paced top-down arena shooter powered by `ObjectPool` auto-aiming plasma bolts, swarming alien drones, `TrailRenderer2D` ribbons, additive particle explosions, and camera trauma shake.

---

## Quick Start: Building & Running

### Prerequisites
- **JDK 17+** (Temurin / Android Studio bundled JBR)
- **Android Studio Ladybug (2024.2+) or newer** / Android SDK Platform **API 35** (`minSdk = 24`)

### Build & Run via Gradle CLI

```bash
# 1. Run pure-Kotlin Engine Core unit tests (Math, SAT Physics, Raycast, A*, Tilemap, JSON Serialization)
./gradlew :tengine-core:test

# 2. Assemble the installable Debug APK (T-Engine Studio + 3 Showcase Games)
./gradlew :app:assembleDebug

# 3. Install onto a connected Android device or emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Writing a Game Script in Kotlin (Unity-Style `Behaviour`)

```kotlin
class PlayerShipScript : Behaviour("PlayerShipScript") {
    var speed: Float = 8.5f

    override fun onUpdate(dt: Float) {
        val rb = getComponent<Rigidbody2D>() ?: return
        val move = Vector2(Input.getAxis("Horizontal"), Input.getAxis("Vertical"))
        rb.linearVelocity.set(move * speed)

        if (Input.isActionJustPressed("Jump")) {
            scene?.audioMixer?.playSynth(SynthPreset.LASER)
            scene?.findMainCamera()?.addTrauma(0.2f)
        }
    }

    override fun onTriggerEnter2D(other: Collider2D) {
        if (other.gameObject.tag == "Collectible") {
            other.gameObject.destroy()
        }
    }

    override fun inspectableProperties() = listOf(
        InspectorProperty.FloatProp("Ship Speed", 1f, 30f, 0.5f, { speed }, { speed = it })
    )
}
```

---

## Documentation
- [**Engine Architecture Deep Dive**](docs/ARCHITECTURE.md)
- [**Kotlin Gameplay Scripting Guide**](docs/SCRIPTING_GUIDE.md)
- [**T-Engine 2D Studio Manual**](docs/EDITOR_MANUAL.md)
