import munit._
import com.gridoai.parsers.filterNonUtf8

class FilterNonUtf8Suite extends FunSuite {

  test("filterNonUtf8 should remove non-UTF8 characters") {
    val expected = "çáõñßðcʝþøæßðʝµ/ħ"
    val input = s"���$expected���"
    assertEquals(filterNonUtf8(input), expected)

  }
}
