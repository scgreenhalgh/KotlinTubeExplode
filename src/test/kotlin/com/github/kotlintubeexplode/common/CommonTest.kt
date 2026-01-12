package com.github.kotlintubeexplode.common

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Common module")
class CommonTest {

    @Nested
    @DisplayName("Batch")
    inner class BatchTests {

        @Test
        fun `should create batch with items`() {
            val items = listOf(TestItem("a"), TestItem("b"), TestItem("c"))
            val batch = Batch(items)

            batch.items.size shouldBe 3
            batch.items[0].value shouldBe "a"
            batch.items[1].value shouldBe "b"
            batch.items[2].value shouldBe "c"
        }

        @Test
        fun `should create empty batch`() {
            val batch = Batch<TestItem>(emptyList())

            batch.items.size shouldBe 0
        }

        @Test
        fun `batch items should implement IBatchItem`() {
            val item = TestItem("test")
            item.shouldBeInstanceOf<IBatchItem>()
        }

        @Test
        fun `should iterate over batch items`() {
            val items = listOf(TestItem("a"), TestItem("b"))
            val batch = Batch(items)

            val collected = mutableListOf<String>()
            for (item in batch.items) {
                collected.add(item.value)
            }

            collected shouldBe listOf("a", "b")
        }
    }

    @Nested
    @DisplayName("Language")
    inner class LanguageTests {

        @Test
        fun `should create language with code and name`() {
            val lang = Language("en", "English")

            lang.code shouldBe "en"
            lang.name shouldBe "English"
        }

        @Test
        fun `should create language with code only`() {
            val lang = Language("fr", "fr")

            lang.code shouldBe "fr"
            lang.name shouldBe "fr"
        }

        @Test
        fun `should have correct toString`() {
            val lang = Language("en", "English")
            lang.toString() shouldBe "English (en)"
        }

        @Test
        fun `should support equality`() {
            val lang1 = Language("en", "English")
            val lang2 = Language("en", "English")
            val lang3 = Language("fr", "French")

            (lang1 == lang2) shouldBe true
            (lang1 == lang3) shouldBe false
        }

        @Test
        fun `common language codes should work`() {
            val english = Language("en", "English")
            val spanish = Language("es", "Spanish")
            val japanese = Language("ja", "Japanese")
            val korean = Language("ko", "Korean")

            english.code shouldBe "en"
            spanish.code shouldBe "es"
            japanese.code shouldBe "ja"
            korean.code shouldBe "ko"
        }
    }

    // Test helper class
    data class TestItem(val value: String) : IBatchItem
}
