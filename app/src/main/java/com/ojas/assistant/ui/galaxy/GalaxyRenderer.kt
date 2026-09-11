package com.ojas.assistant.ui.galaxy

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Draws the galaxy in two additive passes over one vertex buffer.
 *
 * Pass one blows every star up into a large, very faint sprite, which is what produces
 * the milky glow along the arms. Pass two draws the same stars small and bright for the
 * crisp cores. Two draw calls, no depth buffer, no per-frame allocation.
 *
 * Orientation state is written from the UI thread and read on the GL thread, hence the
 * volatile fields; both are plain floats so a torn read is impossible.
 */
class GalaxyRenderer(initialStarCount: Int) : GLSurfaceView.Renderer {

    @Volatile var yawDegrees: Float = 0f
    @Volatile var pitchDegrees: Float = 0f
    @Volatile var distance: Float = DEFAULT_DISTANCE
    @Volatile var spinEnabled: Boolean = true

    /** Frames are capped rather than free-running: 30fps of slow drift is plenty. */
    @Volatile var targetFrameMillis: Long = 33L

    @Volatile private var requestedStarCount: Int = initialStarCount
    private var uploadedStarCount: Int = 0

    private var program = 0
    private var vbo = 0

    private var aPos = 0
    private var aColor = 0
    private var aSize = 0
    private var aRadius = 0
    private var uMvp = 0
    private var uTime = 0
    private var uSpin = 0
    private var uPointScale = 0
    private var uSizeMul = 0
    private var uAlpha = 0
    private var uSharpness = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val temp = FloatArray(16)
    private val mvp = FloatArray(16)

    private var pointScale = 8f
    private var spinPhase = 0f
    private var lastFrameNanos = 0L

    fun setStarCount(count: Int) {
        requestedStarCount = count.coerceIn(600, 20000)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(BG_R, BG_G, BG_B, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Premultiplied additive: overlapping stars accumulate into the arm glow.
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) return

        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        aSize = GLES20.glGetAttribLocation(program, "aSize")
        aRadius = GLES20.glGetAttribLocation(program, "aRadius")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uSpin = GLES20.glGetUniformLocation(program, "uSpin")
        uPointScale = GLES20.glGetUniformLocation(program, "uPointScale")
        uSizeMul = GLES20.glGetUniformLocation(program, "uSizeMul")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uSharpness = GLES20.glGetUniformLocation(program, "uSharpness")

        // The context was just (re)created, so any previous buffer name is stale.
        vbo = 0
        uploadedStarCount = 0
        uploadStars()

        Matrix.setLookAtM(view, 0, 0f, 0f, DEFAULT_DISTANCE, 0f, 0f, 0f, 0f, 1f, 0f)
        lastFrameNanos = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, FOV_DEGREES, aspect, 0.1f, 24f)
        // Sprite size follows resolution so the galaxy looks identical on any density.
        pointScale = max(height, 1) * 0.0040f
    }

    override fun onDrawFrame(gl: GL10?) {
        throttle()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        if (requestedStarCount != uploadedStarCount) uploadStars()
        if (uploadedStarCount == 0) return

        advanceSpin()

        Matrix.setLookAtM(view, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0)
        // Turntable order: yaw about the world up axis first, then tip toward the viewer.
        Matrix.rotateM(model, 0, pitchDegrees, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yawDegrees, 0f, 1f, 0f)
        Matrix.multiplyMM(temp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform1f(uTime, spinPhase)
        GLES20.glUniform1f(uSpin, SPIN_RATE)
        GLES20.glUniform1f(uPointScale, pointScale)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        bindAttributes()

        // Pass 1: broad, faint halos that merge into the arm glow.
        GLES20.glUniform1f(uSizeMul, GLOW_SIZE_MUL)
        GLES20.glUniform1f(uAlpha, GLOW_ALPHA)
        GLES20.glUniform1f(uSharpness, GLOW_SHARPNESS)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, uploadedStarCount)

        // Pass 2: the star cores themselves.
        GLES20.glUniform1f(uSizeMul, CORE_SIZE_MUL)
        GLES20.glUniform1f(uAlpha, CORE_ALPHA)
        GLES20.glUniform1f(uSharpness, CORE_SHARPNESS)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, uploadedStarCount)

        disableAttributes()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    // ------------------------------------------------------------------ helpers

    private fun bindAttributes() {
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, GalaxyModel.STRIDE_BYTES, 0)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, GalaxyModel.STRIDE_BYTES, 12)
        GLES20.glEnableVertexAttribArray(aSize)
        GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, GalaxyModel.STRIDE_BYTES, 24)
        GLES20.glEnableVertexAttribArray(aRadius)
        GLES20.glVertexAttribPointer(aRadius, 1, GLES20.GL_FLOAT, false, GalaxyModel.STRIDE_BYTES, 28)
    }

    private fun disableAttributes() {
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aColor)
        GLES20.glDisableVertexAttribArray(aSize)
        GLES20.glDisableVertexAttribArray(aRadius)
    }

    private fun uploadStars() {
        val count = requestedStarCount
        val data: FloatBuffer = GalaxyModel.build(count)

        if (vbo == 0) {
            val ids = IntArray(1)
            GLES20.glGenBuffers(1, ids, 0)
            vbo = ids[0]
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            count * GalaxyModel.STRIDE_BYTES,
            data,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        uploadedStarCount = count
    }

    private fun advanceSpin() {
        val now = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1e9f
        lastFrameNanos = now
        if (spinEnabled) {
            // Clamped so returning from a long pause does not jump the galaxy forward.
            spinPhase += deltaSeconds.coerceIn(0f, 0.25f)
        }
    }

    /**
     * Sleeps the GL thread down to the target frame interval. A background animation has
     * no business burning a full 120Hz of GPU time on a phone the user is not looking at.
     */
    private fun throttle() {
        val target = targetFrameMillis
        if (target <= 0L || lastFrameNanos == 0L) return
        val elapsedMs = (System.nanoTime() - lastFrameNanos) / 1_000_000L
        val remaining = target - elapsedMs
        if (remaining > 1L) {
            runCatching { Thread.sleep(remaining) }
        }
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0

        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)

        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "link failed: " + GLES20.glGetProgramInfoLog(id))
            GLES20.glDeleteProgram(id)
            return 0
        }
        // The program keeps its own reference once linked.
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return id
    }

    private fun compile(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "compile failed: " + GLES20.glGetShaderInfoLog(id))
            GLES20.glDeleteShader(id)
            return 0
        }
        return id
    }

    companion object {
        private const val TAG = "GalaxyRenderer"

        const val DEFAULT_DISTANCE = 3.05f
        const val MIN_DISTANCE = 1.6f
        const val MAX_DISTANCE = 6.5f

        private const val FOV_DEGREES = 42f
        private const val SPIN_RATE = 0.030f

        private const val GLOW_SIZE_MUL = 7.5f
        private const val GLOW_ALPHA = 0.052f
        private const val GLOW_SHARPNESS = 1.30f
        private const val CORE_SIZE_MUL = 1.0f
        private const val CORE_ALPHA = 1.0f
        private const val CORE_SHARPNESS = 3.1f

        // Matches SpaceVoid so the GL surface and the Compose scrim are seamless.
        private const val BG_R = 0.0157f
        private const val BG_G = 0.0235f
        private const val BG_B = 0.0510f

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform float uTime;
            uniform float uSpin;
            uniform float uPointScale;
            uniform float uSizeMul;

            attribute vec3 aPos;
            attribute vec3 aColor;
            attribute float aSize;
            attribute float aRadius;

            varying vec3 vColor;

            void main() {
                // Rigid rotation with a small differential term, so the arms shear very
                // slowly the way a real disc does instead of turning like a solid plate.
                float angle = uTime * uSpin * (1.0 + 0.22 / (0.45 + aRadius));
                float c = cos(angle);
                float s = sin(angle);
                vec4 spun = vec4(
                    aPos.x * c - aPos.y * s,
                    aPos.x * s + aPos.y * c,
                    aPos.z,
                    1.0
                );

                vec4 clip = uMvp * spun;
                gl_Position = clip;
                gl_PointSize = clamp(
                    uPointScale * aSize * uSizeMul / max(clip.w, 0.2),
                    1.0,
                    110.0
                );
                vColor = aColor;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;

            uniform float uAlpha;
            uniform float uSharpness;

            varying vec3 vColor;

            void main() {
                vec2 offset = gl_PointCoord - vec2(0.5);
                float r2 = dot(offset, offset) * 4.0;
                if (r2 > 1.0) discard;
                float falloff = pow(1.0 - r2, uSharpness);
                gl_FragColor = vec4(vColor * falloff * uAlpha, 1.0);
            }
        """
    }
}
