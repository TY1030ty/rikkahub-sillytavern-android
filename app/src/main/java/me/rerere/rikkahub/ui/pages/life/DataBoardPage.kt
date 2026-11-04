package me.rerere.rikkahub.ui.pages.life

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONObject

/**
 * 数据看板：只读 /sdcard/Download/06_脚本/mirrow/data/daily/ 下按天存的那份记录，
 * 是 daily.py 写出来的东西，这里只是把它摆出来看。
 */
private const val DAILY_DIR = "/sdcard/Download/06_脚本/mirrow/data/daily"

private data class DaySummary(
    val date: String,
    val homeworkDone: Int,
    val homeworkTotal: Int,
    val homeworkItems: List<Pair<String, Boolean>>,
    val weights: List<String>,
    val meals: List<String>,
    val water: Int,
    val missing: List<String>,
)

private fun readDay(f: File): DaySummary? = runCatching {
    val o = JSONObject(f.readText())
    val hw = o.optJSONArray("homework")
    var done = 0
    var total = 0
    val items = mutableListOf<Pair<String, Boolean>>()
    if (hw != null) {
        total = hw.length()
        for (i in 0 until total) {
            val o2 = hw.getJSONObject(i)
            val ok = o2.optBoolean("done")
            if (ok) done++
            items.add(o2.optString("item") to ok)
        }
    }
    val weights = mutableListOf<String>()
    o.optJSONArray("weights")?.let { arr ->
        for (i in 0 until arr.length()) {
            val w = arr.getJSONObject(i)
            weights.add("%s %.1fkg".format(w.optString("when"), w.optDouble("kg")))
        }
    }
    val meals = mutableListOf<String>()
    o.optJSONArray("meals")?.let { arr ->
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            meals.add(m.optString("which") + "：" + m.optString("text"))
        }
    }
    DaySummary(
        date = o.optString("date"),
        homeworkDone = done,
        homeworkTotal = total,
        homeworkItems = items,
        weights = weights,
        meals = meals,
        water = o.optInt("water_ml", 0),
        missing = emptyList(),
    )
}.getOrNull()


private const val EVIDENCE_DIR = "/sdcard/Download/06_脚本/mirrow/data/evidence"

/** 把选中的文件存下来，并把对应功课标成已交 */
private fun submitEvidence(date: String, index: Int, name: String, bytes: ByteArray): Boolean =
    runCatching {
        val dir = File(EVIDENCE_DIR).apply { mkdirs() }
        val ext = name.substringAfterLast('.', "jpg")
        val f = File(dir, "${date}_${index}.${ext}")
        f.writeBytes(bytes)
        val dayFile = File(DAILY_DIR, "$date.json")
        val o = JSONObject(dayFile.readText())
        val hw = o.optJSONArray("homework") ?: return@runCatching false
        val item = hw.getJSONObject(index)
        item.put("done", true)
        item.put("evidence", f.absolutePath)
        item.put("ts", System.currentTimeMillis() / 1000.0)
        dayFile.writeText(o.toString())
        true
    }.getOrDefault(false)

@Composable
fun DataBoardPage() {
    var days by remember { mutableStateOf<List<DaySummary>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        days = runCatching {
            File(DAILY_DIR).listFiles()
                ?.filter { it.name.endsWith(".json") }
                ?.sortedByDescending { it.name }
                ?.mapNotNull { readDay(it) }
                .orEmpty()
        }.getOrElse { emptyList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据看板") },
                navigationIcon = { BackButton() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (days.isEmpty()) {
                item { Text("还没有记录。今天定好功课后，这里就会出现。", modifier = Modifier.padding(top = 24.dp)) }
            }
            items(days, key = { it.date }) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(d.date, style = MaterialTheme.typography.titleMedium)
                        Text("功课 ${d.homeworkDone}/${d.homeworkTotal}", style = MaterialTheme.typography.bodyMedium)
                        val ctx = LocalContext.current
                        d.homeworkItems.forEachIndexed { idx, pair ->
                            val (name, ok) = pair
                            Row {
                                Text(
                                    (if (ok) "✓ " else "○ ") + name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!ok) {
                                    val launcher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.GetContent()
                                    ) { uri ->
                                        uri?.let { u ->
                                            val bytes = runCatching {
                                                ctx.contentResolver.openInputStream(u)?.use { s -> s.readBytes() }
                                            }.getOrNull()
                                            val fileName = u.lastPathSegment ?: "img.jpg"
                                            if (bytes != null && submitEvidence(d.date, idx, fileName, bytes)) {
                                                reload++
                                            }
                                        }
                                    }
                                    TextButton(onClick = { launcher.launch("image/*") }) {
                                        Text("上传", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        if (d.weights.isNotEmpty()) Text("体重：" + d.weights.joinToString("，"))
                        if (d.water > 0) Text("喝水 ${d.water.toInt()} ml")
                        d.meals.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
