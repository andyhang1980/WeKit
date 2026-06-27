package me.hd.wauxv.data.bean

import androidx.annotation.Keep
import com.alibaba.fastjson2.JSONObject
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.features.api.core.WeContactLabelApi

@Suppress("unused")
@Keep
class ContactLabelBean(
    @JvmField val origin: Any
) {
    @JvmField val id: Int = if (origin is WeContactLabelApi.ContactLabel) {
        origin.labelId
    } else {
        (origin.reflekt().getField("field_labelID", true) as? Number)?.toInt() ?: 0
    }

    @JvmField val name: String = if (origin is WeContactLabelApi.ContactLabel) {
        origin.labelName
    } else {
        origin.reflekt().getField("field_labelName", true) as? String ?: ""
    }

    fun getId(): Int = id
    fun getName(): String = name
    fun getOrigin(): Any = origin

    override fun toString(): String {
        val json = JSONObject()
        json["id"] = id
        json["name"] = name
        return json.toString()
    }
}
