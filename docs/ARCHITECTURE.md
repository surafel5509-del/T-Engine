# T-Engine 2D — Engine Architecture Deep Dive

## 1. Design Philosophy

T-Engine separates pure deterministic simulation from Android OS hardware APIs across four cleanly layered Gradle modules:

1. **`:tengine-core`** — Pure Kotlin (`kotlin.*`, `kotlinx-coroutines-core`) with **zero Android SDK imports**. Runs identically on Android ART and standard desktop JVMs for fast headless simulation and unit testing.
2. **`:tengine-android`** — Hardware-accelerated Android runtime implementing `RenderBackend` on **OpenGL ES 2.0/3.0** (`GLESRenderBackend`), `AudioBackend` on `SoundPool` + `MediaPlayer` + `ProceduralSfxGenerator`, multi-touch/gamepad/sensor `AndroidInputBridge`, `DynamicFontAtlas`, and `TEngineActivity`.
3. **`:tengine-editor`** — **T-Engine 2D Studio**, an embeddable visual scene editor featuring `EditorController`, `TransformGizmo2D`, `HierarchyPanelView`, `InspectorPanelView`, and `ConsoleAndProfilerView`.
4. **`:app`** — Android application packaging the T-Engine Hub, T-Engine 2D Studio, and three playable showcase games.

---

## 2. Master Frame Loop (`Engine.tickFrame`)

Every rendered frame on the OpenGL thread executes a deterministic pipeline:

```text
[1] Time.stepFrame(rawDeltaSeconds)        -> Computes scaled deltaTime, unscaledDeltaTime, smoothed FPS
[2] Input.beginFrame()                     -> Drains thread-safe Android touch & key queues
[3] SceneManager.applyPendingTransition()  -> Swaps active Scene cleanly between frames if requested
[4] UICanvas.updateInput() + Action Edge   -> Evaluates Virtual Joysticks & Action Buttons
[5] Fixed-Timestep Physics Accumulator     -> While accumulator >= fixedDeltaTime (1/60s, max 6 substeps):
      ├── Behaviour.onFixedUpdate(fixedDt)
      ├── Joint2D.applyForces(fixedDt)
      ├── Rigidbody2D Force/Gravity Integration + Damping
      ├── SpatialHashGrid Broadphase -> Narrowphase2D (Circle / OBB / Convex SAT + Edge Clipping)
      ├── ContactSolver2D (Iterative Sequential Impulse Velocity + Coulomb Friction + Baumgarte Position)
      └── Dispatch onCollisionEnter/Stay/Exit2D & onTriggerEnter/Stay/Exit2D
[6] Variable-Timestep Gameplay Update:
      ├── Behaviour.onStart() (first active frame) & Behaviour.onUpdate(dt)
      ├── CoroutineScheduler.update(dt)    -> Game-time delayed/repeating tasks
      ├── TweenManager.update(dt)          -> Property & Transform tweens
      ├── Behaviour.onLateUpdate(dt)       -> Smooth Camera2D tracking, Trauma Shake & ParallaxLayers
      └── Scene.flushPendingDestroys()
[7] RenderPipeline2D.renderScene():
      ├── Gather Global Ambient & up to 8 Point Light2Ds
      ├── Camera2D View-Projection Matrix + Frustum Culling AABB
      ├── Sort visible SpriteRenderers, Tilemaps, Trails & ParticleSystem2Ds
      ├── Dynamic SpriteBatch GPU submission (auto-flushing on Texture/Blend/Lit state change)
      ├── Optional Editor Grid + Collider/Light Gizmos + TransformGizmo2D Handles
      └── Screen-Space Orthographic UICanvas Overlay Pass
[8] FrameProfiler.record()                 -> Records FPS, CPU/Physics/Render ms, Draw Calls, Tris, Contacts
```

---

## 3. 2D Physics Engine Internals (`com.tengine.core.physics`)

### Broadphase: `SpatialHashGrid`
World space is partitioned into uniform cells (default `2.5m × 2.5m`) hashed into 64-bit `(cellX, cellY)` keys using recycled bucket lists to avoid GC allocations.

### Narrowphase: `Narrowphase2D`
- **Circle vs Circle**: Exact analytical distance check.
- **Circle vs OBB / Convex Polygon**: Voronoi edge/vertex region classification plus deep-center face normal fallback.
- **Convex Polygon / OBB vs Convex Polygon / OBB**: Full **Separating Axis Theorem (SAT)** finding the axis of minimum penetration, followed by **Sutherland-Hodgman incident-edge clipping** against the reference face's side planes. Producing **2 contact points** on flat face-to-face collisions ensures rock-solid box stacking without single-point rocking.

### Constraint Solver: `ContactSolver2D`
Uses Erin Catto's **Sequential Impulse** formulation:
1. Computes effective normal mass $K_n^{-1}$ and tangent mass $K_t^{-1}$ incorporating linear inverse masses and rotational inverse inertias ($r \times n$).
2. Applies velocity restitution bias only when closing speed exceeds `1.0 m/s` so resting stacks settle cleanly without micro-bouncing.
3. Clamps accumulated normal impulse $j_n \ge 0$ and clamps accumulated tangent friction impulse $j_t \in [-\mu j_n, +\mu j_n]$.
4. Applies Baumgarte positional correction above linear slop (`0.005m`) to eliminate sinking.

---

## 4. OpenGL ES Context Recovery (`GLESRenderBackend`)

On Android, switching apps or locking the screen can destroy and recreate the underlying EGL context. `GLESRenderBackend` increments a `contextGeneration` counter inside `onContextCreated()`. Every `Texture2D` retains its CPU `PixelBuffer` (or `assetPath`) alongside `gpuContextGeneration`; whenever a mismatch is detected during batch submission, T-Engine transparently re-generates the OpenGL texture handle and re-uploads pixel data without losing a single sprite or font glyph.
