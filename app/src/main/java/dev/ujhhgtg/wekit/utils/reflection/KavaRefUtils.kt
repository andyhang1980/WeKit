// i don't want to bother forking KavaRef
@file:Suppress("NOTHING_TO_INLINE", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NON_PUBLIC_CALL_FROM_PUBLIC_INLINE")

package dev.ujhhgtg.wekit.utils.reflection

import com.highcapable.kavaref.KavaRef
import com.highcapable.kavaref.condition.FieldCondition
import com.highcapable.kavaref.condition.MethodCondition
import com.highcapable.kavaref.condition.base.MemberCondition.Configuration.Companion.createConfiguration
import kotlin.reflect.KClass

inline fun <T : Any> KClass<T>.resolve() = java.resolve()
inline fun <T : Any> KClass<T>.asResolver() = java.resolve()

inline fun <T : Any> Class<T>.resolve() = KavaRef.MemberScope(createConfiguration())
inline fun <T : Any> Class<T>.asResolver() = resolve()

inline fun <T : Any> T.asResolver() = KavaRef.MemberScope(javaClass.createConfiguration(memberInstance = this))

inline fun <T : Any> KClass<T>.firstMethod(condition: MethodCondition<T>.() -> Unit = {}) = java.resolve().firstMethod(condition)
inline fun <T : Any> Class<T>.firstMethod(condition: MethodCondition<T>.() -> Unit = {}) = resolve().firstMethod(condition)
inline fun <T : Any> KClass<T>.firstField(condition: FieldCondition<T>.() -> Unit = {}) = java.resolve().firstField(condition)
inline fun <T : Any> Class<T>.firstField(condition: FieldCondition<T>.() -> Unit = {}) = resolve().firstField(condition)

inline fun <T : Any> KClass<T>.methods(condition: MethodCondition<T>.() -> Unit = {}) = java.resolve().methods(condition)
inline fun <T : Any> Class<T>.methods(condition: MethodCondition<T>.() -> Unit = {}) = resolve().methods(condition)
inline fun <T : Any> KClass<T>.fields(condition: FieldCondition<T>.() -> Unit = {}) = java.resolve().fields(condition)
inline fun <T : Any> Class<T>.fields(condition: FieldCondition<T>.() -> Unit = {}) = resolve().fields(condition)

inline fun <T : Any> KavaRef.MemberScope<T>.methods(condition: MethodCondition<T>.() -> Unit = {}) = method(condition)
inline fun <T : Any> KavaRef.MemberScope<T>.fields(condition: FieldCondition<T>.() -> Unit = {}) = field(condition)
