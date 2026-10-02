package com.mergepdf.inone

import com.mergepdf.inone.util.PageNumberParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testParseSpecificPagesCommaSeparated() {
    // Exactly matches user example: "2,4,89"
    val result = PageNumberParser.parse("2,4,89", 100)
    assertEquals(listOf(2, 4, 89), result.validPages)
    assertTrue(result.outOfRangePages.isEmpty())
  }

  @Test
  fun testParseSpecificPagesWithSpacesAndOutOfRange() {
    val result = PageNumberParser.parse("2, 4, 89", 50)
    assertEquals(listOf(2, 4), result.validPages)
    assertEquals(listOf(89), result.outOfRangePages)
  }

  @Test
  fun testParseRangeAndSingleNumbers() {
    val result = PageNumberParser.parse("1, 3-5, 10", 20)
    assertEquals(listOf(1, 3, 4, 5, 10), result.validPages)
  }

  @Test
  fun testEvenOddShortcuts() {
    val even = PageNumberParser.getEvenPagesString(10)
    assertEquals("2, 4, 6, 8, 10", even)

    val odd = PageNumberParser.getOddPagesString(10)
    assertEquals("1, 3, 5, 7, 9", odd)
  }
}

