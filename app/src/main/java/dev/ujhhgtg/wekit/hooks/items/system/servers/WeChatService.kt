package dev.ujhhgtg.wekit.hooks.items.system.servers

import dev.ujhhgtg.wekit.hooks.api.core.WeApi
import dev.ujhhgtg.wekit.hooks.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.hooks.api.core.WeMessageApi
import dev.ujhhgtg.wekit.hooks.api.core.models.MessageType
import dev.ujhhgtg.wekit.utils.collections.LruCache
import kotlinx.serialization.Serializable

object WeChatService {

    private val GROUP_SENDER_REGEX = Regex("""^([^\n:]+):\n(.+)""", setOf(RegexOption.DOT_MATCHES_ALL))

    // groupId → Map<wxId, displayName>
    private val groupMembersCache = LruCache<String, Map<String, String>>()

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    sealed class Result<out T> {
        data class Success<out T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
    }

    // -------------------------------------------------------------------------
    // Shared request / response models (used by both MCP and REST layers)
    // -------------------------------------------------------------------------

    @Serializable
    data class ContactInfo(
        val wxId: String,
        val nickname: String,
        val customWxId: String = "",
        val remarkName: String = "",
    )

    @Serializable
    data class MessageInfo(
        val sender: String,
        val content: String,
        val type: String,
    )

    @Serializable
    data class SendMessageRequest(
        val type: String,
        val convId: String,
        val content: String,
    )

    // -------------------------------------------------------------------------
    // Operations
    // -------------------------------------------------------------------------

    fun sendMessage(req: SendMessageRequest): Result<Unit> =
        sendMessage(req.type, req.convId, req.content)

    fun sendMessage(type: String, convId: String, content: String): Result<Unit> = when (type) {
        "text" -> if (WeMessageApi.sendText(convId, content)) Result.Success(Unit)
        else Result.Error("Failed to send message")

        else -> Result.Error("Unsupported type: $type")
    }

    fun listContacts(type: String): Result<List<ContactInfo>> = when (type) {
        "all" -> Result.Success(WeDatabaseApi.getContacts().map {
            ContactInfo(it.wxId, it.nickname)
        })

        "friends" -> Result.Success(
            WeDatabaseApi.getFriends()
                .filter { c ->
                    c.type != 2051 && c.type != 2049 && c.wxId.startsWith("wxid_") && c.wxId != WeApi.selfWxId
                }.map {
                    ContactInfo(it.wxId, it.nickname, it.customWxId, it.remarkName)
                })

        "groups" -> Result.Success(WeDatabaseApi.getGroups().map {
            ContactInfo(it.wxId, it.nickname)
        })

        "official_accounts" -> Result.Success(WeDatabaseApi.getOfficialAccounts().map {
            ContactInfo(it.wxId, it.nickname)
        })

        else -> Result.Error("Unsupported type: $type")
    }

    fun listMessages(convId: String, pageIndex: Int = 1, pageSize: Int = 20): Result<List<MessageInfo>> {
        val isGroup = convId.endsWith("@chatroom")
        val membersMap: Map<String, String> = if (isGroup) {
            groupMembersCache.getOrPut(convId) {
                WeDatabaseApi.getGroupMembers(convId).associate { m ->
                    m.wxId to (m.remarkName.takeUnless { it.isBlank() }?.let { "$it (${m.nickname})" } ?: m.nickname)
                }
            }
        } else emptyMap()

        val messages = WeDatabaseApi.getMessages(convId, pageIndex, pageSize).map { msg ->
            val match = GROUP_SENDER_REGEX.find(msg.content).takeIf { isGroup }
            val sender = match?.groupValues?.get(1)?.let { membersMap[it] ?: it } ?: "<myself>"
            val isText = MessageType.fromCode(msg.typeCode)?.isText ?: false
            val typeStr = MessageType.fromCode(msg.typeCode)?.name?.lowercase() ?: "unknown"
            val content = if (isText) match?.groupValues?.get(2) ?: msg.content else "<type:$typeStr>"
            MessageInfo(sender, content, typeStr)
        }
        return Result.Success(messages)
    }

    fun listGroupMembers(groupId: String): Result<List<ContactInfo>> =
        Result.Success(WeDatabaseApi.getGroupMembers(groupId).map {
            ContactInfo(it.wxId, it.nickname, it.customWxId, it.remarkName)
        })

    fun getConvIdByDisplayName(displayName: String, groupId: String? = null): Result<String> {
        if (groupId != null) {
            return WeDatabaseApi.getGroupMembers(groupId)
                .find { it.nickname == displayName || it.remarkName == displayName }
                ?.let { Result.Success(it.wxId) }
                ?: Result.Error("search matched 0 contact")
        }
        WeDatabaseApi.getFriends()
            .find { it.nickname == displayName || it.remarkName == displayName }
            ?.let { return Result.Success(it.wxId) }
        WeDatabaseApi.getGroups()
            .find { it.nickname == displayName }
            ?.let { return Result.Success(it.wxId) }
        WeDatabaseApi.getOfficialAccounts()
            .find { it.nickname == displayName }
            ?.let { return Result.Success(it.wxId) }
        return Result.Error("search matched 0 contact")
    }

    fun getDisplayNameByConvId(convId: String): Result<String> =
        Result.Success(WeDatabaseApi.getDisplayName(convId))
}
