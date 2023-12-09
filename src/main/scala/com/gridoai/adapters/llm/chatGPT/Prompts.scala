package com.gridoai.adapters.llm.chatGPT

import com.gridoai.domain.Chunk
import com.gridoai.domain.Message
import com.gridoai.domain.Action

def baseContextPrompt(
    basedOnDocsOnly: Boolean,
    shouldAskUser: Boolean,
    searchedBefore: Boolean
): String =
  ((basedOnDocsOnly, shouldAskUser, searchedBefore) match
    case (true, false, true) =>
      """You're GridoAI, a smart and reliable chatbot that responds contextually.
      |You just got the top semantic-related user's documents chunks for their query.
      |Provide a single, intelligent response. Only answer based on the document's
      |information and refuse to answer questions requiring external data."""
    case (false, false, true) =>
      """You're GridoAI, a smart and reliable chatbot that responds contextually.
      |You just got the top semantic-related user's documents chunks for their query.
      |Provide a single, intelligent response. You can use information that is not
      |in the documents, but make it clear that you are doing so."""
    case (true, true, true) | (false, true, true) =>
      """You're GridoAI, a smart and reliable chatbot that asks contextually.
      |You just got the top semantic-related user's documents chunks for their query.
      |Provide a single, intelligent question to improve your understanding about
      |the user's question. Don't ask for information you should know as a chatbot
      |with access to user documents."""
    case (true, false, false) =>
      """You're GridoAI, a smart and reliable chatbot that responds contextually.
      |You have access to the user's documents but you decided to not search in it.
      |Provide a single, intelligent response. Only answer based on the document's
      |information and refuse to answer questions requiring external data."""
    case (false, false, false) =>
      """You're GridoAI, a smart and reliable chatbot that responds contextually.
      |You have access to the user's documents but you decided to not search in it.
      |Provide a single, intelligent response. You can use information that is not
      |in the documents, but make it clear that you are doing so."""
    case (true, true, false) | (false, true, false) =>
      """You're GridoAI, a smart and reliable chatbot that asks contextually.
      |You have access to the user's documents but you decided to not search in it.
      |Provide a single, intelligent question to improve your understanding about
      |the user's question. Don't ask for information you should know as a chatbot
      |with access to user documents."""
  ).stripMargin.replace("\n", " ")

def mergeMessages(messages: List[Message]): String =
  messages.map(m => s"${m.from}: ${m.message}").mkString("\n")

def chunkToStr(chunk: Chunk): String =
  s"""chunk retrieved from: ${chunk.documentName}
  |position of the first chunk word in original document: ${chunk.startPos}
  |position of the last chunk word in the original document: ${chunk.endPos}
  |chunk content: ${chunk.content}
  |
  |""".stripMargin

def mergeChunks(chunks: List[Chunk]): String =
  if chunks.length > 0 then
    val mergedChunks = chunks
      .map(chunkToStr)
      .mkString("\n")
    s"Retrieved user's doc chunks:\n$mergedChunks"
  else ""

def buildQueryToSearchDocumentsPrompt(
    messages: List[Message],
    lastQuery: Option[String],
    lastChunks: List[Chunk]
): String =

  val instruction = lastQuery match
    case None =>
      """You're a smart and reliable chatbot that builds natural language queries
      |to search information to help answer the user's question. The query will be
      |used for a semantic search in the user's documents chunks using embeddings
      |by multilingual-e5-base. The output MUST BE only the query, nothing more.""".stripMargin
        .replace("\n", " ")
    case Some(query) =>
      val part1 =
        s"""You're a smart and reliable chatbot that builds natural language queries
      |to search information to help answer the user's question. Your last query
      |was""".stripMargin.replace("\n", " ")
      val part2 =
        """and the output documents chunks weren't helpful. The new query will be used
        |for a semantic search in the user's documents chunks using embeddings by
        |multilingual-e5-base. The output MUST BE only the new query, nothing more.
        |The new query MUST BE different from the last query.""".stripMargin
          .replace("\n", " ")
      s"$part1\n\n$query\n\n$part2"
  s"$instruction\n${mergeChunks(lastChunks)}\n${mergeMessages(messages)}\nQuery: "

def optionPrompt(anyChunk: Boolean)(action: Action): String =
  action match
    case Action.Search =>
      anyChunk match
        case false =>
          """Search (Choose this action if you believe the user's documents can
          |give you the necessary information to answer the question or if you need
          |more context. Searching is always a good idea.)""".stripMargin
            .replace("\n", " ")
        case true =>
          """Search again (Choose this action if the documents already searched
          |don't provide the necessary information you need to answer the question,
          |or if you need a different context)""".stripMargin
            .replace("\n", " ")
    case Action.Answer =>
      "Answer (Choose this action if you already have enough information to answer the user)"
    case Action.Ask =>
      "Ask (Choose this action if you believe you need the user to clarify the issue for you)"

def chooseActionPrompt(
    chunks: List[Chunk],
    messages: List[Message],
    options: List[Action]
): String =

  val chunksSection = mergeChunks(chunks)

  val header =
    """You're a smart and reliable chatbot that chooses an action to help answer the
    |user's question. \"My documents\" is what the user will call all the documents chunks
    |you have access to.""".stripMargin.replace("\n", " ")

  val actions = options
    .map(optionPrompt(chunks.length > 0))
    .zipWithIndex
    .map: (str, idx) =>
      s"${idx + 1}) $str"
    .mkString("\n")

  val actionsSection = s"Available actions:\n$actions"

  val numbers =
    s"${(1 to (options.length - 1)).toList.mkString(", ")}, or ${options.length}"
  val footer =
    s"The output MUST BE only the action number ($numbers), nothing more."

  val mergedMessages = mergeMessages(messages)

  val field = "Action number: "

  s"$chunksSection\n$header\n$actionsSection\n$footer\n$mergedMessages\n$field"
