package cn.courtside.client

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.json.JSONArray

private data class PhotoCredit(
    val title: String,
    val usage: String,
    val author: String,
    val license: String,
    val sourceUrl: String,
    val licenseUrl: String,
)

/** Keep the attribution with the offline photos, rather than depending on a remote credits page. */
@Composable
internal fun RefereePhotoCreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val credits =
        remember(context) {
            val json =
                context.assets.open("referee_photo_credits.json").bufferedReader().use {
                    JSONArray(it.readText())
                }
            List(json.length()) { index ->
                val item = json.getJSONObject(index)
                PhotoCredit(
                    item.getString("title"),
                    item.getString("usage"),
                    item.getString("author"),
                    item.getString("license"),
                    item.getString("sourceUrl"),
                    item.getString("licenseUrl"),
                )
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("图片来源与授权") },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "比赛摄影来自 Wikimedia Commons。以下列出作者、原图与授权；图片仅作裁剪取景、等比缩小及 WebP 压缩，界面叠加渐变。",
                        color = Soft,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }
                items(credits, key = { it.sourceUrl }) { photo ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            photo.usage,
                            color = Paper,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(photo.title, color = Soft, fontSize = 10.sp, lineHeight = 15.sp)
                        Text(
                            "${photo.author} · ${photo.license}",
                            color = Soft,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                        )
                        Row {
                            TextButton(onClick = { uri.openUri(photo.sourceUrl) }) {
                                Text("原图与作者", fontSize = 11.sp)
                            }
                            TextButton(onClick = { uri.openUri(photo.licenseUrl) }) {
                                Text(photo.license, fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider(color = Edge)
                    }
                }
                item {
                    Text(
                        "登录与计分页沿用项目原有的三张球星摄影，由用户提供。新增照片的裁剪与压缩版本沿用各自的授权协议。",
                        color = Soft,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
