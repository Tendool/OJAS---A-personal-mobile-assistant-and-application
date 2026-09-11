package com.ojas.assistant.ui.galaxy

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Procedural spiral galaxy.
 *
 * The whole model is generated once into a single interleaved float buffer that is
 * uploaded to a VBO and never touched again. Every frame after that is two draw calls
 * over the same buffer, which is what keeps a 6000-star galaxy essentially free on the
 * GPU and completely allocation-free on the CPU.
 *
 * Layout per vertex (8 floats / 32 bytes):
 *   [0..2] position x, y, z   (the galactic plane is XY, z is disc thickness)
 *   [3..5] colour r, g, b
 *   [6]    point size weight
 *   [7]    galactic radius, used by the shader for differential rotation
 */
object GalaxyModel {

    const val FLOATS_PER_STAR = 8
    const val STRIDE_BYTES = FLOATS_PER_STAR * 4

    private const val ARMS = 2

    /** Tightness of the logarithmic spiral; larger means more wraps before the rim. */
    private const val WINDING = 3.05f
    private const val INNER_RADIUS = 0.10f
    private const val OUTER_RADIUS = 1.0f
    private const val DISC_THICKNESS = 0.030f

    // Star temperatures sampled from the reference render.
    private val COOL = floatArrayOf(0.66f, 0.78f, 1.00f)   // blue-white, young arm stars
    private val WARM = floatArrayOf(1.00f, 0.70f, 0.44f)   // amber, older population
    private val NEUTRAL = floatArrayOf(1.00f, 0.98f, 0.95f)

    /**
     * Builds the star field. The seed is fixed so the galaxy a user learns to recognise
     * is the same one on every launch, even after changing the density setting.
     */
    fun build(starCount: Int, seed: Long = 20240917L): FloatBuffer {
        val count = starCount.coerceIn(600, 20000)
        val rng = Random(seed)

        val buffer = ByteBuffer
            .allocateDirect(count * STRIDE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // Population split: most stars trace the arms, a dense core anchors the centre,
        // and a sparse halo keeps the frame from ending abruptly at the disc edge.
        val coreCount = (count * 0.16f).toInt()
        val haloCount = (count * 0.07f).toInt()
        val armCount = count - coreCount - haloCount

        repeat(armCount) { writeArmStar(buffer, rng) }
        repeat(coreCount) { writeCoreStar(buffer, rng) }
        repeat(haloCount) { writeHaloStar(buffer, rng) }

        buffer.position(0)
        return buffer
    }

    // ------------------------------------------------------------- populations

    private fun writeArmStar(out: FloatBuffer, rng: Random) {
        // Bias the radial distribution outward slightly so the arms do not crowd the core.
        val t = rng.nextFloat().pow(0.72f)
        val radius = INNER_RADIUS + t * (OUTER_RADIUS - INNER_RADIUS)

        val arm = rng.nextInt(ARMS)
        val armAngle = arm * (2.0 * PI / ARMS).toFloat()
        val spiral = WINDING * ln(radius / INNER_RADIUS)

        // Scatter grows with radius: arms are crisp near the bulge and fray at the rim.
        val spread = 0.10f + 0.34f * t
        val angle = armAngle + spiral + gaussian(rng) * spread

        // A little radial jitter stops the arm reading as a drawn curve.
        val r = (radius + gaussian(rng) * 0.022f * (0.4f + t)).coerceAtLeast(0.02f)

        val x = r * cos(angle)
        val y = r * sin(angle)
        val z = gaussian(rng) * DISC_THICKNESS * (1f - 0.45f * t)

        // Young blue stars dominate the arms; a warm minority is scattered through.
        val warmth = if (rng.nextFloat() < 0.24f) 0.55f + rng.nextFloat() * 0.45f else rng.nextFloat() * 0.28f
        val brightness = 0.55f + rng.nextFloat() * 0.45f
        val colour = mixTemperature(warmth, brightness)

        // Power law: a few genuinely bright stars, a long tail of faint ones.
        val size = 0.55f + rng.nextFloat().pow(4.2f) * 4.6f

        write(out, x, y, z, colour, size, r)
    }

    private fun writeCoreStar(out: FloatBuffer, rng: Random) {
        // Cube root of a uniform gives a roughly constant volume density in the bulge.
        val r = INNER_RADIUS * 2.2f * rng.nextFloat().pow(0.42f)
        val angle = rng.nextFloat() * (2.0 * PI).toFloat()
        val x = r * cos(angle)
        val y = r * sin(angle)
        val z = gaussian(rng) * DISC_THICKNESS * 2.4f

        val warmth = 0.25f + rng.nextFloat() * 0.4f
        val colour = mixTemperature(warmth, 0.8f + rng.nextFloat() * 0.2f)
        val size = 0.7f + rng.nextFloat().pow(3.0f) * 3.4f

        write(out, x, y, z, colour, size, r)
    }

    private fun writeHaloStar(out: FloatBuffer, rng: Random) {
        val r = OUTER_RADIUS * (0.35f + rng.nextFloat() * 1.5f)
        val angle = rng.nextFloat() * (2.0 * PI).toFloat()
        val incline = gaussian(rng) * 0.5f
        val x = r * cos(angle)
        val y = r * sin(angle)
        val z = r * incline * 0.35f

        val colour = mixTemperature(rng.nextFloat() * 0.5f, 0.35f + rng.nextFloat() * 0.4f)
        val size = 0.4f + rng.nextFloat().pow(5f) * 2.4f

        write(out, x, y, z, colour, size, r)
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Blends between the cool and warm star colours, then pulls a little toward neutral
     * white as brightness rises so the brightest cores read as white rather than tinted.
     */
    private fun mixTemperature(warmth: Float, brightness: Float): FloatArray {
        val w = warmth.coerceIn(0f, 1f)
        val whiteness = (brightness - 0.6f).coerceAtLeast(0f) * 0.8f
        return FloatArray(3) { i ->
            val base = COOL[i] * (1f - w) + WARM[i] * w
            (base * (1f - whiteness) + NEUTRAL[i] * whiteness) * brightness
        }
    }

    /** Box-Muller, clamped so a rare outlier cannot fling a star across the frame. */
    private fun gaussian(rng: Random): Float {
        var u1 = rng.nextFloat()
        if (u1 < 1e-6f) u1 = 1e-6f
        val u2 = rng.nextFloat()
        val mag = sqrt(-2f * ln(u1))
        return (mag * cos((2.0 * PI * u2).toFloat())).coerceIn(-2.6f, 2.6f)
    }

    private fun write(
        out: FloatBuffer,
        x: Float,
        y: Float,
        z: Float,
        colour: FloatArray,
        size: Float,
        radius: Float
    ) {
        out.put(x); out.put(y); out.put(z)
        out.put(colour[0]); out.put(colour[1]); out.put(colour[2])
        out.put(size); out.put(radius)
    }
}
