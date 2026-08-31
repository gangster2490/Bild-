package de.spardirekt.agents.pro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spardirekt.agents.pro.ui.theme.GradientAccent
import de.spardirekt.agents.pro.ui.theme.GradientTitle
import de.spardirekt.agents.pro.ui.theme.LocalVppType
import de.spardirekt.agents.pro.ui.theme.VppColors
import de.spardirekt.agents.pro.ui.theme.VppShapes

@Composable
fun AppHeader(
    onNewProject: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val type = LocalVppType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GradientAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Veo Prompt Pro",
                style = type.cardTitle.copy(color = VppColors.textDark, fontWeight = FontWeight.Bold)
            )
            Text(
                "VEO 3.1 Product Ads",
                style = type.secondary.copy(color = VppColors.textMutedDark, fontSize = 12.sp)
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            if (onNewProject != null) {
                Text(
                    "Новый проект",
                    style = type.badge.copy(color = VppColors.accentPurple, fontSize = 12.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNewProject() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            if (onHistory != null) {
                HeaderSquareButton(onClick = onHistory) {
                    Icon(Icons.Filled.History, null, tint = VppColors.textLight, modifier = Modifier.size(18.dp))
                }
            }
            if (onMenu != null) {
                Spacer(Modifier.width(8.dp))
                HeaderSquareButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, null, tint = VppColors.textLight, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun HeaderSquareButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(VppColors.cardNavy)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun GradientHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LocalVppType.current.screenTitle.copy(
            brush = GradientTitle,
            fontWeight = FontWeight.Bold
        ),
        modifier = modifier
    )
}

@Composable
fun NavyCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, VppShapes.cardShape, clip = false)
            .clip(VppShapes.cardShape)
            .background(VppColors.cardNavy)
            .padding(22.dp),
        content = content
    )
}

@Composable
fun LightOutlineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(VppShapes.cardShape)
            .border(1.5.dp, VppColors.outlineSoft, VppShapes.cardShape)
            .background(Color.White.copy(alpha = 0.55f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(scale)
            .shadow(if (enabled) 12.dp else 0.dp, VppShapes.buttonShape, clip = false)
            .clip(VppShapes.buttonShape)
            .background(
                if (enabled) GradientAccent
                else Brush.horizontalGradient(listOf(Color(0xFF9A9AB0), Color(0xFF8A8AA0)))
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = LocalVppType.current.buttonLabel.copy(color = Color.White)
        )
    }
}

@Composable
fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(VppShapes.buttonShape)
            .border(1.5.dp, VppColors.accentPurple.copy(alpha = 0.55f), VppShapes.buttonShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = LocalVppType.current.buttonLabel.copy(
                color = VppColors.textLight,
                fontSize = 14.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VppColors.cardInset)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .then(
                        if (isSelected) Modifier.background(GradientAccent)
                        else Modifier.background(Color.Transparent)
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    style = LocalVppType.current.badge.copy(
                        color = if (isSelected) Color.White else VppColors.textMuted,
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Use Flow conceptually via wrapping in parent LazyRow typically;
        // here keep single-line horizontal scroll friendly chips in caller.
    }
}

@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(VppShapes.chipShape)
            .then(
                if (selected) Modifier.background(GradientAccent)
                else Modifier.background(VppColors.cardInset)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = LocalVppType.current.badge.copy(
                color = if (selected) Color.White else VppColors.textMuted,
                fontSize = 12.sp
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun StatusPill(text: String, success: Boolean) {
    Box(
        modifier = Modifier
            .clip(VppShapes.chipShape)
            .background(if (success) Color(0xFF1E3B32) else Color(0xFF3A2430))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = LocalVppType.current.badge.copy(
                color = if (success) VppColors.success else VppColors.error
            )
        )
    }
}

@Composable
fun ProgressRail(
    stages: List<String>,
    activeIndex: Int,
    completedThrough: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        stages.forEachIndexed { index, label ->
            val done = index <= completedThrough
            val active = index == activeIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        done && !active -> {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(VppColors.success),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        active -> {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(VppColors.accentPurple)
                            )
                        }
                        else -> {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(VppColors.textMuted.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = LocalVppType.current.body.copy(
                        color = when {
                            active -> VppColors.textLight
                            done -> VppColors.textLight.copy(alpha = 0.85f)
                            else -> VppColors.textMuted
                        },
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }
    }
}
