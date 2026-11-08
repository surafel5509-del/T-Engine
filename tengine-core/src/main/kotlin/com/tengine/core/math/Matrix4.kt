package com.tengine.core.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Column-major 3x3 2D affine transformation matrix:
 * [ m00(0)  m01(3)  m02(6) ]
 * [ m10(1)  m11(4)  m12(7) ]
 * [ m20(2)  m21(5)  m22(8) ]
 */
class Matrix3 {
    val values: FloatArray = FloatArray(9).apply {
        this[0] = 1f
        this[4] = 1f
        this[8] = 1f
    }

    fun setIdentity(): Matrix3 {
        values.fill(0f)
        values[0] = 1f
        values[4] = 1f
        values[8] = 1f
        return this
    }

    fun setTRS(tx: Float, ty: Float, rotationRad: Float, sx: Float, sy: Float): Matrix3 {
        val c = cos(rotationRad)
        val s = sin(rotationRad)
        values[0] = c * sx
        values[1] = s * sx
        values[2] = 0f
        values[3] = -s * sy
        values[4] = c * sy
        values[5] = 0f
        values[6] = tx
        values[7] = ty
        values[8] = 1f
        return this
    }

    fun setFrom(other: Matrix3): Matrix3 {
        other.values.copyInto(values)
        return this
    }

    fun transformPoint(p: Vector2): Vector2 = Vector2(
        values[0] * p.x + values[3] * p.y + values[6],
        values[1] * p.x + values[4] * p.y + values[7]
    )

    fun transformPointX(x: Float, y: Float): Float = values[0] * x + values[3] * y + values[6]
    fun transformPointY(x: Float, y: Float): Float = values[1] * x + values[4] * y + values[7]

    fun transformVector(v: Vector2): Vector2 = Vector2(
        values[0] * v.x + values[3] * v.y,
        values[1] * v.x + values[4] * v.y
    )

    fun invert(out: Matrix3 = Matrix3()): Boolean {
        val m = values
        val a = m[0]; val b = m[3]; val c = m[6]
        val d = m[1]; val e = m[4]; val f = m[7]
        val g = m[2]; val h = m[5]; val i = m[8]

        val A = e * i - f * h
        val B = -(d * i - f * g)
        val C = d * h - e * g
        val det = a * A + b * B + c * C
        if (abs(det) < 1e-8f) return false

        val invDet = 1f / det
        val o = out.values
        o[0] = A * invDet
        o[1] = B * invDet
        o[2] = C * invDet
        o[3] = -(b * i - c * h) * invDet
        o[4] = (a * i - c * g) * invDet
        o[5] = -(a * h - b * g) * invDet
        o[6] = (b * f - c * e) * invDet
        o[7] = -(a * f - c * d) * invDet
        o[8] = (a * e - b * d) * invDet
        return true
    }

    companion object {
        fun multiply(lhs: Matrix3, rhs: Matrix3, out: Matrix3 = Matrix3()): Matrix3 {
            val a = lhs.values
            val b = rhs.values
            val r0 = a[0] * b[0] + a[3] * b[1] + a[6] * b[2]
            val r1 = a[1] * b[0] + a[4] * b[1] + a[7] * b[2]
            val r2 = a[2] * b[0] + a[5] * b[1] + a[8] * b[2]

            val r3 = a[0] * b[3] + a[3] * b[4] + a[6] * b[5]
            val r4 = a[1] * b[3] + a[4] * b[4] + a[7] * b[5]
            val r5 = a[2] * b[3] + a[5] * b[4] + a[8] * b[5]

            val r6 = a[0] * b[6] + a[3] * b[7] + a[6] * b[8]
            val r7 = a[1] * b[6] + a[4] * b[7] + a[7] * b[8]
            val r8 = a[2] * b[6] + a[5] * b[7] + a[8] * b[8]

            out.values[0] = r0; out.values[1] = r1; out.values[2] = r2
            out.values[3] = r3; out.values[4] = r4; out.values[5] = r5
            out.values[6] = r6; out.values[7] = r7; out.values[8] = r8
            return out
        }
    }
}

/**
 * Column-major 4x4 transformation matrix directly compatible with OpenGL ES (GLES20/GLES30).
 */
class Matrix4 {
    val values: FloatArray = FloatArray(16).apply {
        this[0] = 1f
        this[5] = 1f
        this[10] = 1f
        this[15] = 1f
    }

    fun setIdentity(): Matrix4 {
        values.fill(0f)
        values[0] = 1f
        values[5] = 1f
        values[10] = 1f
        values[15] = 1f
        return this
    }

    fun setFrom(other: Matrix4): Matrix4 {
        other.values.copyInto(values)
        return this
    }

    /**
     * Standard OpenGL orthographic projection matrix mapping [left..right], [bottom..top], [near..far] to NDC [-1..1].
     */
    fun setOrtho(
        left: Float,
        right: Float,
        bottom: Float,
        top: Float,
        near: Float = -100f,
        far: Float = 100f
    ): Matrix4 {
        val rl = right - left
        val tb = top - bottom
        val fn = far - near
        setIdentity()
        if (abs(rl) <= Mathf.EPSILON || abs(tb) <= Mathf.EPSILON || abs(fn) <= Mathf.EPSILON) {
            return this
        }
        values[0] = 2f / rl
        values[5] = 2f / tb
        values[10] = -2f / fn
        values[12] = -(right + left) / rl
        values[13] = -(top + bottom) / tb
        values[14] = -(far + near) / fn
        values[15] = 1f
        return this
    }

    /**
     * Sets 2D Translation-Rotation-Scale in XY plane with optional Z depth.
     */
    fun setTRS2D(
        x: Float,
        y: Float,
        rotationRad: Float,
        scaleX: Float,
        scaleY: Float,
        z: Float = 0f
    ): Matrix4 {
        val c = cos(rotationRad)
        val s = sin(rotationRad)
        values[0] = c * scaleX
        values[1] = s * scaleX
        values[2] = 0f
        values[3] = 0f

        values[4] = -s * scaleY
        values[5] = c * scaleY
        values[6] = 0f
        values[7] = 0f

        values[8] = 0f
        values[9] = 0f
        values[10] = 1f
        values[11] = 0f

        values[12] = x
        values[13] = y
        values[14] = z
        values[15] = 1f
        return this
    }

    fun transformPoint2D(p: Vector2): Vector2 {
        val nx = values[0] * p.x + values[4] * p.y + values[12]
        val ny = values[1] * p.x + values[5] * p.y + values[13]
        return Vector2(nx, ny)
    }

    fun transformPoint2DX(x: Float, y: Float): Float = values[0] * x + values[4] * y + values[12]
    fun transformPoint2DY(x: Float, y: Float): Float = values[1] * x + values[5] * y + values[13]

    /**
     * Full 4x4 matrix analytical inversion using cofactor expansion.
     */
    fun invert(out: Matrix4 = Matrix4()): Boolean {
        val m = values
        val inv = FloatArray(16)

        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
            m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
            m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
            m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
            m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]

        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
            m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
            m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
            m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
            m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]

        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
            m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
            m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
            m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
            m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]

        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
            m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
            m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
            m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
            m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (abs(det) < 1e-8f) return false

        val invDet = 1f / det
        for (i in 0 until 16) {
            out.values[i] = inv[i] * invDet
        }
        return true
    }

    companion object {
        fun multiply(lhs: Matrix4, rhs: Matrix4, out: Matrix4 = Matrix4()): Matrix4 {
            val a = lhs.values
            val b = rhs.values
            val tmp = FloatArray(16)
            for (col in 0 until 4) {
                val bCol = col * 4
                val b0 = b[bCol]
                val b1 = b[bCol + 1]
                val b2 = b[bCol + 2]
                val b3 = b[bCol + 3]
                tmp[bCol] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
                tmp[bCol + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
                tmp[bCol + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
                tmp[bCol + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
            }
            tmp.copyInto(out.values)
            return out
        }
    }
}
