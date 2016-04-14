package org.kotyle.xamil.staxparser

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.FileInputStream


class SellerNameTracker(path: String): AbstractTextTracker<String>(path) {
    override fun onEndTag(): String = text.trim()
}

class RootTracker(path: String): AbstractCollectionTracker<List<String>>(path) {
    override fun onEndTag(): List<String> =
            children.filterIsInstance<String>()
}

class ParserTest {
    @Test
    fun frParse() {
        val parser = StaxParser()
        parser.addTracker(SellerNameTracker("/root/listing/seller_info/seller_name"))
        parser.addTracker(RootTracker("/root"))

        val data = FileInputStream(File("samples/xml/auction.xml")).use { parser.parse(it) } as? List<String>?
        assertEquals(listOf("537_sb_3", "lapro8", "aboutlaw", "1137_sb_8", "537_sb_3", "42_sb_691", "42_sb_691", "okcbuy", "bell25", "lapro8"), data)
    }
}