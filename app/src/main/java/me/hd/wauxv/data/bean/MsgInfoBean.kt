package me.hd.wauxv.data.bean

import androidx.annotation.Keep
import dev.ujhhgtg.wekit.features.api.core.models.MessageInfo

@Suppress("unused")
@Keep
class MsgInfoBean(
    @JvmField val origin: Any
) {
    val messageInfo = MessageInfo(origin)

    @JvmField val msgId: Long = messageInfo.id
    @JvmField val msgSvrId: Long = messageInfo.serverId
    @JvmField val type: Int = messageInfo.typeCode
    @JvmField val isSendInt: Int = messageInfo.isSend
    @JvmField val createTime: Long = messageInfo.createTime
    @JvmField val talker: String = messageInfo.talker
    @JvmField val originContent: String = messageInfo.content
    @JvmField val imgPath: String? = messageInfo.imagePath
    @JvmField val lvBuffer: ByteArray = messageInfo.lvBuffer
    @JvmField val talkerId: Int = messageInfo.talkerId
    @JvmField val msgSeq: Long = messageInfo.seq

    fun getMsgId(): Long = msgId
    fun getMsgSvrId(): Long = msgSvrId
    fun getType(): Int = type
    fun getCreateTime(): Long = createTime
    fun getTalker(): String = talker
    fun getOriginContent(): String = originContent
    fun getImgPath(): String? = imgPath
    fun getLvBuffer(): ByteArray = lvBuffer
    fun getTalkerId(): Int = talkerId
    fun getMsgSeq(): Long = msgSeq
    fun getOrigin(): Any = origin

    fun isSend(): Boolean = isSendInt == 1
    fun isGroupChat(): Boolean = talker.endsWith("@chatroom") || talker.endsWith("@im.chatroom")
    fun isChatroom(): Boolean = isGroupChat()

    fun isText(): Boolean = type == 1
    fun isImage(): Boolean = type == 3 || type == 39 || type == 13
    fun isEmoji(): Boolean = type == 47
    fun isVoice(): Boolean = type == 34
    fun isVideo(): Boolean = type == 43 || type == 62
    fun isShareCard(): Boolean = type == 42
    fun isPat(): Boolean = type == 10002 && originContent.contains("patMsg")
    fun isSystem(): Boolean = type == 10000 || type == 10002
    fun isQuote(): Boolean = type == 0x31 && originContent.contains("<refermsg>")
    fun isLocation(): Boolean = type == 48
    fun isApp(): Boolean = type == 49
    fun isLink(): Boolean = type == 49 && originContent.contains("<url>")
    fun isTransfer(): Boolean = type == 49 && originContent.contains("<transferid>")
    fun isRedBag(): Boolean = type == 49 && originContent.contains("<redenvelope>")
    fun isVideoNumberVideo(): Boolean = type == 49 && originContent.contains("<finderFeed>")
    fun isNote(): Boolean = type == 49 && originContent.contains("<note>")
    fun isFile(): Boolean = type == 49 && originContent.contains("<file>")
    fun isRecalled(): Boolean = type == 10002 && originContent.contains("revokemsg")

    fun getSendTalker(): String {
        return messageInfo.sender
    }

    fun getContent(): String {
        return messageInfo.actualContent
    }

    @Keep
    class FileMsg(val xml: String) {
        fun getTitle(): String = xml.substringAfter("<title>").substringBefore("</title>")
    }

    @Keep
    class ImageMsg(val xml: String) {
        fun getAesKey(): String = xml.substringAfter("aeskey=\"").substringBefore("\"")
        fun getCdnUrl(): String = xml.substringAfter("cdnthumburl=\"").substringBefore("\"")
    }

    @Keep
    class QuoteMsg(val xml: String) {
        fun getTitle(): String = xml.substringAfter("<title>").substringBefore("</title>")
    }

    @Keep
    class TransferMsg(val xml: String) {
        fun getTransactionId(): String = xml.substringAfter("<transcationid><![CDATA[").substringBefore("]]>")
        fun getTransferId(): String = xml.substringAfter("<transferid><![CDATA[").substringBefore("]]>")
    }

    @Keep
    class PatMsg(val xml: String) {
        fun getTemplate(): String = xml.substringAfter("<template><![CDATA[").substringBefore("]]>")
        fun getFromUser(): String = xml.substringAfter("<fromUser>").substringBefore("</fromUser>")
    }

    fun getFileMsg(): FileMsg? = if (isFile()) FileMsg(originContent) else null
    fun getImageMsg(): ImageMsg? = if (isImage()) ImageMsg(originContent) else null
    fun getQuoteMsg(): QuoteMsg? = if (isQuote()) QuoteMsg(originContent) else null
    fun getTransferMsg(): TransferMsg? = if (isTransfer()) TransferMsg(originContent) else null
    fun getPatMsg(): PatMsg? = if (isPat()) PatMsg(originContent) else null
}
