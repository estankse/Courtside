package cn.courtside.client

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlin.random.Random

private data class MatchCardPhoto(val image: Int, val focus: Alignment)

private val matchCardPhotos =
    listOf(
        MatchCardPhoto(R.drawable.referee_card_lin_return, Alignment.Center),
        MatchCardPhoto(R.drawable.referee_card_lee_airborne, Alignment.BottomCenter),
        MatchCardPhoto(R.drawable.referee_card_ginting, Alignment.TopCenter),
        MatchCardPhoto(R.drawable.referee_card_momota, Alignment.TopCenter),
        MatchCardPhoto(R.drawable.referee_card_intanon, Alignment.TopCenter),
        MatchCardPhoto(R.drawable.referee_card_shuttle, Alignment.BottomCenter),
        MatchCardPhoto(R.drawable.referee_card_doubles, Alignment.BottomCenter),
        MatchCardPhoto(R.drawable.referee_card_nishimoto, Alignment.TopCenter),
    )

@Composable
internal fun RefereeMatchCardArt(matchId: String, modifier: Modifier = Modifier) {
    // A match keeps its random draw across tabs, filtering, refresh, rotation and app restarts.
    val photo =
        remember(matchId) {
            matchCardPhotos[Random(matchId.hashCode()).nextInt(matchCardPhotos.size)]
        }
    Box(modifier) {
        Image(
            painterResource(photo.image),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alignment = photo.focus,
            alpha = .30f,
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to PanelColor.copy(alpha = .48f),
                        .48f to PanelColor.copy(alpha = .20f),
                        1f to PanelColor.copy(alpha = .48f),
                    )
                )
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to PanelColor.copy(alpha = .42f),
                        .42f to PanelColor.copy(alpha = .20f),
                        .72f to PanelColor.copy(alpha = .65f),
                        1f to PanelColor.copy(alpha = .96f),
                    )
                )
        )
    }
}
