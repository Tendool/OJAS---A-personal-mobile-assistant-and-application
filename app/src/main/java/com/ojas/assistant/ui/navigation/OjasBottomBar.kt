package com.ojas.assistant.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ojas.assistant.ui.theme.NebulaBlue
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightFaint

/**
 * A floating console rather than a solid bar, so the galaxy keeps running underneath it.
 * The selected item gets a lit halo; the rest sit back at low contrast.
 */
@Composable
fun OjasBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    val systemBars = WindowInsets.navigationBars.asPaddingValues()

    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 10.dp + systemBars.calculateBottomPadding()
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xE60A101E), Color(0xF2060B16))
                    )
                )
                .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(26.dp))
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomDestinations.forEach { destination ->
                val selected = currentRoute?.startsWith(destination.route) == true
                NavOrb(
                    destination = destination,
                    selected = selected,
                    onClick = {
                        if (!selected) navController.navigateTop(destination.route)
                    }
                )
            }
        }
    }
}

@Composable
private fun NavOrb(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) NebulaBlue else StarlightFaint,
        animationSpec = tween(220),
        label = "nav-tint"
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(240),
        label = "nav-halo"
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(34.dp)
                    .alpha(haloAlpha)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NebulaBlue.copy(alpha = 0.14f))
            )
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
