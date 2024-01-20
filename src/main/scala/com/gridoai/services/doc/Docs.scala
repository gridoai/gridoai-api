package com.gridoai.services.doc

import cats.Monad
import cats.effect.IO
import cats.implicits._
import cats.syntax.all._
import cats.data.EitherT
import cats.effect.implicits.concurrentParTraverseOps
import java.nio.file.Files
import java.util.UUID
import sttp.model.Part
import java.io.File

import com.gridoai.adapters.llm._
import com.gridoai.adapters.embeddingApi._
import com.gridoai.adapters.rerankApi._
import com.gridoai.domain._
import com.gridoai.models.DocDB
import com.gridoai.utils._
import com.gridoai.endpoints._
import FileUploadError._
import com.gridoai.parsers.ExtractTextError
import com.gridoai.adapters._
import com.gridoai.parsers._
import com.gridoai.models.DocumentPersistencePayload
import com.gridoai.auth.limitRole
import com.gridoai.auth.authErrorMsg
import com.gridoai.auth.AuthData
import com.gridoai.domain.UploadStatus
import com.gridoai.adapters.notifications.NotificationService
import com.gridoai.services.notifications.notifyUploadProgress
import com.gridoai.services.notifications.notifySearchProgress

val pageSize = sys.env.getOrElse("PAGE_SIZE", "2000").toInt
val logger = org.slf4j.LoggerFactory.getLogger("com.gridoai.services.doc")

def searchDoc(
    auth: AuthData
)(payload: SearchPayload)(using
    db: DocDB[IO],
    ns: NotificationService[IO]
): EitherT[IO, String, List[Chunk]] =
  val tokenLimitPerQuery = payload.tokenLimit / payload.queries.length
  notifySearchProgress(payload.queries, auth.userId):
    for
      vecs <- getEmbeddingAPI("embaas").embedChats(payload.queries)
      nearChunks <- db
        .getNearChunks(vecs, payload.scope, 0, 1000, auth.orgId, auth.role)
        .map(_.map(_.map(_.chunk)) zip payload.queries)
      resChunks <- nearChunks
        .parTraverseN(5)(
          rerankChunks(
            getLLM(payload.llmName |> strToLLM).calculateChunkTokenQuantity,
            tokenLimitPerQuery
          )
        )
        .map(_.flatten)
    yield resChunks

def rerankChunks(
    calculateChunkTokenQuantity: Chunk => Int,
    tokenLimit: Int
)(chunks: List[Chunk], query: String): EitherT[IO, String, List[Chunk]] =
  if (chunks.isEmpty) EitherT.rightT(chunks)
  else
    getRerankAPI("cohere")
      .rerank(RerankPayload(query, chunks))
      .map: chunks =>
        mergeNewChunksToList(
          List.empty,
          calculateChunkTokenQuantity,
          tokenLimit,
          chunks
        )
      .traceRight: chunks =>
        val chunksInfo = chunks
          .map(chunk =>
            s"${chunk.chunk.documentName} ${chunk.chunk.startPos}-${chunk.chunk.endPos} (${chunk.relevance})"
          )
          .mkString("\n")
        s"query: $query\nresult chunks: $chunksInfo"
      .map(_.map(_.chunk))

def mapExtractToUploadError(e: ExtractTextError): FileUploadError =
  (FileParseError(e.format, e.message))

def extractText(
    name: String,
    body: Array[Byte],
    format: Option[FileFormat] = None
): EitherT[IO, ExtractTextError, String] = traceMappable("extractText"):
  logger.info(s"Extracting text ${name}, ${format}")

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
      extractTextFromDocx(body).pure[IO].asEitherT
    case Some(
          FileFormat.PPTX
        ) =>
      extractTextFromPptx(body).pure[IO].asEitherT
    case Some(FileFormat.Plaintext) => EitherT.rightT(String(body))
    case Some(other) =>
      EitherT.leftT(
        ExtractTextError(
          other,
          "Unknown file format"
        )
      )
    case None =>
      EitherT.leftT(
        ExtractTextError(
          FileFormat.Unknown(name),
          "Unknown file format"
        )
      )

def extractAndCleanText(
    name: String,
    body: Array[Byte],
    format: Option[FileFormat] = None
): EitherT[IO, ExtractTextError, String] =
  extractText(name, body, format).map(filterNonUtf8)

type FileUpErr = List[FileUploadError]
type FileUpOutput = List[String]

def parseFileForPersistence(
    fileRaw: Part[File]
): EitherT[IO, FileUploadError, DocumentCreationPayload] =
  val name = fileRaw.fileName.getOrElse("file")
  extractAndCleanText(
    name,
    Files.readAllBytes((fileRaw.body).toPath())
  )
    .map(content =>
      DocumentCreationPayload(
        name = name,
        content = content
      )
    )
    .leftMap(mapExtractToUploadError)

def saveUploadedDocs(auth: AuthData)(
    payloads: List[DocumentCreationPayload]
)(using
    db: DocDB[IO]
): EitherT[IO, FileUpErr, FileUpOutput] =
  createDocs(auth)(payloads, Source.Upload)
    .map(_.map(_.uid.toString))
    .leftMap(e => List(FileUploadError.DocumentCreationError(e)))

def uploadDocuments(auth: AuthData)(source: FileUpload)(using
    db: DocDB[IO],
    ns: NotificationService[IO]
): EitherT[IO, FileUpErr, Unit] =
  limitRole(
    auth.role,
    Left(List(UnauthorizedError(authErrorMsg(auth.role.some))))
      .pure[IO]
      .asEitherT
  ):
    logger.info(s"Uploading files... ${source.files.length}")

    notifyUploadProgress(auth.userId):
      source.files
        .map(parseFileForPersistence)
        .partitionEitherTs
        .flatMap(saveUploadedDocs(auth))

def listDocuments(auth: AuthData)(
    start: Int,
    end: Int,
    truncate: Boolean
)(using db: DocDB[IO]): EitherT[IO, String, PaginatedResponse[List[Document]]] =
  traceMappable("listDocuments"):
    logger.info(s"Listing docs... truncate: ${truncate}")
    val docs = db.listDocuments(auth.orgId, auth.role, start, end)
    if truncate then
      docs.map: r =>
        r.copy(data =
          r.data.map(doc => doc.copy(content = doc.content.slice(0, 100)))
        )
    else docs

def deleteDocument(auth: AuthData)(id: String)(using
    db: DocDB[IO]
): EitherT[IO, String, Unit] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO].asEitherT
  ):
    traceMappable("deleteDocument"):
      logger.info("Deleting doc... ")
      db.deleteDocument(UUID.fromString(id), auth.orgId, auth.role)

def createDoc(auth: AuthData)(
    payload: DocumentCreationPayload
)(implicit db: DocDB[IO]): EitherT[IO, String, String] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO].asEitherT
  ):
    createDocs(auth)(
      List(payload),
      Source.CreateButton
    )
      .map(_.head.uid.toString())

def validateSize[A, B](a: List[A])(b: List[B]): Either[String, List[B]] =
  if a.length == b.length then Right(b)
  else
    Left(
      s"Got wrong number of embeddings (${a.length} chunks != ${b.length} embeddings)"
    )

def mapDocumentsToDB[F[_]: Monad](
    documents: List[Document],
    embeddingApi: EmbeddingAPI[F]
): EitherT[F, String, List[DocumentPersistencePayload]] =
  logger.info("Mapping documents to db... " + documents.length)

  val chunks = documents.flatMap(makeChunks)
  logger.info("Got chunks, n: " + chunks.length)
  embeddingApi
    .embedChunks(chunks)
    .subflatMap(validateSize(chunks))
    .map: embeddings =>
      logger.info("Got embeddings: " + embeddings.length)
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
): EitherT[IO, String, List[Document]] =
  upsertDocs(auth)(payload.map(_.toDocument(UUID.randomUUID(), source)))

def createOrUpdateFiles(auth: AuthData)(
    filesToUpload: List[Document]
)(using db: DocDB[IO]): EitherT[IO, String, List[Document]] =
  logger.info(s"Creating or updating ${filesToUpload.length} files...")
  db.listDocumentsBySource(filesToUpload.map(_.source), auth.orgId, auth.role)
    .map: filesToUpdate =>
      filesToUpload.map(fileToUpload =>
        filesToUpdate.find(_.source == fileToUpload.source) match
          case Some(fileToUpdate) =>
            Document(
              uid = fileToUpdate.uid,
              name = fileToUpload.name,
              source = fileToUpload.source,
              content = fileToUpload.content
            )
          case None => fileToUpload
      )
    .flatMap(upsertDocs(auth))

def upsertDocs(auth: AuthData)(
    documents: List[Document]
)(using db: DocDB[IO]): EitherT[IO, String, List[Document]] =
  limitRole(
    auth.role,
    Left(authErrorMsg(Some(auth.role))).pure[IO].asEitherT
  ):
    traceMappable("upsertDocs"):
      logger.info("Upserting docs... ")
      if documents.length > 0 then
        mapDocumentsToDB(documents, getEmbeddingAPI("embaas"))
          .flatMap(persistencePayload =>
            logger.info("Got persistencePayloads: " + persistencePayload.length)
            db.addDocuments(
              persistencePayload,
              auth.orgId,
              auth.role
            )
          )
      else EitherT.rightT(List.empty[Document])
