package dev.ujhhgtg.wekit.features.items.chat

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import de.robv.android.xposed.XC_MethodHook
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.api.core.WeMessageApi
import dev.ujhhgtg.wekit.features.api.core.WeServiceApi
import dev.ujhhgtg.wekit.features.api.ui.WeChatMessageViewApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.ui.utils.dpToPx
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs

@Feature(name = "左划引用消息", categories = ["聊天"], description = "在消息上左划以引用")
object SwipeToQuote : SwitchFeature(), IResolveDex,
    WeChatMessageViewApi.ICreateViewListener {

    // Mutable per-view gesture state, kept off the heap as long as the view lives
    private class SwipeState(
        val chattingContext: Any,
        val triggerThreshold: Float,
        var startX: Float = 0f,
        var startY: Float = 0f,
        var isDragging: Boolean = false,
        var triggered: Boolean = false,
    )

    // WeakHashMap: entries are automatically removed when the View is GC'd
    // (RecyclerView recycles views, so this stays small in practice)
    private val states: MutableMap<View, SwipeState> =
        Collections.synchronizedMap(WeakHashMap())

    private val springInterpolator = OvershootInterpolator(1.3f)

    // ── lifecycle ────────────────────────────────────────────────────────────

    override fun onEnable() {
        WeChatMessageViewApi.addListener(this)

        ViewGroup::class.reflekt()
            .firstMethod { name = "onInterceptTouchEvent" }
            .hookAfter {
                val v = thisObject as ViewGroup
                val s = states[v] ?: return@hookAfter
                val event = args[0] as MotionEvent

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        s.startX = event.rawX
                        s.startY = event.rawY
                        s.isDragging = false
                        s.triggered = false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - s.startX
                        val dy = event.rawY - s.startY
                        if (!s.isDragging && abs(dx) > abs(dy) && dx < 0) {
                            s.isDragging = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        if (s.isDragging) result = true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        s.isDragging = false
                    }
                }
            }

        View::class.reflekt()
            .firstMethod { name = "onTouchEvent"; superclass() }
            .hookAfter {
                val v = thisObject as View
                val s = states[v] ?: return@hookAfter
                val event = args[0] as MotionEvent

                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (s.isDragging) {
                            val rawDx = event.rawX - s.startX
                            v.translationX = rawDx.coerceIn(-s.triggerThreshold, 0f)
                            if (!s.triggered && rawDx < -s.triggerThreshold) {
                                s.triggered = true
                                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                            result = true
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (s.isDragging) {
                            v.animate()
                                .translationX(0f)
                                .setDuration(250)
                                .setInterpolator(springInterpolator)
                                .start()
                            if (s.triggered) onSwipeLeft(v, s.chattingContext)
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            s.isDragging = false
                            result = true
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        if (s.isDragging) {
                            v.animate().translationX(0f).setDuration(150).start()
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            s.isDragging = false
                        }
                    }
                }
            }
    }

    override fun onDisable() {
        WeChatMessageViewApi.removeListener(this)
        states.clear()
    }

    // ── ICreateViewListener ──────────────────────────────────────────────────

    override fun onCreateView(param: XC_MethodHook.MethodHookParam, view: View) {
        if (states.containsKey(view)) return
        val chattingContext = WeChatMessageViewApi.getChattingContextFromParam(param)
        states[view] = SwipeState(
            chattingContext = chattingContext,
            triggerThreshold = 60.dpToPx(view.context).toFloat(),
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun onSwipeLeft(originalView: View, chattingContext: Any) {
        val apiMan = chattingContext.reflekt()
            .firstField { type = WeServiceApi.apiManagerClass }
            .get()!!
        val api = WeServiceApi.getApiByClass(apiMan, classChattingUiFootComponent.clazz)
        val chatFooter = api.reflekt()
            .firstField { type = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter" }
            .get()!!
        val quoteMethod = chatFooter.reflekt()
            .firstMethod {
                parameters { params -> params[0] == WeMessageApi.classMsgInfo.clazz }
                returnType = Boolean::class
            }.self
        val chatHolder = originalView.tag.reflekt().getField("chatHolder", true)!!
        val msgInfo = methodGetMsgInfo.method.invoke(null, chatHolder, chattingContext)
        if (quoteMethod.parameterCount == 1) quoteMethod.invoke(chatFooter, msgInfo)
        else quoteMethod.invoke(chatFooter, msgInfo, null)
    }

    private val classChattingUiFootComponent by dexClass {
        searchPackages("com.tencent.mm.ui.chatting.component")
        matcher {
            usingEqStrings(
                "MicroMsg.ChattingUI.FootComponent",
                "onNotifyChange event %s talker %s"
            )
        }
    }

    private val methodGetMsgInfo by dexMethod {
        searchPackages("com.tencent.mm.ui.chatting.viewitems")
        matcher { usingEqStrings("ItemDataTag", "getCurrentMsg2 err") }
    }
}
