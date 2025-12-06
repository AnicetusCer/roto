package org.roto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.roto.data.ThemeMode
import org.roto.data.ThemeOption
import org.roto.ui.MenuRoot
import org.roto.ui.MenuViewModel
import org.roto.ui.ThemeUiState
import org.roto.ui.ThemeViewModel

class MainActivity : ComponentActivity() {

    private val menuViewModel: MenuViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeState by themeViewModel.themeState.collectAsStateWithLifecycle()
            val themeOption = themeState.option
            val themeMode = themeState.mode
            val applySystemPadding = themeState.applySystemPadding
            RotoTheme(themeOption, themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MenuRoot(
                        viewModel = menuViewModel,
                        themeOption = themeOption,
                        themeMode = themeMode,
                        onThemeChange = themeViewModel::setTheme,
                        onThemeModeChange = themeViewModel::setMode,
                        applySystemPadding = applySystemPadding,
                        onSystemPaddingChange = themeViewModel::setApplySystemPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .let { base -> if (applySystemPadding) base.systemBarsPadding() else base }
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

private val RotoLightColorScheme = lightColorScheme(
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
fun RotoTheme(
    themeOption: ThemeOption,
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = colorSchemeFor(themeOption, themeMode, systemDark)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

private fun colorSchemeFor(option: ThemeOption, mode: ThemeMode, systemDark: Boolean): androidx.compose.material3.ColorScheme {
    val isDark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    return when (option) {
        ThemeOption.SYSTEM -> if (isDark) RotoDarkColorScheme else RotoLightColorScheme
        ThemeOption.LIGHT -> RotoLightColorScheme
        ThemeOption.DARK -> RotoDarkColorScheme
        ThemeOption.FOREST -> if (isDark) ForestDarkColorScheme else ForestLightColorScheme
        ThemeOption.SUNSET -> if (isDark) SunsetDarkColorScheme else SunsetLightColorScheme
        ThemeOption.OCEAN -> if (isDark) OceanDarkColorScheme else OceanLightColorScheme
        ThemeOption.BLOSSOM -> if (isDark) BlossomDarkColorScheme else BlossomLightColorScheme
        ThemeOption.MIDNIGHT -> if (isDark) MidnightDarkColorScheme else MidnightLightColorScheme
        ThemeOption.SAND -> if (isDark) SandDarkColorScheme else SandLightColorScheme
    }
}

private val RotoDarkColorScheme = darkColorScheme(
    primary = Color(0xFF66E6C7),
    onPrimary = Color(0xFF00241C),
    secondary = Color(0xFF7BA3A8),
    onSecondary = Color(0xFF0C1B1D),
    tertiary = Color(0xFF84C5E3),
    onTertiary = Color(0xFF00141E),
    background = Color(0xFF0E1A1D),
    onBackground = Color(0xFFE3F0EE),
    surface = Color(0xFF152629),
    onSurface = Color(0xFFE3F0EE),
    surfaceVariant = Color(0xFF234244),
    onSurfaceVariant = Color(0xFFC2D7D6),
    outline = Color(0xFF4B6A6E)
)

private val ForestLightColorScheme = lightColorScheme(
    primary = Color(0xFF2F7D4B),
    onPrimary = Color.White,
    secondary = Color(0xFF3E5F46),
    onSecondary = Color.White,
    tertiary = Color(0xFF6B8F71),
    onTertiary = Color.White,
    background = Color(0xFFF1F6F2),
    onBackground = Color(0xFF1E3326),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E3326),
    surfaceVariant = Color(0xFFDCE7DD),
    onSurfaceVariant = Color(0xFF2E4A35),
    outline = Color(0xFF55735A)
)

private val ForestDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7FD1A2),
    onPrimary = Color(0xFF00321C),
    secondary = Color(0xFF9BC5A6),
    onSecondary = Color(0xFF082014),
    tertiary = Color(0xFFA3D6B8),
    onTertiary = Color(0xFF082014),
    background = Color(0xFF0E1912),
    onBackground = Color(0xFFE2F0E6),
    surface = Color(0xFF16261C),
    onSurface = Color(0xFFE2F0E6),
    surfaceVariant = Color(0xFF2B4733),
    onSurfaceVariant = Color(0xFFCBE3D2),
    outline = Color(0xFF5E7D69)
)

private val SunsetLightColorScheme = lightColorScheme(
    primary = Color(0xFFCE4D53),
    onPrimary = Color.White,
    secondary = Color(0xFFEB8A3D),
    onSecondary = Color(0xFF3B1D00),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color.White,
    background = Color(0xFFFFF6F2),
    onBackground = Color(0xFF2E1B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2E1B16),
    surfaceVariant = Color(0xFFF2DFD7),
    onSurfaceVariant = Color(0xFF5A3E35),
    outline = Color(0xFF8A675D)
)

private val SunsetDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFA6A9),
    onPrimary = Color(0xFF3D0209),
    secondary = Color(0xFFFFC278),
    onSecondary = Color(0xFF3C1B00),
    tertiary = Color(0xFFC8B0FF),
    onTertiary = Color(0xFF1D0B4B),
    background = Color(0xFF1B1210),
    onBackground = Color(0xFFF6E8E3),
    surface = Color(0xFF231816),
    onSurface = Color(0xFFF6E8E3),
    surfaceVariant = Color(0xFF3C2B27),
    onSurfaceVariant = Color(0xFFE3CFC8),
    outline = Color(0xFF8E6D64)
)

private val OceanLightColorScheme = lightColorScheme(
    primary = Color(0xFF0E88C8),
    onPrimary = Color.White,
    secondary = Color(0xFF106F8C),
    onSecondary = Color.White,
    tertiary = Color(0xFF42B0FF),
    onTertiary = Color(0xFF002537),
    background = Color(0xFFF2F8FC),
    onBackground = Color(0xFF0D2430),
    surface = Color.White,
    onSurface = Color(0xFF0D2430),
    surfaceVariant = Color(0xFFD7E8F3),
    onSurfaceVariant = Color(0xFF274556),
    outline = Color(0xFF5F8297)
)

private val OceanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6BCBFF),
    onPrimary = Color(0xFF00263A),
    secondary = Color(0xFF82D3F5),
    onSecondary = Color(0xFF062433),
    tertiary = Color(0xFF63E2FF),
    onTertiary = Color(0xFF00252E),
    background = Color(0xFF0A1620),
    onBackground = Color(0xFFE3EFF7),
    surface = Color(0xFF111F2A),
    onSurface = Color(0xFFE3EFF7),
    surfaceVariant = Color(0xFF1E3342),
    onSurfaceVariant = Color(0xFFC7D8E4),
    outline = Color(0xFF5F8297)
)

private val BlossomLightColorScheme = lightColorScheme(
    primary = Color(0xFFD65C7A),
    onPrimary = Color.White,
    secondary = Color(0xFFEA9BB1),
    onSecondary = Color(0xFF3C0F1F),
    tertiary = Color(0xFF8C6BD1),
    onTertiary = Color.White,
    background = Color(0xFFFDF6F8),
    onBackground = Color(0xFF2F1921),
    surface = Color.White,
    onSurface = Color(0xFF2F1921),
    surfaceVariant = Color(0xFFEED9E1),
    onSurfaceVariant = Color(0xFF503741),
    outline = Color(0xFF9C7A8A)
)

private val BlossomDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF29CB7),
    onPrimary = Color(0xFF430C24),
    secondary = Color(0xFFF3C6D7),
    onSecondary = Color(0xFF3B1121),
    tertiary = Color(0xFFCEB5FF),
    onTertiary = Color(0xFF1D0D3B),
    background = Color(0xFF1A1116),
    onBackground = Color(0xFFF4E8ED),
    surface = Color(0xFF21171D),
    onSurface = Color(0xFFF4E8ED),
    surfaceVariant = Color(0xFF3A2A32),
    onSurfaceVariant = Color(0xFFE2CED7),
    outline = Color(0xFF9C7A8A)
)

private val MidnightLightColorScheme = lightColorScheme(
    primary = Color(0xFF3A4E8C),
    onPrimary = Color.White,
    secondary = Color(0xFF556992),
    onSecondary = Color.White,
    tertiary = Color(0xFF7E8DC1),
    onTertiary = Color(0xFF0D132B),
    background = Color(0xFFF3F4FB),
    onBackground = Color(0xFF111424),
    surface = Color.White,
    onSurface = Color(0xFF111424),
    surfaceVariant = Color(0xFFDDE0F1),
    onSurfaceVariant = Color(0xFF2C304A),
    outline = Color(0xFF6F7492)
)

private val MidnightDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FA4FF),
    onPrimary = Color(0xFF0A1028),
    secondary = Color(0xFFA8B6E6),
    onSecondary = Color(0xFF0D132B),
    tertiary = Color(0xFF93B5FF),
    onTertiary = Color(0xFF0A162F),
    background = Color(0xFF0A0D18),
    onBackground = Color(0xFFE6EAFF),
    surface = Color(0xFF111627),
    onSurface = Color(0xFFE6EAFF),
    surfaceVariant = Color(0xFF1F2539),
    onSurfaceVariant = Color(0xFFC7CCE5),
    outline = Color(0xFF6F7492)
)

private val SandLightColorScheme = lightColorScheme(
    primary = Color(0xFFC48A3A),
    onPrimary = Color.White,
    secondary = Color(0xFF8B6A3C),
    onSecondary = Color.White,
    tertiary = Color(0xFF9B8B6B),
    onTertiary = Color.White,
    background = Color(0xFFFBF5EB),
    onBackground = Color(0xFF2A1E10),
    surface = Color.White,
    onSurface = Color(0xFF2A1E10),
    surfaceVariant = Color(0xFFE9DDCD),
    onSurfaceVariant = Color(0xFF4F412F),
    outline = Color(0xFF8B7A65)
)

private val SandDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF0C27D),
    onPrimary = Color(0xFF3A2700),
    secondary = Color(0xFFD6B07A),
    onSecondary = Color(0xFF2D1F06),
    tertiary = Color(0xFFC7B190),
    onTertiary = Color(0xFF2B1F0A),
    background = Color(0xFF171008),
    onBackground = Color(0xFFEFE2CF),
    surface = Color(0xFF1F160C),
    onSurface = Color(0xFFEFE2CF),
    surfaceVariant = Color(0xFF3A2D1F),
    onSurfaceVariant = Color(0xFFE0CDB4),
    outline = Color(0xFF8B7A65)
)
