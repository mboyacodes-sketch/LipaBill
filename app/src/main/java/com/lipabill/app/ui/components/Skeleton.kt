package com.lipabill.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Space

/** Soft shimmer used by loading placeholders across the app. */
@Composable
fun rememberSkeletonBrush(): Brush {
    val base = Hairline.copy(alpha = 0.55f)
    val highlight = CardWhite.copy(alpha = 0.95f)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonShift"
    )
    val start = Offset(x = shift * 900f - 200f, y = 0f)
    val end = Offset(x = start.x + 280f, y = 180f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = start,
        end = end
    )
}

@Composable
fun SkeletonBone(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    brush: Brush = rememberSkeletonBrush()
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun SkeletonCircle(
    size: Dp,
    modifier: Modifier = Modifier,
    brush: Brush = rememberSkeletonBrush()
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(brush)
    )
}

/**
 * Full-screen loading shell. [content] draws the page-shaped bones;
 * chrome (status padding / canvas) stays consistent across destinations.
 */
@Composable
fun SkeletonScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(Brush) -> Unit
) {
    val brush = rememberSkeletonBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.page, vertical = Space.pageV),
        content = { content(brush) }
    )
}

@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier = modifier) { brush ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBone(
                modifier = Modifier
                    .width(120.dp)
                    .height(28.dp),
                brush = brush
            )
            Spacer(modifier = Modifier.weight(1f))
            SkeletonCircle(size = 36.dp, brush = brush)
            Spacer(modifier = Modifier.width(10.dp))
            SkeletonCircle(size = 36.dp, brush = brush)
        }
        Spacer(modifier = Modifier.height(Space.section))
        SkeletonBone(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            shape = RoundedCornerShape(28.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.block))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(4) {
                SkeletonBone(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = RoundedCornerShape(18.dp),
                    brush = brush
                )
            }
        }
        Spacer(modifier = Modifier.height(Space.section))
        SkeletonBone(
            modifier = Modifier
                .width(140.dp)
                .height(18.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.block))
        repeat(6) {
            SkeletonListRow(brush = brush)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun InsightsSkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier = modifier) { brush ->
        SkeletonBone(
            modifier = Modifier
                .width(100.dp)
                .height(24.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.block))
        SkeletonBone(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = RoundedCornerShape(22.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.block))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                SkeletonBone(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp),
                    shape = RoundedCornerShape(16.dp),
                    brush = brush
                )
            }
        }
        Spacer(modifier = Modifier.height(Space.section))
        repeat(3) {
            SkeletonBone(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(20.dp),
                brush = brush
            )
            Spacer(modifier = Modifier.height(Space.block))
        }
    }
}

@Composable
fun PassesSkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier = modifier) { brush ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonCircle(size = 40.dp, brush = brush)
            Spacer(modifier = Modifier.width(12.dp))
            SkeletonBone(
                modifier = Modifier
                    .width(110.dp)
                    .height(22.dp),
                brush = brush
            )
            Spacer(modifier = Modifier.weight(1f))
            PillBone(width = 72.dp, brush = brush)
        }
        Spacer(modifier = Modifier.height(Space.section))
        SkeletonBone(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(24.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.block))
        repeat(3) {
            SkeletonBone(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                shape = RoundedCornerShape(18.dp),
                brush = brush
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun HabitsSkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier = modifier) { brush ->
        SkeletonBone(
            modifier = Modifier
                .width(120.dp)
                .height(24.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.block))
        SkeletonBone(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            shape = RoundedCornerShape(22.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.section))
        repeat(4) {
            SkeletonListRow(brush = brush, avatar = true)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier = modifier) { brush ->
        SkeletonBone(
            modifier = Modifier
                .width(160.dp)
                .height(22.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.section))
        SkeletonBone(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(22.dp),
            brush = brush
        )
        Spacer(modifier = Modifier.height(Space.block))
        repeat(4) {
            SkeletonBone(
                modifier = Modifier
                    .fillMaxWidth(if (it % 2 == 0) 1f else 0.72f)
                    .height(16.dp),
                brush = brush
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Spacer(modifier = Modifier.height(Space.section))
        SkeletonBone(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            brush = brush
        )
    }
}

@Composable
private fun SkeletonListRow(
    brush: Brush,
    avatar: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatar) {
            SkeletonCircle(size = 44.dp, brush = brush)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBone(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp),
                brush = brush
            )
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBone(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(12.dp),
                brush = brush
            )
        }
        SkeletonBone(
            modifier = Modifier
                .width(64.dp)
                .height(14.dp),
            brush = brush
        )
    }
}

/** Rounded pill placeholder (chips, tabs, small buttons). */
@Composable
private fun PillBone(width: Dp, brush: Brush) {
    SkeletonBone(
        modifier = Modifier
            .width(width)
            .height(36.dp),
        shape = RoundedCornerShape(18.dp),
        brush = brush
    )
}
