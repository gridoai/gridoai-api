import com.gridoai.adapters.*
import munit.CatsEffectSuite

import java.nio.file.Files
import java.nio.file.Paths
import cats.effect.IO
class PdfParserTest extends CatsEffectSuite {
  test("Pdf parse") {
    val t = Files.readAllBytes(Paths.get("./src/test/resources/test.pdf"))
    val expected =
      Files.readString(Paths.get("./src/test/resources/expected_pdf.txt"))
    extractTextFromPdf(t).value.assertEquals(Right(expected))

  }
}
