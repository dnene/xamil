package org.kotyle.xamil.staxparser

import org.kotyle.kylix.helpers.EmptyIterator
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.*
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.Characters
import javax.xml.stream.events.EndElement
import javax.xml.stream.events.StartElement

val log = LoggerFactory.getLogger("org.kotyle.xamil.staxparser")

fun <K,V> Sequence<Pair<K,V>>.toMap() : Map<K,V> = this.fold(mapOf<K,V>()) { acc, pair -> acc + pair }

interface Tracker<T> {
    val path: String
    val tag: String
    fun onEndTag(): T
    fun reset()
}

interface AttributesTracker<T>: Tracker<T> {
    val attrs: Map<String, String>
    fun onStartTag(attrs: Map<String, String>)
}

interface TextTracker<T>: Tracker<T> {
    val text: String
    fun addCharacters(data: String)
}

interface CollectionTracker<T>: Tracker<T> {
    val children: List<Any>
    fun addChild(child: Any)
}

abstract class AbstractTextTracker<T>(override val path: String): TextTracker<T> {
    override val tag = path.split("/").last()

    override val text: String
        get() = texts.joinToString()
    protected var texts: MutableList<String> = mutableListOf()
    override fun addCharacters(data: String) {texts = texts.apply { add(data)} }

    override fun reset() = texts.clear()
}

abstract class AbstractAttributesTracker<T>(override val path: String): AttributesTracker<T> {
    override val tag = path.split("/").last()

    override val attrs: Map<String,String>
        get() = accMap
    protected val accMap = mutableMapOf<String,String>()
    override fun onStartTag(attrs: Map<String, String>) { accMap += attrs}

    override fun reset() = accMap.clear()
}

abstract class AbstractCollectionTracker<T>(override val path: String): CollectionTracker<T> {
    override val tag = path.split("/").last()

    override val children: List<Any>
        get() = accList
    protected val accList = mutableListOf<Any>()
    override fun addChild(child: Any) {
        accList.add(child)
    }
    override fun reset() = accList.clear()
}

abstract class AbstractTextAttributesTracker<T>(override val path: String): TextTracker<T>, AttributesTracker<T> {
    override val tag = path.split("/").last()

    override val text: String
        get() = texts.joinToString()
    protected var texts: MutableList<String> = mutableListOf()
    override fun addCharacters(data: String) {texts = texts.apply { add(data)} }

    override val attrs: Map<String,String>
        get() = accMap
    protected val accMap = mutableMapOf<String,String>()
    override fun onStartTag(attrs: Map<String, String>) { accMap += attrs}

    override fun reset() { texts.clear(); accMap.clear() }
}

abstract class AbstractAttributesCollectionTracker<T>(override val path: String): AttributesTracker<T>, CollectionTracker<T> {
    override val tag = path.split("/").last()

    override val attrs: Map<String,String>
        get() = accMap
    protected val accMap = mutableMapOf<String,String>()
    override fun onStartTag(attrs: Map<String, String>) { accMap += attrs}

    override val children: List<Any>
        get() = accList
    protected val accList = mutableListOf<Any>()
    override fun addChild(child: Any) { accList.add(child) }

    override fun reset() { accMap.clear() ; accList.clear() }
}

abstract class AbstractTextCollectionTracker<T>(override val path: String): TextTracker<T>, CollectionTracker<T> {
    override val tag = path.split("/").last()

    override val text: String
        get() = texts.joinToString()
    protected var texts: MutableList<String> = mutableListOf()
    override fun addCharacters(data: String) {texts = texts.apply { add(data)} }

    override val children: List<Any>
        get() = accList
    protected val accList = mutableListOf<Any>()
    override fun addChild(child: Any) { accList.add(child) }

    override fun reset() { texts.clear() ; accList.clear() }
}

abstract class AbstractTracker<T>(override val path: String): CollectionTracker<T>, AttributesTracker<T>, TextTracker<T> {
    override val tag = path.split("/").last()

    override val children: List<Any>
        get() = accList
    protected val accList = mutableListOf<Any>()
    override fun addChild(child: Any) { accList.add(child) }

    override val text: String
        get() = texts.joinToString()
    protected var texts: MutableList<String> = mutableListOf()
    override fun addCharacters(data: String) {texts = texts.apply { add(data)} }

    override val attrs: Map<String,String>
        get() = accMap
    protected val accMap = mutableMapOf<String,String>()
    override fun onStartTag(attrs: Map<String, String>) { accMap += attrs}

    override fun reset() { accList.clear(); texts.clear() ; accMap.clear() }
}

sealed class Stack<out T>: Collection<T> {
    abstract fun toString(buf: StringBuffer): StringBuffer
    abstract fun matching(predicate: (T) -> Boolean): T?
    object Nil: Stack<Nothing>() {
        override val size: Int = 0
        override fun contains(element: Nothing): Boolean = false
        override fun containsAll(elements: Collection<Nothing>): Boolean = false
        override fun isEmpty(): Boolean = true
        override fun iterator(): Iterator<Nothing> = EmptyIterator
        override fun toString(): String = "<Nil>"
        override fun toString(buf: StringBuffer) = buf.apply { append("") }
        override fun matching(predicate: (Nothing) -> Boolean): Nothing? = null
    }
    class Item<T>(val value: T, val next: Stack<T> = Nil): Stack<T>() {
        fun pop(): Pair<T, Stack<T>> = Pair(value, next)
        override fun toString(buf: StringBuffer): StringBuffer {
            buf.append(value.toString())
            buf.append(',')
            return next.toString(buf)
        }
        override fun toString(): String {
            val buf = StringBuffer()
            buf.append("Stack[")
            this.toString(buf)
            buf.append("]")
            return buf.toString()
        }
        override val size: Int by lazy { next.size + 1 }
        override fun contains(element: T): Boolean = element == value || next.contains(element)
        private fun allContained(elements: Set<T>): Boolean {
            return if (elements.isEmpty()) {
                true
            } else if (next is Item) {
                val remainder = if (elements.contains(value)) elements - value else elements
                next.allContained(remainder)
            } else
                false
        }

        override fun containsAll(elements: Collection<T>): Boolean =
            allContained(elements.toSet())


        override fun isEmpty(): Boolean = false

        override fun iterator(): Iterator<T> = object:Iterator<T> {
            var current: Stack<T> = this@Item
            override fun hasNext(): Boolean = current is Item<T>
            override fun next(): T =
                if (current !is Item<T>) throw(NoSuchElementException()) else
                (current as? Item<T>)?.let {
                    val toBeReturned = it.value
                    current = it.next
                    toBeReturned
                }
        }
        override fun matching(predicate: (T) -> Boolean): T? =
                if (predicate(value)) value else next.matching(predicate)
    }
}
fun <T> Stack<T>.push(value: T): Stack<T> = Stack.Item<T>(value, this)
fun <T> Stack<T>.path(): String = this.iterator().asSequence().toList().reversed().joinToString("/","/")
infix fun <T> T?.or(other: T?): T? =
    if (this == null) other else this
infix fun <T> T?.or(other: () -> T?): T? =
        if (this == null) other() else this

class StaxParser() {
    val factory = XMLInputFactory.newInstance()
    val trackers = mutableMapOf<String,Tracker<*>>()
    fun addTracker(tracker: Tracker<*>) { trackers.put(tracker.path,tracker) }
    fun parse(stream: InputStream): Any? {
        val reader = factory.createXMLEventReader(stream)
        var tagStack: Stack<String> = Stack.Nil
        var stack: Stack<Tracker<*>?> = Stack.Nil
        var lastResult: Any? = null
        while (reader.hasNext()) {
            val event = reader.nextEvent()
            when(event.eventType) {
                XMLStreamConstants.START_DOCUMENT -> { }
                XMLStreamConstants.START_ELEMENT -> {
                    val startElement = event as StartElement
                    val name = startElement.name.localPart
                    tagStack = tagStack.push(name)
                    val tracker = trackers.get(tagStack.path())
                    tracker?.let {
                        it.reset()
                        (it as? AttributesTracker)?.onStartTag(
                                ((startElement.attributes as Iterator<Attribute>).asSequence().map {
                                    it.name.localPart to it.value}).toMap()) }
                    stack = stack.push(tracker)

                }
                XMLStreamConstants.CHARACTERS -> {
                    if (stack is Stack.Item) (stack.value as? TextTracker)?.addCharacters((event as Characters).data)
                }
                XMLStreamConstants.END_ELEMENT -> {
                    val endElement = event as EndElement
                    val name = endElement.name.localPart
                    lastResult = stack.let {
                        if (it is Stack.Item) {
                            val (tracker, tail) = it.pop()
                            val result = tracker?.onEndTag()
                            result?.apply { log.debug("Tag:${name}: ${result}");
                                (tail.matching{it != null} as? CollectionTracker)?.addChild(result) }
                            stack = tail
                            result
                        } else null
                    }
                    if (tagStack is Stack.Item) {
                        val (foo, tail) = tagStack.pop()
                        tagStack = tail
                    }
                }
                XMLStreamConstants.END_DOCUMENT -> { }
            }
        }
        return lastResult
    }
}