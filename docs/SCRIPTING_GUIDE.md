# T-Engine 2D — Kotlin Gameplay Scripting Guide

## 1. Creating a Standalone Android 2D Game Activity

Subclass `TEngineActivity` in your Android module and return a configured `Scene`:

```kotlin
class MyGameActivity : TEngineActivity() {
    override fun createInitialScene(): Scene {
        val scene = Scene("MainLevel", gravity = Vector2(0f, -20f))

        val cameraObj = scene.createGameObject("MainCamera")
        cameraObj.addComponent(Camera2D(orthographicSize = 5.5f))

        val player = scene.createGameObject("Hero", Vector2(0f, 2f)).apply {
            tag = "Player"
            addComponent(SpriteRenderer(Sprite.DIAMOND, Color.CYAN))
            addComponent(Rigidbody2D(BodyType.DYNAMIC).apply { freezeRotation = true })
            addComponent(BoxCollider2D(Vector2(1f, 1f)))
            addComponent(HeroBehaviour())
        }
        return scene
    }
}
```

---

## 2. Writing Custom `Behaviour` Components

`Behaviour` mirrors Unity's `MonoBehaviour` lifecycle:

- `onAwake()` — Called once as soon as the component joins an initialized `Scene`.
- `onEnable()` / `onDisable()` — Called when the component or its `GameObject` becomes active/inactive.
- `onStart()` — Called right before the component's first `onFixedUpdate` or `onUpdate`.
- `onFixedUpdate(fixedDt: Float)` — Fixed timestep (`1/60s`); apply continuous physics forces here.
- `onUpdate(dt: Float)` — Per-frame gameplay logic, input polling, state machines.
- `onLateUpdate(dt: Float)` — Runs after all `onUpdate`, schedulers, and tweens (ideal for cameras).
- `onCollisionEnter2D` / `Stay2D` / `Exit2D(collision: CollisionEvent2D)` — Solid rigid-body contact callbacks.
- `onTriggerEnter2D` / `Stay2D` / `Exit2D(other: Collider2D)` — Sensor overlap callbacks (`isTrigger = true`).

### Registering Custom Behaviours for Scene Serialization & Studio Inspector

Register your script in `ComponentRegistry` so T-Engine Studio can add it from **+ Add Component** and serialize it to `.tescene.json`:

```kotlin
ComponentRegistry.register("HeroBehaviour", "Gameplay") { HeroBehaviour() }
```

Override `serialize()`, `deserialize()`, and `inspectableProperties()` to expose live-editable sliders/fields in Studio!

---

## 3. Input, Virtual Joysticks & Action Buttons

```kotlin
// Read unified movement (-1..+1) from VirtualJoystick, WASD/Arrows, or Bluetooth Gamepad!
val moveX = Input.getAxis("Horizontal")
val moveY = Input.getAxis("Vertical")

// Edge-triggered actions bound to UIButtons, Keyboard, and Gamepad buttons:
if (Input.isActionJustPressed("Jump")) { /* Jump! */ }
if (Input.isActionPressed("Attack")) { /* Hold fire! */ }
```

---

## 4. Physics2D Spatial Queries

```kotlin
val hit = scene?.physicsWorld?.raycast(
    origin = transform.worldPosition,
    direction = Vector2.RIGHT,
    maxDistance = 12f
)
if (hit != null) {
    EngineLogger.info("Combat", "Laser hit ${hit.collider.gameObject.name} at ${hit.point}")
}
```

---

## 5. Tweens, Timers & AI

```kotlin
// Smooth scale punch tween
scene?.tweens?.tweenScale(transform, Vector2(1.4f, 1.4f), duration = 0.2f, easing = Easing.OUT_BACK, yoyo = true, repeatCount = 2)

// Game-time delayed action (pauses automatically when Time.timeScale == 0!)
invokeDelayed(1.5f) {
    gameObject.destroy()
}

// Grid A* Pathfinding
val path: List<Vector2> = AStarPathfinder.findWorldPath(navGrid, myPos, targetPos)
```
