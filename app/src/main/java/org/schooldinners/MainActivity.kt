package org.schooldinners

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SchoolDinnersTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AssetImage(
                            assetName = "SchoolNomNoms.png",
                            contentDescription = "School Nom Noms logo"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssetImage(
    assetName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageBitmap = remember(assetName) {
        context.assets.open(assetName).use { input ->
            BitmapFactory.decodeStream(input).asImageBitmap()
        }
    }

    Image(
        bitmap = imageBitmap,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun SchoolDinnersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        typography = MaterialTheme.typography,
        content = content
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AssetImagePreview() {
    SchoolDinnersTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AssetImage(
                assetName = "SchoolNomNoms.png",
                contentDescription = "School Nom Noms logo"
            )
        }
    }
}
