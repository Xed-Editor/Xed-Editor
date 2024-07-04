/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/

package io.github.rosemoe.sora.widget

import io.github.rosemoe.sora.event.Event
import io.github.rosemoe.sora.event.EventManager.NoUnsubscribeReceiver
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.widget.component.EditorBuiltinComponent

/**
 * Subscribe event in editor, without [io.github.rosemoe.sora.event.Unsubscribe]
 *
 * @see CodeEditor.subscribeAlways
 */
inline fun <reified T : Event> CodeEditor.subscribeAlways(receiver: NoUnsubscribeReceiver<T>): SubscriptionReceipt<T> {
    return subscribeAlways(T::class.java, receiver)
}

/**
 * Subscribe event in editor
 *
 * @see CodeEditor.subscribeEvent
 */
inline fun <reified T : Event> CodeEditor.subscribeEvent(receiver: EventReceiver<T>): SubscriptionReceipt<T> {
    return subscribeEvent(T::class.java, receiver)
}

/**
 * Get builtin component so that you can enable/disable them or do some other actions.
 *
 * @see CodeEditor.getComponent
 */
inline fun <reified T : EditorBuiltinComponent> CodeEditor.getComponent(): T {
    return getComponent(T::class.java)
}

/**
 * Replace the built-in component to the given one. The new component's enabled state will extend the old one.
 *
 * @see CodeEditor.replaceComponent
 */
inline fun <reified T : EditorBuiltinComponent> CodeEditor.replaceComponent(component: T) {
    replaceComponent(T::class.java, component)
}