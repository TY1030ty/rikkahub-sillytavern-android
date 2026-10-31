package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.io.File
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.toComposeColor

/**
 * 判断一个背景图地址是否还有可能画出来。
 *
 * 以前只要字符串非空就当成「有图」，于是一张随备份导进来的、指向旧包名私有目录的
 * file:// 会一直占据第一优先级：图读不到 → 什么都不显示，而渐变和角色卡背景排在它
 * 后面，永远轮不到。这里对 file:// 做一次存在性检查，读不到就当没设置。
 */
internal fun isLoadableImagePath(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    // http(s) / content:// 之类交给 Coil 自己判断，不在这里拦
    if (!path.startsWith("file:", ignoreCase = true)) return true
    val filePath = runCatching { Uri.parse(path).path }.getOrNull() ?: return false
    return File(filePath).isFile
}

/**
 * 聊天页背景，优先级：全局聊天背景图 → 角色卡渐变背景 → 角色卡背景图。
 * 无效的地址会被跳过，不再挡住后面的层。
 */
@Composable
fun AssistantBackground(setting: Settings, assistant: Assistant, modifier: Modifier) {
    val globalBackground = setting.displaySetting.chatBackgroundImagePath
    // 全局聊天背景图（酒馆主题导入或外观自定义页设置），优先于角色卡背景
    if (isLoadableImagePath(globalBackground)) {
        val scrimColor = setting.displaySetting.chatBackgroundColor?.toComposeColor()
            ?: MaterialTheme.colorScheme.background
        Box(modifier = modifier) {
            AsyncImage(
                model = globalBackground,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 聊天背景色作为遮罩，模拟酒馆 chat_tint 叠层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = 0.45f))
            )
        }
        return
    }
    if (assistant.useGradientBackground) {
        MeshGradientBackground(modifier = modifier)
        return
    }
    val background = assistant.background
    if (isLoadableImagePath(background) && background != null) {
        val backgroundColor = MaterialTheme.colorScheme.background
        val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
        Box(modifier = modifier) {
            AsyncImage(
                model = background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backgroundOpacity)
            )

            // 全屏渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.2f),
                                backgroundColor.copy(alpha = 0.5f)
                            )
                        )
                    )
            )
        }
    }
}
