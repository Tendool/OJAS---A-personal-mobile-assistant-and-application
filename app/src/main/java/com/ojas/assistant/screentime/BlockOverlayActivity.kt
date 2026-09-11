package com.ojas.assistant.screentime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ojas.assistant.MainActivity
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.ui.galaxy.GalaxyBackground
import com.ojas.assistant.ui.theme.AuroraTeal
import com.ojas.assistant.ui.theme.OjasTheme
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint

/**
 * Shown in front of an app that has spent its daily budget, or any limited app during a
 * focus session. It offers no bypass on purpose: the way past it is the home button, and
 * that small deliberate friction is the whole mechanism.
 */
class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "That app" }
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: REASON_LIMIT
        val used = intent?.getIntExtra(EXTRA_USED, 0) ?: 0
        val cap = intent?.getIntExtra(EXTRA_CAP, 0) ?: 0
        val until = intent?.getLongExtra(EXTRA_UNTIL, 0L) ?: 0L

        setContent {
            OjasTheme {
                BlockSurface(
                    label = label,
                    headline = if (reason == REASON_FOCUS) "Focus is running" else "Budget spent",
                    body = when {
                        reason == REASON_FOCUS && until > TimeUtils.now() ->
                            "$label is on hold until your focus session ends " +
                                TimeUtils.relative(until) + "."
                        reason == REASON_FOCUS -> "$label is on hold while you focus."
                        cap > 0 -> "$label has used $used of its $cap minutes today."
                        else -> "$label is over the limit you set."
                    },
                    onHome = { goHome() },
                    onOpenOjas = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(MainActivity.EXTRA_ROUTE, "screentime")
                        )
                        finish()
                    }
                )
            }
        }
    }

    /** Back would drop the user straight back into the blocked app. */
    override fun onBackPressed() = goHome()

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_LABEL = "ojas.block.label"
        const val EXTRA_REASON = "ojas.block.reason"
        const val EXTRA_USED = "ojas.block.used"
        const val EXTRA_CAP = "ojas.block.cap"
        const val EXTRA_UNTIL = "ojas.block.until"

        const val REASON_LIMIT = "limit"
        const val REASON_FOCUS = "focus"
    }
}

@Composable
private fun BlockSurface(
    label: String,
    headline: String,
    body: String,
    onHome: () -> Unit,
    onOpenOjas: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        GalaxyBackground(
            modifier = Modifier.fillMaxSize(),
            starCount = 3000,
            reduceMotion = false,
            interactive = false
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                headline,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = StarlightDim,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BlockAction("Home", AuroraTeal, Modifier.weight(1f), onHome)
                BlockAction("Open Ojas", StarlightDim, Modifier.weight(1f), onOpenOjas)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "You set this limit for $label yourself.",
                style = MaterialTheme.typography.labelSmall,
                color = StarlightFaint,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BlockAction(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = accent)
    }
}
