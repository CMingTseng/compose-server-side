@file:OptIn(ExperimentalComposeApi::class)

package me.shika.compose

import androidx.compose.*

typealias ServerUpdater<T> = ComposerUpdater<HtmlNode, T>

class ServerComposer(
    root: HtmlNode,
    slotTable: SlotTable,
    val commandDispatcher: RenderCommandDispatcher,
    applier: Applier<HtmlNode> = Applier(
        root = root,
        adapter = ServerApplyAdapter(commandDispatcher)
    ),
    recomposer: Recomposer
) : Composer<HtmlNode>(slotTable, applier, recomposer) {

    init {
        FrameManager.ensureStarted()
    }

    inline fun <T : HtmlNode> emit(
        key: Any,
        /*crossinline*/ ctor: (RenderCommandDispatcher) -> T,
        update: ServerUpdater<T>.() -> Unit
    ) {
        println("emit1 $key")
        startNode(key)

        val node = if (inserting) {
            ctor(commandDispatcher).also { emitNode(it) }
        } else {
            useNode() as T
        }

        ServerUpdater(this, node).update()
        endNode()
    }

    inline fun <T : HtmlNode> emit(
        key: Any,
        /*crossinline*/ ctor: (RenderCommandDispatcher) -> T,
        update: ServerUpdater<T>.() -> Unit,
        children: () -> Unit
    ) {
        println("emit2 $key")
        startNode(key)

        val node = if (inserting) {
            ctor(commandDispatcher).also {
                emitNode(it)
            }
        } else {
            useNode() as T
        }

        ServerUpdater(this, node).update()
        children()
        endNode()
    }

    private class ServerApplyAdapter(private val commandDispatcher: RenderCommandDispatcher) : ApplyAdapter<HtmlNode> {
        private var depth = 0

        override fun HtmlNode.start(instance: HtmlNode) {
            depth++
        }

        override fun HtmlNode.insertAt(index: Int, instance: HtmlNode) {
            println("insert $id $index $instance")
            tag().insertAt(index, instance)
            commandDispatcher.insert(this, index, instance)
        }

        override fun HtmlNode.removeAt(index: Int, count: Int) {
            println("remove $id $index $count")
            tag().remove(index, count)
            commandDispatcher.remove(this, index, count)
        }

        override fun HtmlNode.move(from: Int, to: Int, count: Int) {
            println("move $id $from $to $count")
            tag().move(from, to, count)
            commandDispatcher.move(this, from, to, count)
        }

        override fun HtmlNode.end(instance: HtmlNode, parent: HtmlNode) {
            depth--
            if (depth == 0) {
                commandDispatcher.commit()
            }
        }

        private fun HtmlNode.tag() =
            when (this) {
                is HtmlNode.Tag -> this
                is HtmlNode.Text -> throw IllegalStateException("Only tag can have children")
            }

    }
}
