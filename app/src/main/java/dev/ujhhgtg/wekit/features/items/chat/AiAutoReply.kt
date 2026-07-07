package dev.ujhhgtg.wekit.features.items.chat

import android.content.ContentValues
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseListenerApi
import dev.ujhhgtg.wekit.features.api.core.WeMessageApi
import dev.ujhhgtg.wekit.features.api.core.models.MessageType
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.preferences.WePrefs.Companion.prefOption
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.DefaultColumn
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast
import dev.ujhhgtg.wekit.utils.strings.isGroupChatWxId
import dev.ujhhgtg.wekit.utils.strings.stripWxId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Feature(
    name = "AI 自动回复",
    categories = ["聊天"],
    description = "接入 AI 大模型自动回复消息, 支持 DeepSeek/Qwen/智谱/Silicon 等 OpenAI 兼容 API\n" +
            "私聊自动回复, 群聊需@或关键词触发\n" +
            "发送 #绑定AI / #解绑AI 管理聊天室绑定"
)
object AiAutoReply : ClickableFeature(), WeDatabaseListenerApi.IInsertListener {

    private val TAG = This.Class.simpleName

    // ---- Configuration ----

    private var apiUrl by prefOption(
        "ai_reply_api_url",
        "https://api.deepseek.com/v1/chat/completions"
    )
    private var apiKey by prefOption("ai_reply_api_key", "")
    private var model by prefOption("ai_reply_model", "deepseek-chat")
    private var systemPrompt by prefOption(
        "ai_reply_system_prompt",
        "你是一个友好的助手, 用简洁自然的中文回复, 语气亲切但不啰嗦。"
    )
    private var triggerKeyword by prefOption("ai_reply_trigger_keyword", "")
    private var maxHistory by prefOption("ai_reply_max_history", 10)

    // ---- State ----

    /** Set of bound talker IDs (conversation IDs) */
    private val boundChats = ConcurrentHashMap.newKeySet<String>()

    /** Conversation history: talkerId -> list of messages (oldest first, capped at maxHistory * 2 entries) */
    private val chatHistories = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---- Serialization models for OpenAI-compatible API ----

    @Serializable
    data class ChatMessage(val role: String, val content: String)

    @Serializable
    data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7,
        val max_tokens: Int = 1024,
    )

    @Serializable
    data class ChatCompletionChoice(
        val message: ChatMessage,
        val finish_reason: String? = null,
    )

    @Serializable
    data class ChatCompletionResponse(
        val choices: List<ChatCompletionChoice>? = null,
        val error: ApiError? = null,
    )

    @Serializable
    data class ApiError(val message: String, val type: String? = null)

    // ---- Lifecycle ----

    override fun onEnable() {
        WeDatabaseListenerApi.addListener(this)
        loadBoundChats()
    }

    override fun onDisable() {
        WeDatabaseListenerApi.removeListener(this)
        saveBoundChats()
        boundChats.clear()
        chatHistories.clear()
    }

    // ---- Database Listener ----

    override fun onInsert(table: String, values: ContentValues) {
        if (table != "message") return

        try {
            val isSend = values.getAsInteger("isSend") ?: 1
            if (isSend == 1) return // ignore self-sent

            val typeCode = values.getAsInteger("type") ?: return
            val type = MessageType.fromCode(typeCode) ?: return
            if (!type.isText) return // only text messages

            val talker = values.getAsString("talker") ?: return
            if (!boundChats.contains(talker)) {
                // Check if this is a "#绑定AI" command
                checkBindCommand(talker, values)
                return
            }

            val content = values.getAsString("content") ?: return
            var userMessage = content

            // Check for "#解绑AI" command
            if (content.trim() == "#解绑AI") {
                boundChats.remove(talker)
                chatHistories.remove(talker)
                saveBoundChats()
                WeMessageApi.sendText(talker, "已解绑 AI 自动回复")
                return
            }

            // For group chats, strip the sender prefix and check trigger
            if (talker.isGroupChatWxId) {
                userMessage = content.stripWxId().trimStart('\n')
                if (triggerKeyword.isNotBlank() && !content.contains(triggerKeyword)) {
                    return // keyword not found, skip
                }
            }

            // Process the message
            val finalUserMessage = userMessage
            CoroutineScope(Dispatchers.IO).launch {
                processMessage(talker, finalUserMessage)
            }
        } catch (e: Exception) {
            WeLogger.e(TAG, "onInsert error", e)
        }
    }

    // ---- Core Logic ----

    private suspend fun processMessage(talker: String, userMessage: String) {
        try {
            // Get or create history
            val history = chatHistories.getOrPut(talker) { mutableListOf() }

            // Build messages list for API call
            val messages = buildList {
                add(ChatMessage("system", systemPrompt))
                // Add conversation history (capped)
                val recentHistory = if (history.size > maxHistory * 2) {
                    history.takeLast(maxHistory * 2)
                } else {
                    history.toList()
                }
                addAll(recentHistory)
                add(ChatMessage("user", userMessage))
            }

            // Call AI API
            val response = callAiApi(messages)
            if (response == null) {
                history.add(ChatMessage("user", userMessage))
                return
            }

            // Send reply
            withContext(Dispatchers.Main) {
                WeMessageApi.sendText(talker, response)
            }

            // Update history
            history.add(ChatMessage("user", userMessage))
            history.add(ChatMessage("assistant", response))

            // Trim history
            while (history.size > maxHistory * 4) {
                history.removeAt(0)
                if (history.isNotEmpty()) history.removeAt(0)
            }
        } catch (e: Exception) {
            WeLogger.e(TAG, "processMessage error for talker=$talker", e)
        }
    }

    private fun callAiApi(messages: List<ChatMessage>): String? {
        return try {
            val requestBody = ChatCompletionRequest(
                model = model,
                messages = messages,
            )

            val bodyStr = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)
            val body = bodyStr.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                WeLogger.e(TAG, "AI API error: ${response.code} $responseBody")
                return null
            }

            val chatResponse = json.decodeFromString(
                ChatCompletionResponse.serializer(), responseBody
            )

            if (chatResponse.error != null) {
                WeLogger.e(TAG, "AI API returned error: ${chatResponse.error.message}")
                return null
            }

            chatResponse.choices?.firstOrNull()?.message?.content
        } catch (e: Exception) {
            WeLogger.e(TAG, "callAiApi exception", e)
            null
        }
    }

    // ---- Bind/Unbind Commands ----

    private fun checkBindCommand(talker: String, values: ContentValues) {
        val content = values.getAsString("content") ?: return
        val trimmed = content.trim()

        if (trimmed == "#绑定AI") {
            boundChats.add(talker)
            saveBoundChats()
            WeMessageApi.sendText(talker, "已绑定 AI 自动回复 ✓\n发送 #解绑AI 可取消绑定")
        }
    }

    // ---- Persistence ----

    private fun saveBoundChats() {
        val prefs = dev.ujhhgtg.wekit.preferences.WePrefs.default
        prefs.putString("ai_reply_bound_chats", boundChats.joinToString(","))
        prefs.save()
    }

    private fun loadBoundChats() {
        val prefs = dev.ujhhgtg.wekit.preferences.WePrefs.default
        val saved = prefs.getString("ai_reply_bound_chats") ?: ""
        if (saved.isNotBlank()) {
            boundChats.addAll(saved.split(",").filter { it.isNotBlank() })
        }
    }

    // ---- Settings UI ----

    override fun onClick(context: ComponentActivity) {
        showComposeDialog(context) {
            var apiUrlInput by remember { mutableStateOf(apiUrl) }
            var apiKeyInput by remember { mutableStateOf(apiKey) }
            var modelInput by remember { mutableStateOf(model) }
            var systemPromptInput by remember { mutableStateOf(systemPrompt) }
            var triggerKeywordInput by remember { mutableStateOf(triggerKeyword) }
            var maxHistoryInput by remember { mutableStateOf(maxHistory.toString()) }

            AlertDialogContent(
                title = { Text("AI 自动回复") },
                text = {
                    DefaultColumn {
                        TextField(
                            value = apiUrlInput,
                            onValueChange = { apiUrlInput = it },
                            label = { Text("API 地址") },
                            supportingText = { Text("OpenAI 兼容的 chat/completions 端点") }
                        )
                        TextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("API Key") }
                        )
                        TextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("模型名称") },
                            supportingText = { Text("如 deepseek-chat, qwen-turbo, glm-4 等") }
                        )
                        TextField(
                            value = systemPromptInput,
                            onValueChange = { systemPromptInput = it },
                            label = { Text("系统提示词") },
                            minLines = 2
                        )
                        TextField(
                            value = triggerKeywordInput,
                            onValueChange = { triggerKeywordInput = it },
                            label = { Text("群聊触发关键词") },
                            supportingText = { Text("留空则群聊中所有消息都触发; 如 @机器人名称") }
                        )
                        TextField(
                            value = maxHistoryInput,
                            onValueChange = { maxHistoryInput = it },
                            label = { Text("上下文轮数") },
                            supportingText = { Text("保留最近 N 轮对话作为 AI 上下文") }
                        )
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        val maxHistoryInt = maxHistoryInput.toIntOrNull()
                        if (maxHistoryInt == null || maxHistoryInt < 1 || maxHistoryInt > 50) {
                            showToast("上下文轮数需为 1-50 之间的整数")
                            return@Button
                        }

                        apiUrl = apiUrlInput
                        apiKey = apiKeyInput
                        model = modelInput
                        systemPrompt = systemPromptInput
                        triggerKeyword = triggerKeywordInput
                        maxHistory = maxHistoryInt
                        onDismiss()
                        showToast("AI 自动回复配置已保存")
                    }) { Text("确定") }
                })
        }
    }
}
