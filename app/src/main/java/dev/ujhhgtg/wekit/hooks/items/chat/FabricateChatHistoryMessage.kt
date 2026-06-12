package dev.ujhhgtg.wekit.hooks.items.chat

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Person_search
import dev.ujhhgtg.comptime.nameOf
import dev.ujhhgtg.wekit.hooks.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.hooks.api.core.WeMessageApi
import dev.ujhhgtg.wekit.hooks.api.core.models.IWeContact
import dev.ujhhgtg.wekit.hooks.api.core.models.WeContact
import dev.ujhhgtg.wekit.hooks.core.ClickableHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.IconButton
import dev.ujhhgtg.wekit.ui.content.SingleContactSelector
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.copyToClipboard
import dev.ujhhgtg.wekit.utils.android.readTextFromClipboard
import dev.ujhhgtg.wekit.utils.android.showToast
import dev.ujhhgtg.wekit.utils.serialization.XmlJsonParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.random.Random

@HookItem(name = "伪造聊天记录消息", categories = ["聊天"], description = "创建伪造的聊天记录并生成卡片消息 XML")
object FabricateChatHistoryMessage : ClickableHookItem() {

    override val noSwitchWidget = true

    override fun onClick(context: Context) {
        val contacts = WeDatabaseApi.getContacts()

        showComposeDialog(context) {
            ChatRecordXmlGeneratorDialog(
                contacts = contacts,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ChatRecordXmlGeneratorDialog(
    contacts: List<WeContact>,
    onDismiss: () -> Unit
) {
    var outerTitle by remember { mutableStateOf("聊天记录") }
    var outerDesc by remember { mutableStateOf("描述") }

    val rows = remember {
        mutableStateListOf(
            MessageRowState()
        )
    }

    val context = LocalContext.current
    val contactsByWxId = remember(contacts) { contacts.associateBy { it.wxId } }

    val canGenerate by remember(rows, contacts) {
        derivedStateOf {
            rows.isNotEmpty() &&
                    rows.all { it.senderWxId != null && it.senderWxId!!.isNotBlank() && it.text.isNotBlank() } &&
                    contactsByWxId.isNotEmpty()
        }
    }

    AlertDialogContent(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        title = { Text("伪造聊天记录") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = outerTitle,
                    onValueChange = { outerTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = outerDesc,
                    onValueChange = { outerDesc = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("描述") }
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val clipboardText = readTextFromClipboard(context)
                        if (clipboardText.isNullOrBlank()) {
                            showToast(context, "剪贴板为空")
                            return@Button
                        }
                        runCatching {
                            val outerJson = XmlJsonParser.toJsonObject(clipboardText)
                            val appmsg = outerJson["msg"]?.jsonObject?.get("appmsg")?.jsonObject ?: error("未找到 appmsg")
                            val parsedTitle = appmsg["title"]?.jsonPrimitive?.contentOrNull
                            val parsedDesc = appmsg["des"]?.jsonPrimitive?.contentOrNull
                            val recordItemCdata = appmsg["recorditem"]!!.jsonPrimitive.content
                            if (recordItemCdata.isBlank()) {
                                error("recorditem 内容为空")
                            }
                            val innerJson = XmlJsonParser.toJsonObject(recordItemCdata)
                            val recordInfo = innerJson["recordinfo"]?.jsonObject ?: error("未找到 recordinfo")
                            val datalist = recordInfo["datalist"]?.jsonObject ?: error("未找到 datalist")
                            val dataItems: List<JsonObject> = when (val elems = datalist["dataitem"]) {
                                is JsonArray -> elems.map { it.jsonObject }
                                is JsonObject -> listOf(elems)
                                else -> emptyList()
                            }
                            if (dataItems.isEmpty()) {
                                error("未找到消息条目")
                            }
                            val newRows = dataItems.mapNotNull { item ->
                                val text = item["datadesc"]?.jsonPrimitive?.contentOrNull?.trim()
                                    ?: return@mapNotNull null
                                val sourceName = item["sourcename"]?.jsonPrimitive?.contentOrNull
                                    ?: return@mapNotNull null
                                val contact = contacts.firstOrNull { it.nickname == sourceName }
                                MessageRowState(senderWxId = contact?.wxId, text = text)
                            }
                            if (newRows.isEmpty()) {
                                error("未能解析出有效消息")
                            }
                            if (parsedTitle != null) outerTitle = parsedTitle
                            if (parsedDesc != null) outerDesc = parsedDesc
                            rows.clear()
                            rows.addAll(newRows)
                            showToast(context, "已从剪贴板加载 ${newRows.size} 条消息")
                        }.onFailure {
                            showToast(context, "解析失败：${it.message}")
                            WeLogger.e(nameOf(FabricateChatHistoryMessage), "failed to parse messages from clipboard", it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("从剪贴板加载")
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "消息列表",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { rows.add(MessageRowState()) }
                    ) {
                        Icon(MaterialSymbols.Outlined.Add, contentDescription = "Add message")
                    }
                }

                Spacer(Modifier.height(8.dp))

                rows.forEachIndexed { index, row ->
                    MessageRowEditor(
                        row = row,
                        contactsByWxId = contactsByWxId,
                        onPickSender = {
                            showComposeDialog(context) {
                                SingleContactSelector(
                                    title = "选择发送者",
                                    contacts = contacts,
                                    initialSelectedWxId = row.senderWxId,
                                    onDismiss = this.onDismiss,
                                    onConfirm = { wxId ->
                                        row.senderWxId = wxId
                                        this.onDismiss()
                                    }
                                )
                            }
                        },
                        onRemove = {
                            if (rows.size > 1) rows.removeAt(index)
                        }
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Button(
                enabled = canGenerate,
                onClick = {
                    val xml = buildWeChatRecordXml(
                        outerTitle = outerTitle,
                        outerDesc = outerDesc,
                        rows = rows.map { it.toSnapshot() },
                        contacts = contacts
                    )

                    copyToClipboard(context, xml)
                    showToast(context, "已复制")
                }
            ) { Text("复制") }
            Button(
                enabled = canGenerate,
                onClick = {
                    val xml = buildWeChatRecordXml(
                        outerTitle = outerTitle,
                        outerDesc = outerDesc,
                        rows = rows.map { it.toSnapshot() },
                        contacts = contacts
                    )
                    showComposeDialog(context) {
                        SingleContactSelector(
                            title = "选择发送目标",
                            contacts = contacts,
                            initialSelectedWxId = null,
                            onDismiss = this.onDismiss,
                            onConfirm = { wxId ->
                                WeMessageApi.sendXmlAppMsg(wxId, xml)
                                showToast(context, "已发送")
                                this.onDismiss()
                            }
                        )
                    }
                }
            ) { Text("发送") }
        }
    )
}

@Composable
private fun MessageRowEditor(
    row: MessageRowState,
    contactsByWxId: Map<String, IWeContact>,
    onPickSender: () -> Unit,
    onRemove: () -> Unit
) {
    val selectedContact = remember(row.senderWxId, contactsByWxId) {
        row.senderWxId?.let { contactsByWxId[it] }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedContact?.nickname?.takeIf { it.isNotBlank() } ?: "选择发送者 →",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (selectedContact != null) {
                        Text(
                            text = selectedContact.wxId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onPickSender) {
                    Icon(MaterialSymbols.Outlined.Person_search, contentDescription = "Select sender")
                }

                IconButton(
                    onClick = onRemove,
                    enabled = true
                ) {
                    Icon(MaterialSymbols.Outlined.Delete, contentDescription = "Remove message")
                }
            }

            OutlinedTextField(
                value = row.text,
                onValueChange = { row.text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("消息内容") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
            )
        }
    }
}

// ------------------------------------------------------------
// State
// ------------------------------------------------------------

@Stable
private class MessageRowState(
    senderWxId: String? = null,
    text: String = ""
) {
    var senderWxId by mutableStateOf(senderWxId)
    var text by mutableStateOf(text)

    fun toSnapshot() = MessageRowSnapshot(
        senderWxId = senderWxId,
        text = text
    )
}

private data class MessageRowSnapshot(
    val senderWxId: String?,
    val text: String
)

// ------------------------------------------------------------
// XML generation
// ------------------------------------------------------------

private fun buildWeChatRecordXml(
    outerTitle: String,
    outerDesc: String,
    rows: List<MessageRowSnapshot>,
    contacts: List<WeContact>,
    baseTimeMillis: Long = System.currentTimeMillis()
): String {
    val nowSeconds = baseTimeMillis / 1000L
    val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    fun timeStringAt(index: Int): String {
        val t = Date(baseTimeMillis + index * 2000L)
        return timeFormat.format(t).replace(" ", "&#x20;")
    }

    val innerTitle = xmlEscape(outerTitle)
    val innerDesc = xmlEscape(outerDesc)

    return buildString {
        appendLine("<msg>")
        appendLine("    <appmsg>")
        appendLine("        <title>${xmlEscape(outerTitle)}</title>")
        appendLine("        <des>${xmlEscapeNewLines(outerDesc)}</des>")
        appendLine("        <action>view</action>")
        appendLine("        <type>19</type>")
        appendLine("        <url>")
        appendLine("            https://support.weixin.qq.com/cgi-bin/mmsupport-bin/readtemplate?t=page/favorite_record__w_unsupport&amp;from=singlemessage&amp;isappinstalled=0</url>")
        appendLine("        <recorditem><![CDATA[<recordinfo><title>$innerTitle</title><desc>$innerDesc</desc><datalist count=\"${rows.size}\">")

        rows.forEachIndexed { index, row ->
            val contact = row.senderWxId?.let { wxId -> contacts.firstOrNull { it.wxId == wxId } }
            if (contact != null) {
                val sourceName = contact.nickname.ifBlank { contact.displayName }
                val sourceHeadUrl = contact.avatarUrl
                val sourcetime = timeStringAt(index)
                val srcMsgCreateTime = nowSeconds + index * 2L
                val fromNewMsgId = generateFakeMsgId(index)

                append("<dataitem datatype=\"1\" dataid=\"\">")
                append("<datadesc>${xmlEscape(row.text)}</datadesc>")
                append("<sourcename>${xmlEscape(sourceName)}</sourcename>")
                append("<sourceheadurl>${xmlEscape(sourceHeadUrl)}</sourceheadurl>")
                append("<sourcetime>$sourcetime</sourcetime>")
                append("<srcMsgCreateTime>$srcMsgCreateTime</srcMsgCreateTime>")
                append("<fromnewmsgid>$fromNewMsgId</fromnewmsgid>")
                append("<dataitemsource><hashusername></hashusername></dataitemsource>")
                append("</dataitem>")
            }
        }

        appendLine("</datalist></recordinfo>]]></recorditem>")
        appendLine("    </appmsg>")
        appendLine("    <extcommoninfo>")
        appendLine("        <media_expire_at>2000000000</media_expire_at>")
        appendLine("    </extcommoninfo>")
        appendLine("</msg>")
    }
}

private fun generateFakeMsgId(index: Int): Long {
    val a = System.currentTimeMillis()
    val b = Random.nextLong(1_000_000_000L, 9_999_999_999L)
    return abs(a + b + index)
}

private fun xmlEscape(text: String): String {
    return buildString(text.length) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}

private fun xmlEscapeNewLines(text: String): String {
    return xmlEscape(text).replace("\n", "&#x0A;")
}
