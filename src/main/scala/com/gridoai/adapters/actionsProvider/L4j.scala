package com.gridoai.adapters.l4j
import dev.langchain4j.agent.tool.Tool
import java.time.LocalDateTime
import scala.util.Try
import scala.sys.process._
import com.gridoai.adapters.cohere
import me.shadaj.scalapy.py
import scala.util.Try
import me.shadaj.scalapy.py.SeqConverters
import dev.langchain4j.model.openai.OpenAiChatModel
import com.gridoai.utils.getEnv
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.model.StreamingResponseHandler
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.output.Response

import cats.effect.IO
import cats.effect.std.Queue
import dev.langchain4j.service.AiServices
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import com.gridoai.adapters.cohere.formatCohereFormatForWeb
import dev.langchain4j.service.TokenStream
import com.gridoai.utils.|>

// Assuming necessary LangChain4j imports are available in Scala
// Import Java interoperability packages

class ToolBox {
  import cats.effect.unsafe.implicits.global

  @Tool("getCurrentTime")
  def getCurrentTime(): String = {
    println("[getCurrentTime] Getting current time")
    LocalDateTime.now.toString
  }

  @Tool("CalcProduct")
  def calculateMultiplication(a: Int, b: Int): Int = {
    println(s"[calculateMultiplication] Calculating the product of $a and $b")
    a * b
  }

  // For searchWeb, you'll need to adapt this function to use LangChain4j's way of integrating with external APIs or services.
  // Assuming LangChain4j has a way to make HTTP requests or you use a Java HTTP client that is callable from Scala.

  @Tool("SearchRealTimeWeb")
  def searchWeb(query: String): String = {
    cohere
      .searchWeb(query)
      .map(formatCohereFormatForWeb)
      .getOrElse("No response")
      .unsafeRunSync()
  }

  @Tool("CalcSum")
  def calculateSum(a: Int, b: Int): Int = {
    println(s"[calculateSum] Calculating the sum of $a and $b")
    a + b
  }

  // Transcribing YouTube videos might require calling external services or APIs.
  // You would need to adapt this to use Java libraries or external services that can be called from Scala.
  @Tool("TranscribeYouTubeVideoByID")
  def transcribeYouTubeVideo(videoId: String): String = {
    com.gridoai.l4j.transcribeYouTubeVideo(videoId)
  }
}

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import fs2.Stream
import java.util.function.Consumer

// Function to convert Java Consumer to Scala function
def javaConsumerToScalaFunction[A](consumer: Consumer[A]): A => Unit =
  consumer.accept

// Function to create a Stream from TokenStream
def createStreamFromTokenStream(
    tokenStream: TokenStream
) = {
  Queue.unbounded[IO, Option[String]].map { queue =>

    tokenStream.onNext(token =>
      println(s"onNext: $token")
      queue.offer(Some(token)).unsafeRunSync()
    ) // Assuming this returns a meaningful value or is called for side effects
    Stream.fromQueueNoneTerminated(queue)
  } |> Stream.eval
    |> (_.flatten)
}

// Example usage

trait Assistant {
  def chat(userMessage: String): TokenStream
}
// val assistant = AiServices.builder(Assistant.getClass())
val model = OpenAiStreamingChatModel
  .builder()
  .baseUrl(getEnv("OPENAI_ENDPOINT"))
  .apiKey(getEnv("OPENAI_API_KEY"))
  .modelName("gemini-pro")
  // .modelName(getEnv("OPENAI_MODEL"))
  .build();

def run: Unit = {
  val assistant = AiServices
    .builder(classOf[Assistant])
    .chatLanguageModel(
      OpenAiChatModel.withApiKey(System.getenv("OPENAI_API_KEY"))
    )
    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
    .tools(new ToolBox)
    .streamingChatLanguageModel(
      OpenAiStreamingChatModel
        .builder()
        .baseUrl(getEnv("OPENAI_ENDPOINT"))
        .apiKey(getEnv("OPENAI_API_KEY"))
        .modelName("gemini-pro")
        .logRequests(true)
        .logResponses(true)
        .build()
    )
    .build
  val question =
    "Como está o mercado nacional de tecnologia no brasil? Nao esqueça de citar as fontes"
  val answer = assistant.chat(question)
  createStreamFromTokenStream(answer)
    .evalMap(token => IO(println(s"Stream onNext: $token")))
    .compile
    .drain
    .unsafeRunSync()
  // The square root of the sum of the number of letters in the words "hello" and "world" is approximately 3.162.
}
// Adapt this to use scala 3 and fs2.

// StreamingChatLanguageModel model = OpenAiStreamingChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));

// String userMessage = "Tell me a joke";

// model.generate(userMessage, new StreamingResponseHandler<AiMessage>() {

//     @Override
//     public void onNext(String token) {
//         System.out.println("onNext: " + token);
//     }

//     @Override
//     public void onComplete(Response<AiMessage> response) {
//         System.out.println("onComplete: " + response);
//     }

//     @Override
//     public void onError(Throwable error) {
//         error.printStackTrace();
//     }
// });

// You need to make use of StreamingResponseHandler and convert this to a Stream
// def streamedWrapperOfCallbacK(userMessage: String) = {
//   val streamSetup = for {
//     queue <- Queue
//       .unbounded[IO, String] // Create an unbounded queue for StreamEvent
//     _ <- IO {
//       model.generate(
//         userMessage,
//         new StreamingResponseHandler[AiMessage]() {

//           override def onNext(token: String): Unit = {
//             queue.offer((token)).unsafeRunSync() // Enqueue onNext event
//             println("onNext: " + token)
//           }

//           override def onComplete(response: Response[AiMessage]): Unit = {
//             // queue
//             //   .offer(Complete(response))
//             //   .unsafeRunSync() // Enqueue onComplete event
//             println("onComplete: " + response)
//           }

//           override def onError(error: Throwable): Unit = {
//             // nt
//             error.printStackTrace()
//           }
//         }
//       )
//     }
//     stream = queue.s // Create a stream from the queue
//   } yield stream

//   // Run the stream
//   streamSetup.flatMap { stream =>
//     stream
//       .evalMap {
//         case Next(token)        => IO(println(s"Stream onNext: $token"))
//         case Complete(response) => IO(println(s"Stream onComplete: $response"))
//         case Error(error)       => IO.raiseError(error) // Rethrow error
//       }
//       .compile
//       .drain
//   }
// }
