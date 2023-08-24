package com.gridoai.adapters.llm.chatGPT

import com.gridoai.domain.Chunk
import com.gridoai.domain.Message

def baseContextPrompt(
    basedOnDocsOnly: Boolean,
    shouldAskUser: Boolean,
    searchedBefore: Boolean
): String =
  (basedOnDocsOnly, shouldAskUser, searchedBefore) match
    case (true, false, true) =>
      "You're GridoAI, a smart and reliable chatbot that responds contextually. After searching the user's documents for their query, provide a single, intelligent response. Only answer based on the document's information and refuse to answer questions requiring external data."
    case (false, false, true) =>
      "You're GridoAI, a smart and reliable chatbot that responds contextually. After searching the user's documents for their query, provide a single, intelligent response. You can use information that is not in the documents, but make it clear that you are doing so."
    case (true, true, true) | (false, true, true) =>
      "You're GridoAI, a smart and reliable chatbot that asks contextually. After searching the user's documents for their query, provide a single, intelligent question to improve your understanding about the user's question. Don't ask for information you should know as a chatbot with access to user documents."
    case (true, false, false) =>
      "You're GridoAI, a smart and reliable chatbot that responds contextually. You have access to the user's documents but you decided to not search in it. Provide a single, intelligent response. Only answer based on the document's information and refuse to answer questions requiring external data."
    case (false, false, false) =>
      "You're GridoAI, a smart and reliable chatbot that responds contextually. You have access to the user's documents but you decided to not search in it. Provide a single, intelligent response. You can use information that is not in the documents, but make it clear that you are doing so."
    case (true, true, false) | (false, true, false) =>
      "You're GridoAI, a smart and reliable chatbot that asks contextually. You have access to the user's documents but you decided to not search in it. Provide a single, intelligent question to improve your understanding about the user's question. Don't ask for information you should know as a chatbot with access to user documents."

def mergeMessages(messages: List[Message]): String =
  messages.map(m => s"${m.from}: ${m.message}").mkString("\n")

def mergeChunks(chunks: List[Chunk]): String =
  if chunks.length > 0 then
    val mergedChunks = chunks
      .map(chunk =>
        s"name: ${chunk.documentName}\ncontent: ${chunk.content}\n\n"
      )
      .mkString("\n")
    s"Retrieved user's documents chunks:\n$mergedChunks"
  else ""

def buildQueryToSearchDocumentsPrompt(
    messages: List[Message],
    lastQuery: Option[String],
    lastChunks: List[Chunk]
): String =

  val instruction = lastQuery match
    case None =>
      "You're a smart and reliable chatbot that builds natural language queries to search information to help answer the user's question. The query will be used for a semantic search in the user's documents chunks using embeddings by multilingual-e5-base. The output MUST BE only the query, nothing more."
    case Some(query) =>
      s"You're a smart and reliable chatbot that builds natural language queries to search information to help answer the user's question. Your last query was \n$query\n and the output documents weren't that helpful. The new query will be used for a semantic search in the user's documents chunks using embeddings by multilingual-e5-base. The output MUST BE only the new query, nothing more."
  s"$instruction\n${mergeChunks(lastChunks)}\n${mergeMessages(messages)}\nQuery: "

def chooseActionPrompt(chunks: List[Chunk], messages: List[Message]): String =

  val chunksSection = mergeChunks(chunks)

  val header =
    "You're a smart and reliable chatbot that chooses an action to help answer the user's question. \"My documents\" is what the user will call all the documents you have access to."

  val actions = List(
    chunks.length > 0 match
      case false =>
        "1) Search (Choose this action if you believe the user's documents can give you the necessary information to answer the question or if you need more context. Searching is always a good idea.)"
      case true =>
        "1) Search again (Choose this action if the documents already searched don't provide the necessary information you need to answer the question, or if you need a different context)"
    ,
    "2) Answer (Choose this action if you already have enough information to answer the user)",
    "3) Ask (Choose this action if you believe you need the user to clarify the issue for you)"
  ).mkString("\n")

  val actionsSection = s"Available actions:\n$actions"

  val footer =
    "The output MUST BE only the action number (1, 2, or 3), nothing more."

  val mergedMessages = mergeMessages(messages)

  val field = "Action number: "

  s"$chunksSection\n$header\n$actionsSection\n$footer\n$mergedMessages\n$field"
