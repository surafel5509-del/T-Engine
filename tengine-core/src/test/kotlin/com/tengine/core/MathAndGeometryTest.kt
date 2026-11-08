package com.tengine.core

import com.tengine.core.math.AABB
import com.tengine.core.math.AnimationCurve
import com.tengine.core.math.Color
import com.tengine.core.math.Easing
import com.tengine.core.math.Geometry2D
import com.tengine.core.math.Mathf
import com.tengine.core.math.Matrix3
import com.tengine.core.math.Matrix4
import com.tengine.core.math.Ray2D
import com.tengine.core.math.Vector2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathAndGeometryTest {

    @Test
    fun testVector2AlgebraAndRotation() {
        val a = Vector2(3f, 4f)
        assertEquals(5f, a.length, 1e-4f)

        val rotated90 = Vector2(1f, 0f).rotatedDeg(90f)
        assertEquals(0f, rotated90.x, 1e-4f)
        assertEquals(1f, rotated90.y, 1e-4f)

        val crossVal = Vector2(1f, 0f) cross Vector2(0f, 1f)
        assertEquals(1f, crossVal, 1e-4f)
    }

    @Test
    fun testMatrix3AndMatrix4Inversion() {
        val m3 = Matrix3().setTRS(10f, -5f, 45f * Mathf.DEG_TO_RAD, 2f, 3f)
        val inv3 = Matrix3()
        assertTrue(m3.invert(inv3))

        val original = Vector2(4f, -2f)
        val world = m3.transformPoint(original)
        val roundTrip = inv3.transformPoint(world)
        assertEquals(original.x, roundTrip.x, 1e-3f)
        assertEquals(original.y, roundTrip.y, 1e-3f)

        val ortho = Matrix4().setOrtho(-10f, 10f, -5f, 5f, -100f, 100f)
        val invOrtho = Matrix4()
        assertTrue(ortho.invert(invOrtho))
        val ndc = ortho.transformPoint2D(Vector2(5f, 2.5f))
        assertEquals(0.5f, ndc.x, 1e-4f)
        assertEquals(0.5f, ndc.y, 1e-4f)
        val unprojected = invOrtho.transformPoint2D(ndc)
        assertEquals(5f, unprojected.x, 1e-4f)
        assertEquals(2.5f, unprojected.y, 1e-4f)
    }

    @Test
    fun testColorHexAndHsvRoundTrip() {
        val c = Color.fromHex("#FF8000FF")
        assertEquals(1f, c.r, 0.01f)
        assertEquals(0.502f, c.g, 0.01f)
        assertEquals(0f, c.b, 0.01f)

        val hsv = c.toHSV()
        val rebuilt = Color.fromHSV(hsv[0], hsv[1], hsv[2], c.a)
        assertEquals(c.r, rebuilt.r, 0.02f)
        assertEquals(c.g, rebuilt.g, 0.02f)
        assertEquals(c.b, rebuilt.b, 0.02f)
    }

    @Test
    fun testAabbRaycastAndConvexHull() {
        val box = AABB(-1f, -1f, 1f, 1f)
        val ray = Ray2D(Vector2(-5f, 0f), Vector2(1f, 0f))
        val dist = box.intersectRay(ray, 20f)
        assertEquals(4f, dist, 1e-4f)

        val cloud = listOf(
            Vector2(0f, 0f),
            Vector2(2f, 0f),
            Vector2(2f, 2f),
            Vector2(0f, 2f),
            Vector2(1f, 1f) // interior point must be discarded by hull
        )
        val hull = Geometry2D.convexHull(cloud)
        assertEquals(4, hull.size)
        assertTrue(Geometry2D.signedArea(hull) > 0f)
    }

    @Test
    fun testEasingAndAnimationCurve() {
        for (e in Easing.entries) {
            assertEquals(0f, e.evaluate(0f), 1e-3f)
            assertEquals(1f, e.evaluate(1f), 1e-3f)
        }
        val curve = AnimationCurve.linear(2f, 10f)
        assertEquals(6f, curve.evaluate(0.5f), 1e-3f)
        assertNotNull(curve)
    }
}
