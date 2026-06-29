package dev.ujhhgtg.wekit.features.items.home_screen_menu

import de.robv.android.xposed.XC_MethodHook
import dev.ujhhgtg.wekit.features.api.core.WeConversationApi
import dev.ujhhgtg.wekit.features.api.ui.WeHomeScreenPopupMenuApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.ui.utils.CheckCircleIcon
import dev.ujhhgtg.wekit.utils.android.showToast

@Feature(name = "清空未读", categories = ["聊天", "首页右上角菜单"], description = "在首页右上角菜单添加「清空未读」选项")
object MarkAllAsRead : SwitchFeature(), WeHomeScreenPopupMenuApi.IMenuItemsProvider {

    override fun onEnable() {
        WeHomeScreenPopupMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeHomeScreenPopupMenuApi.removeProvider(this)
    }

    override fun getMenuItems(param: XC_MethodHook.MethodHookParam): List<WeHomeScreenPopupMenuApi.MenuItem> {
        return listOf(
            WeHomeScreenPopupMenuApi.MenuItem(
                777012, "清空未读", CheckCircleIcon
            ) {
                WeConversationApi.markAllAsRead()
                showToast("已将全部未读消息标为已读")
            }
        )
    }
}
