package org.roto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.roto.ui.MenuRoot
import org.roto.ui.MenuViewModel

class MainActivity : ComponentActivity() {

    private val menuViewModel: MenuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RotoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MenuRoot(
                        viewModel = menuViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        menuViewModel.onAppForeground()
    }
}

private val RotoColorScheme = lightColorScheme(
    primary = Color(0xFF00BF93),
    onPrimary = Color(0xFF00382B),
    secondary = Color(0xFF3D595D),
    onSecondary = Color.White,
    tertiary = Color(0xFF253E45),
    onTertiary = Color.White,
    background = Color(0xFFF4FBFA),
    onBackground = Color(0xFF132327),
    surface = Color.White,
    onSurface = Color(0xFF132327),
    surfaceVariant = Color(0xFFD7E8E4),
    onSurfaceVariant = Color(0xFF2B4548),
    outline = Color(0xFF4F6A6D)
)

@Composable
fun RotoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RotoColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
