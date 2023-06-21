import cats.effect.IO
import cats.effect.SyncIO
import com.gridoai.adapters.*
import munit.CatsEffectSuite
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

import java.io.File
class pdf extends CatsEffectSuite {
  test("HelloWorld") {
    val c = java.util.Calendar.getInstance()
    val now = c.getTime()
    val stripper = new PDFTextStripper()
    val t = stripper.getText(
      PDDocument.load(new File("/Users/davi/Downloads/grokking_algorithms.pdf"))
    );
    val end = java.util.Calendar.getInstance().getTime()

    println("Time taken: " + (end.getTime() - now.getTime()))
  }
}
