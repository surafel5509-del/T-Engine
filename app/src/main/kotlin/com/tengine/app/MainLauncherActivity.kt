package com.tengine.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tengine.editor.ui.EditorTheme

/**
 * T-Engine Hub Launcher Activity:
 * Lets the developer or player open the full Unity-style **T-Engine 2D Studio**
 * OR launch any of the 3 standalone hardware-accelerated 60 FPS 2D games!
 */
class MainLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(EditorTheme.BG_DEEP)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = EditorTheme.dp(this@MainLauncherActivity, 24f)
            setPadding(pad, pad, pad, pad)
        }

        val badge = TextView(this).apply {
            text = "T-ENGINE 2D  •  KOTLIN ANDROID GAME ENGINE & STUDIO"
            setTextColor(EditorTheme.ACCENT_CYAN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        container.addView(badge)

        val heroTitle = TextView(this).apply {
            text = "Professional 2D Game Engine & Visual Studio"
            setTextColor(EditorTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, EditorTheme.dp(context, 4f), 0, EditorTheme.dp(context, 4f))
        }
        container.addView(heroTitle)

        val subtitle = TextView(this).apply {
            text = "Built from scratch in Kotlin: OpenGL ES Batch Renderer • 2D Multi-Light Shaders • SAT & Sequential Impulse Physics • Tilemaps • Particle Emitters • Behavior Trees & A* • Unity-Style On-Device Visual Editor"
            setTextColor(EditorTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, EditorTheme.dp(context, 18f))
        }
        container.addView(subtitle)

        // Primary Studio Card
        container.addView(
            buildFeatureCard(
                title = "Open T-Engine 2D Studio (Unity-Like Visual Editor)",
                description = "Full Scene Hierarchy, Interactive Move/Rotate/Scale Gizmos, Tilemap Brush, Live Component Inspector, Undo/Redo, Play/Pause/Step/Stop Mode, JSON Scene Save/Load & Live Frame Profiler!",
                accentColor = EditorTheme.ACCENT_BLUE,
                buttonLabel = "Launch 2D Studio ▶"
            ) {
                startActivity(Intent(this, StudioActivity::class.java))
            }
        )

        // 3 Standalone Playable Games Row
        val gamesHeader = TextView(this).apply {
            text = "STANDALONE PLAYABLE 2D SHOWCASE GAMES"
            setTextColor(EditorTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, EditorTheme.dp(context, 14f), 0, EditorTheme.dp(context, 8f))
        }
        container.addView(gamesHeader)

        container.addView(
            buildFeatureCard(
                title = "1. Neon Knight: Cyber Citadel (2D Action Platformer)",
                description = "Tilemap world with merged colliders, Coyote-Time + Jump-Buffer + Air-Dash controller, Dynamic 2D Point Lights, Behavior-Tree Patrol/Chase Slimes, Parallax & Synth Audio.",
                accentColor = EditorTheme.ACCENT_GREEN,
                buttonLabel = "Play Platformer"
            ) {
                launchGame(PlayerActivity.GAME_NEON_KNIGHT)
            }
        )

        container.addView(
            buildFeatureCard(
                title = "2. 2D Rigid-Body Physics & Joint Sandbox Lab",
                description = "Interactive crate pyramid (2-point SAT clipping), DistanceJoint wrecking ball, SpringJoint polygon dancer, Zero-G toggle, and touch-to-launch impulse orbs!",
                accentColor = EditorTheme.ACCENT_AMBER,
                buttonLabel = "Play Physics Lab"
            ) {
                launchGame(PlayerActivity.GAME_PHYSICS_LAB)
            }
        )

        container.addView(
            buildFeatureCard(
                title = "3. Star Swarm Survivor (Top-Down Bullet Heaven)",
                description = "Object-pooled auto-aiming plasma bolts, swarming alien drones, TrailRenderers, additive particle bursts, trauma camera shake & XP fire-rate upgrades!",
                accentColor = Color.parseColor("#B646F5"),
                buttonLabel = "Play Survivor Arena"
            ) {
                launchGame(PlayerActivity.GAME_SPACE_SURVIVOR)
            }
        )

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    private fun launchGame(gameId: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_GAME_ID, gameId)
        }
        startActivity(intent)
    }

    private fun buildFeatureCard(
        title: String,
        description: String,
        accentColor: Int,
        buttonLabel: String,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = EditorTheme.roundedBox(this@MainLauncherActivity, EditorTheme.BG_CARD, cornerDp = 8f, strokeColor = accentColor, strokeDp = 1.5f)
            val pad = EditorTheme.dp(this@MainLauncherActivity, 14f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = EditorTheme.dp(this@MainLauncherActivity, 10f)
            }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = EditorTheme.dp(this@MainLauncherActivity, 12f)
            }
        }

        textCol.addView(TextView(this).apply {
            text = title
            setTextColor(EditorTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })

        textCol.addView(TextView(this).apply {
            text = description
            setTextColor(EditorTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, EditorTheme.dp(context, 4f), 0, 0)
        })

        val launchBtn = EditorTheme.createToolButton(
            this,
            label = buttonLabel,
            bgColor = accentColor,
            textColor = Color.WHITE,
            onClick = onClick
        )

        card.addView(textCol)
        card.addView(launchBtn)
        return card
    }
}
