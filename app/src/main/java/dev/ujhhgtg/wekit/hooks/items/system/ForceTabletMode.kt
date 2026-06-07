package dev.ujhhgtg.wekit.hooks.items.system

import android.content.Context
import android.widget.Button
import androidx.compose.material3.Text
import androidx.core.view.isGone
import dev.ujhhgtg.wekit.dexkit.abc.IResolvesDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.hooks.core.SwitchHookItem
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import org.luckypray.dexkit.DexKitBridge

@HookItem(name = "强制平板模式", categories = ["系统与隐私"], description = "让微信将当前设备识别为平板")
object ForceTabletMode : SwitchHookItem(), IResolvesDex {

    private val methodIsTablet by dexMethod()
    private val methodOtherDeviceLoginButtonIsVisible by dexMethod()

    override fun onEnable() {
        methodIsTablet.hookBefore {
            result = true
        }

        methodOtherDeviceLoginButtonIsVisible.hookBefore {
            val view = args[0] as? Button? ?: return@hookBefore
            if (view.isGone) view.isGone = false
        }
    }

    override fun resolveDex(dexKit: DexKitBridge) {
        methodIsTablet.find(dexKit) {
            matcher {
                usingEqStrings("Lenovo TB-9707F", "eebbk")
            }
        }

        methodOtherDeviceLoginButtonIsVisible.find(dexKit) {
            matcher {
                usingEqStrings("loginAsOtherDeviceBtn")
            }
        }
    }

    override fun onBeforeToggle(newState: Boolean, context: Context): Boolean {
        if (newState) {
            showComposeDialog(context) {
                AlertDialogContent(
                    title = { Text(text = "警告") },
                    text = { Text(text = "此功能可能导致账号异常, 确定要启用吗?") },
                    confirmButton = {
                        Button(onClick = {
                            applyToggle(true)
                            onDismiss()
                        }) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onDismiss) {
                            Text("取消")
                        }
                    }
                )
            }
            return false
        }

        return true
    }
}
