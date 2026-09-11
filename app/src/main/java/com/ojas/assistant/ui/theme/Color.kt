package com.ojas.assistant.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette is sampled straight from the reference render: a near-black navy void,
 * star cores in pure white, and the two star temperatures that give a spiral galaxy its
 * character — cool blue-white young stars in the arms, warm amber older stars.
 */

// Void / structure
val SpaceVoid = Color(0xFF04060D)
val SpaceDeep = Color(0xFF070B15)
val SpaceSurface = Color(0xFF0C1220)
val SpaceSurfaceHigh = Color(0xFF121A2C)
val SpaceOutline = Color(0xFF223052)
val SpaceOutlineSoft = Color(0x33A9C4FF)

// Light
val Starlight = Color(0xFFE9EFFF)
val StarlightDim = Color(0xFF9FB0D4)
val StarlightFaint = Color(0xFF64749A)

// Accents, matching the star temperatures in the galaxy render
val NebulaBlue = Color(0xFF8FB8FF)
val NebulaBlueDeep = Color(0xFF4C7DE0)
val EmberOrange = Color(0xFFFFB27A)
val SolarAmber = Color(0xFFFFD08A)
val VioletDrift = Color(0xFFC0AEFF)
val AuroraTeal = Color(0xFF7FE3D4)
val PulsarRose = Color(0xFFFF8FA8)

/** Accent keys stored on workouts and calendar events. */
val accentByKey: Map<String, Color> = mapOf(
    "nebula" to NebulaBlue,
    "ember" to EmberOrange,
    "solar" to SolarAmber,
    "violet" to VioletDrift,
    "aurora" to AuroraTeal,
    "pulsar" to PulsarRose
)

val accentKeys: List<String> = accentByKey.keys.toList()

fun accentFor(key: String?): Color = accentByKey[key] ?: NebulaBlue
