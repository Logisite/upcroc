package app.upcroc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.Upcroc
import dev.chrisbanes.haze.HazeInput
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
 * Floating glass dock. Haze samples the live content behind it, then the
 * #484848 at 66% tint and the 1 px rim finish the glass, matching the mock.
 */
@Composable
fun FloatingDock(
    current: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val style = remember {
        HazeBlurStyle {
            blurRadius(26.dp)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .height(58.dp)
            .shadow(24.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = style)
            .background(Upcroc.NavGlass)
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockItems.forEachIndexed { index, item ->
                val active = index == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .semantics { contentDescription = item.label }
                        .pressable(onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    IconGlyph(
                        icon = item.icon,
                        modifier = Modifier.size(22.dp),
                        tint = if (active) Color.White else Upcroc.NavIdle,
                        strokeWidth = 1.7.dp,
                    )
                }
            }
        }
    }
}
