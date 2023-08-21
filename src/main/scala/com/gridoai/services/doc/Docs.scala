package com.gridoai.services.doc

import cats.Monad
import cats.effect.IO
import cats.implicits.*
import com.gridoai.adapters.llm.*
import com.gridoai.adapters.embeddingApi.*
import com.gridoai.domain.*
import com.gridoai.models.DocDB
import com.gridoai.utils.*
import com.gridoai.endpoints.*
import FileUploadError.*

import java.nio.file.Files
import com.gridoai.adapters.extractTextFromDocx
import com.gridoai.adapters.extractTextFromPptx

import java.util.UUID
import com.gridoai.parsers.ExtractTextError
import com.gridoai.adapters.*
import com.gridoai.parsers.*
import com.gridoai.models.DocumentPersistencePayload
import com.gridoai.auth.limitRole
import com.gridoai.auth.authErrorMsg
import com.gridoai.auth.AuthData
import sttp.model.Part

import java.io.File

def searchDoc(
    auth: AuthData
)(text: String, tokenLimit: Int, llmModel: String)(using
    db: DocDB[IO]
): IO[Either[String, List[Chunk]]] =
  getEmbeddingAPI("embaas")
    .embedChat(text)
    .flatMapRight(
      getChunks(
        getLLM(llmModel |> strToLLM).calculateChunkTokenQuantity,
        tokenLimit,
        auth.orgId,
        auth.role
      )
    )
    .traceRight: chunks =>
      val chunksInfo = chunks
        .map(chunk => s"${chunk.chunk.documentName} (${chunk.distance})")
        .mkString(", ")
      s"result chunks: $chunksInfo"
    .mapRight(_.map(_.chunk))

def mapExtractToUploadError(e: ExtractTextError) =
  (FileParseError(e.format, e.message))

def extractText(
    name: String,
    body: Array[Byte],
    format: Option[FileFormat] = None
): IO[Either[ExtractTextError, String]] = traceMappable("extractText"):
  println("file name: " + name)

  val currentFormat = (format, FileFormat.ofFilename(name)) match
    case (Some(f), _)    => Some(f)
    case (None, Some(f)) => Some(f)
    case _               => None

  currentFormat match
    case Some(FileFormat.PDF) =>
      extractTextFromPdf(body)
    case Some(
          FileFormat.DOCX
        ) =>
      extractTextFromDocx(body).pure[IO]
    case Some(
          FileFormat.PPTX
        ) =>
      extractTextFromPptx(body).pure[IO]
    case Some(FileFormat.Plaintext) => IO.pure(Right(String(body)))
    case Some(other) =>
      Left(
        ExtractTextError(
          other,
          "Unknown file format"
        )
      ).pure[IO]
    case None =>
      IO.pure(
        Left(
          ExtractTextError(
            FileFormat.Unknown(name),
            "Unknown file format"
          )
        )
      )

def extractAndCleanText(
    name: String,
    body: Array[Byte],
    format: Option[FileFormat] = None
): IO[Either[ExtractTextError, String]] =
  extractText(name, body, format).mapRight(filterNonUtf8)

type FileUpErr = List[Either[FileUploadError, String]]
type FileUpOutput = List[String]

def parseFileForPersistence(
    fileRaw: Part[File]
): IO[Either[FileUploadError, DocumentCreationPayload]] =
  val name = fileRaw.fileName.getOrElse("file")
  extractAndCleanText(
    name,
    Files.readAllBytes((fileRaw.body).toPath())
  )
    .mapLeft(mapExtractToUploadError)
    .mapRight(content =>
      DocumentCreationPayload(
        name = name,
        content = content
      )
    )

def saveUploadedDocs(auth: AuthData)(
    payloads: List[DocumentCreationPayload]
)(using
    db: DocDB[IO]
): IO[Either[FileUpErr, FileUpOutput]] =
  createDocs(auth)(payloads, Source.Upload)
    .mapRight(_.map(_.uid.toString()))
    .mapLeft(e => List(FileUploadError.DocumentCreationError(e).asLeft))

def uploadDocuments(auth: AuthData)(source: FileUpload)(using
    db: DocDB[IO]
): IO[Either[FileUpErr, FileUpOutput]] =
  limitRole(
    auth.role,
    (Left(List(Left(UnauthorizedError(authErrorMsg(Some(auth.role))))))
      .pure[IO])
  ):
    println(s"Uploading files... ${source.files.length}")
    source.files
      .map(parseFileForPersistence)
      .parSequence
      .flatMap: eithers =>
        val (errors, payloads) = eithers.partitionMap(identity)
        if (errors.nonEmpty) Left(eithers.map(_.map(_.name))).pure[IO]
        else saveUploadedDocs(auth)(payloads)

def listDocuments(auth: AuthData)(
    start: Int,
    end: Int
)(using db: DocDB[IO]): IO[Either[String, PaginatedResponse[List[Document]]]] =
  traceMappable("listDocuments"):
    println("Listing docs... ")
    db.listDocuments(auth.orgId, auth.role, start, end)

def deleteDocument(auth: AuthData)(id: String)(using
    db: DocDB[IO]
): IO[Either[String, Unit]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    traceMappable("deleteDocument"):
      println("Deleting doc... ")
      db.deleteDocument(UUID.fromString(id), auth.orgId, auth.role)

def createDoc(auth: AuthData)(
    payload: DocumentCreationPayload
)(implicit db: DocDB[IO]): IO[Either[String, String]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    createDocs(auth)(
      List(payload),
      Source.CreateButton
    )
      .mapRight(_.head.uid.toString())

def validateSize[A, B](a: List[A])(b: List[B]) =
  if a.length == b.length then Right(b)
  else
    Left(
      s"Got wrong number of embeddings (${a.length} chunks != ${b.length} embeddings)"
    )

def mapDocumentsToDB[F[_]: Monad](
    documents: List[Document],
    embeddingApi: EmbeddingAPI[F]
): F[Either[String, List[DocumentPersistencePayload]]] =
  println("Mapping documents to db... " + documents.length)

  val chunks = documents.flatMap(makeChunks)
  println("Got chunks, n: " + chunks.length)
  embeddingApi
    .embedChunks(chunks)
    .map(_.flatMap(validateSize(chunks)))
    .mapRight: embeddings =>
      println("Got embeddings: " + embeddings.length)
      val embeddingChunk =
        chunks.zip(embeddings).map(ChunkWithEmbedding.apply.tupled)
      val chunksMap =
        embeddingChunk.groupBy(_._1.documentUid)
      documents.map: document =>
        DocumentPersistencePayload(
          document,
          chunksMap.get(document.uid).get
        )

def createDocs(
    auth: AuthData
)(payload: List[DocumentCreationPayload], source: Source)(using
    db: DocDB[IO]
): IO[Either[String, List[Document]]] =
  upsertDocs(auth)(payload.map(_.toDocument(UUID.randomUUID(), source)))

def upsertDocs(auth: AuthData)(
    documents: List[Document]
)(using db: DocDB[IO]): IO[Either[String, List[Document]]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO]
  ):
    traceMappable("upsertDocs"):
      println("Upserting docs... ")
      if documents.length > 0 then
        mapDocumentsToDB(documents, getEmbeddingAPI("embaas"))
          .flatMapRight(persistencePayload =>
            println("Got persistencePayloads: " + persistencePayload.length)
            db.addDocuments(
              persistencePayload,
              auth.orgId,
              auth.role
            )
          )
      else Right(List()).pure[IO]

def ask(auth: AuthData)(payload: AskPayload)(implicit
    db: DocDB[IO]
): IO[Either[String, String]] = traceMappable("ask"):
  payload.messages.last.from match
    case MessageFrom.Bot =>
      IO.pure(Left("Last message should be from the user"))
    case MessageFrom.User =>
      val llmModel = LLMModel.Gpt35Turbo
      val llm = getLLM(llmModel)
      println("Used llm: " + llm.toString())

      llm
        .mergeMessages(payload.messages)
        .trace("prompt built by llm")
        .flatMapRight: prompt =>
          searchDoc(auth)(
            prompt,
            llm.askMaxTokens(payload.messages, payload.basedOnDocsOnly),
            llmModel |> llmToStr
          )
        .flatMapRight: chunks =>
          val answer = llm.ask(chunks, payload.basedOnDocsOnly)(payload.messages)
          val sources = chunks.map(_.documentName).distinct.mkString(", ")
          if chunks.length < 1 then answer
          else answer.mapRight(x => s"$x\n\n\n\nsources: $sources")
