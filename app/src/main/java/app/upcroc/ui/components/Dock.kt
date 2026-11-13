package app.upcroc.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.Upcroc
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

private data class DockItem(val icon: UiIcon, val label: String)

private val dockItems = listOf(
    DockItem(UiIcon.Radar, "Alerts"),
    DockItem(UiIcon.Search, "Search"),
    DockItem(UiIcon.Settings, "Settings"),
)

/**
 * Floating blue glass dock. The frost comes from a real backdrop blur; the blue is a
 * fixed translucent colour painted on the bar itself, so it never tints the content
 * passing underneath.
 */
@Composable
fun FloatingDock(
    current: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val shape = remember { RoundedCornerShape(29.dp) }
    val style = remember {
        HazeBlurStyle {
            blurEnabled(true)
            blurRadius(26.dp)
            noiseFactor(0.02f)
            alpha(1f)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .height(58.dp),
    ) {
        // Soft lift, behind the glass so the effect layer is never clipped by the shadow.
        Box(
            Modifier
                .matchParentSize()
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = style,
                    performanceMode = HazePerformanceMode.Quality,
                )
                .clip(shape)
                // Fixed blue bar colour; translucent so the blur reads through it.
                .background(Upcroc.Blue.copy(alpha = 0.75f))
                // Hairline specular edge.
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape),
        ) {
            Row(
                Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dockItems.forEachIndexed { index, item ->
                    val active = index == current
                    // Active item swells and sits in a frosted capsule; idle items stay
                    // visible as dimmed white so both states read clearly on the blue.
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.18f else 0.94f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "dockIconScale",
                    )
                    val pillAlpha by animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = tween(durationMillis = 180),
                        label = "dockPillAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { contentDescription = item.label }
                            .pressable(onClick = { onSelect(index) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 46.dp, height = 34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.14f * pillAlpha)),
                        )
                        IconGlyph(
                            icon = item.icon,
                            modifier = Modifier.size(22.dp).scale(iconScale),
                            tint = if (active) Color.White else Color.White.copy(alpha = 0.62f),
                            strokeWidth = if (active) 1.9.dp else 1.7.dp,
                        )
                    }
                }
            }
        }
    }
}
