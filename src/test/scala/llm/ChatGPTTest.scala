package com.gridoai.adapters.llm.chatGPT

import munit.FunSuite

class ChatGPTTokenCountTest extends FunSuite {

  test("calculateTokenQuantity calculates correctly") {

    val content = "hi everyone! i'm a beautiful test, right?"
    val expectedTokenQuantity = 11

    val tokenQuantity = ChatGPTClient.calculateTokenQuantity(content)

    assertEquals(
      tokenQuantity,
      expectedTokenQuantity
    )
  }

  test("calculateTokenQuantity calculates correctly with no text") {

    val content = ""
    val expectedTokenQuantity = 0

    val tokenQuantity = ChatGPTClient.calculateTokenQuantity(content)

    assertEquals(
      tokenQuantity,
      expectedTokenQuantity
    )
  }

  test("calculateTokenQuantity works with non-utf-8 characteres") {

    val content = "������"
    val expectedTokenQuantity = 2

    val tokenQuantity = ChatGPTClient.calculateTokenQuantity(content)

    assertEquals(
      tokenQuantity,
      expectedTokenQuantity
    )
  }
}
