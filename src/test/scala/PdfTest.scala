import cats.effect.IO
import cats.effect.SyncIO
import com.gridoai.adapters.*
import munit.CatsEffectSuite
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
class PdfParserTest extends CatsEffectSuite {
  test("Pdf parse") {
    val t = Files.readAllBytes(Paths.get("./src/test/resources/test.pdf"))
    val expected =
      Files.readString(Paths.get("./src/test/resources/expected_pdf.txt"))
    parsePdf(t).assertEquals(expected)

  }
}
