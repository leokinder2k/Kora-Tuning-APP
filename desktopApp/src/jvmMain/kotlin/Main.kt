import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.leokinder2k.koratuningcompanion.navigation.KoraAuthorityApp
import com.leokinder2k.koratuningcompanion.ui.theme.KoraTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kora Tuning Companion"
    ) {
        var themeMode by remember { mutableStateOf("SYSTEM") }

        KoraTheme(themeMode = themeMode) {
            KoraAuthorityApp(
                themeMode = themeMode,
                onThemeModeChange = { themeMode = it }
            )
        }
    }
}
