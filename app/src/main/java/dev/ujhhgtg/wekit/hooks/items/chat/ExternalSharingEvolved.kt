package dev.ujhhgtg.wekit.hooks.items.chat

import android.app.Person
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import dev.ujhhgtg.wekit.hooks.core.ClickableHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.utils.HostInfo

@HookItem(name = "分享进化", categories = ["聊天"], description = "让微信的系统分享菜单更易用 (没写完)")
object ExternalSharingEvolved : ClickableHookItem() {

    override fun onEnable() {
        val ctx = HostInfo.application

        val sm =
            ctx.getSystemService(ShortcutManager::class.java)

        val contact = Person.Builder()
            .setName("John Doe")
            .setImportant(true)
            .build()

        val shortcut = ShortcutInfo.Builder(ctx, "contact_id_123")
            .setShortLabel("John Doe")
            .setPerson(contact)
            .setCategories(setOf("android.intent.category.DEFAULT"))
            .setIntent(
                Intent(Intent.ACTION_SEND)
                    .setComponent(
                        ComponentName(
                            "com.tencent.mm",
                            // although this activity is called 'ShareImg',
                            // it is actually used to handle all types
                            "com.tencent.mm.ui.tools.ShareImgUI"
                        )
                    )
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Hello")
            )
            .setLongLived(true)
            .build()

        sm.dynamicShortcuts = listOf(shortcut)
    }

    override fun onClick(context: Context) {
        val ctx = HostInfo.application
        val sm = ctx.getSystemService(ShortcutManager::class.java)

        sm.removeDynamicShortcuts(listOf("contact_id_123"))
    }
}
