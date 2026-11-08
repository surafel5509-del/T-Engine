# T-Engine 2D Studio — On-Device Visual Editor Manual

**T-Engine 2D Studio** brings a Unity-inspired visual 2D scene authoring workflow directly onto Android tablets and phones.

---

## 1. Workspace Layout

- **Top Toolbar**:
  - **Scenes ▾**: Create a new empty 2D scene, open any built-in Showcase Game template, or load a saved `.tescene.json` file.
  - **Save**: Serializes the current scene hierarchy and all component properties to internal storage.
  - **Hand / Move / Rotate / Scale / Tile**: Switches the active Scene Viewport manipulation tool.
  - **Snap**: Toggles `0.5m` grid snapping for Move handles and `15°` snapping for Rotate handles.
  - **Undo / Redo**: Reverts or reapplies up to 30 scene edits.
  - **▶ Play / ⏸ Pause / ⏭ Step / ⏹ Stop**:
    - Pressing **▶ Play** snapshots the editor scene to JSON, activates the Scene's `MainCamera`, enables in-game HUD `UICanvas` controls, and runs full Physics2D, Animations, AI, Particles, and Audio.
    - Pressing **⏸ Pause** freezes simulation in place so you can inspect live velocities and positions, while **⏭ Step** advances exactly one `1/60s` frame.
    - Pressing **⏹ Stop** cleanly restores the pre-Play editor snapshot!
  - **Tree / Insp / Stats**: Collapses or expands the Hierarchy, Inspector, and Profiler docks to maximize viewport space on compact phone screens.

---

## 2. Viewport Multi-Touch Controls (Edit Mode)

- **Two-Finger Pinch & Drag**: Zooms and pans the Editor Camera smoothly in **any** tool mode.
- **Single-Finger Tap**: Selects the tapped `GameObject` in the 2D Scene View and highlights its collider/sprite bounds.
- **Move Tool**: Drag the **Red X Arrow**, **Green Y Arrow**, or **Center Quad** to translate the selected `GameObject`.
- **Rotate Tool**: Drag the **Cyan Ring** around the selected `GameObject` to rotate it.
- **Scale Tool**: Drag the **X/Y Box Handles** or **Center Handle** to scale non-uniformly or uniformly.
- **Tile Tool**: Tap **Tile** again while active to pick a tile brush (Eraser, Stone, Grass, Neon Platform, Lava, Back Brick), then drag across the viewport to paint tiles with automatic merged-collider generation!
