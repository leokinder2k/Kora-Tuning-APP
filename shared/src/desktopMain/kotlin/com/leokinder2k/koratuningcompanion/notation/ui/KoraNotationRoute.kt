package com.leokinder2k.koratuningcompanion.notation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.platform.openUrl
import org.jetbrains.compose.resources.stringResource
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*

@Composable
actual fun KoraNotationRoute(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(Res.string.notation_desktop_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                stringResource(Res.string.notation_desktop_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { openUrl("http://localhost:3000") },
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Language, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Local Web App")
            }
        }
    }
}
