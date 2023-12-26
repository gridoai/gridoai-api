package com.gridoai.adapters.llm.chatGPT

import com.gridoai.domain.*

def baseContextPrompt(
    basedOnDocsOnly: Boolean,
    shouldAskUser: Boolean,
    searchedBefore: Boolean
): String =
  val impersonation =
    """You're GridoAI, a smart and reliable chatbot. You can answer specific
    |questions, do summarization, translate content and so on, using both the
    |information you've been trained on and the documents available to you.""".stripMargin
  val answerInstruction =
    """Please use the information provided in the documents to respond to the
    |user's query in a factual and objective style. You have full access to the
    |content of these documents so don't ask the content to the user.""".stripMargin

  val askInstruction =
    """You are asking the user to improve your understanding about
    |the user's question. Don't ask for information you should know as a chatbot
    |with access to user's documents.""".stripMargin

  val externalInfoUsageInstruction =
    if (basedOnDocsOnly)
      """Please use only the information provided in the documents to answer the user's query.
      |Avoid using any external knowledge or information.""".stripMargin
    else
      """You may use external knowledge and information, but prioritize the
      |information provided in the documents. If you rely on external
      |information, please inform the user.""".stripMargin

  if (shouldAskUser)
    s"$impersonation $askInstruction"
  else
    s"$impersonation $answerInstruction $externalInfoUsageInstruction"

def mergeMessages(messages: List[Message]): String =
  messages.map(m => s"${m.from}: ${m.message}").mkString("\n")

def chunkToStr(chunk: Chunk): String =
  s"Chunk ${chunk.startPos}-${chunk.endPos} (${chunk.documentName}): ${chunk.content}\n\n"

def mergeChunks(chunks: List[Chunk]): String =
  if chunks.length > 0 then
    val mergedChunks = chunks
      .map(chunkToStr)
      .mkString("\n")
    s"**Retrieved documents:**\n$mergedChunks"
  else ""

def buildQueriesToSearchDocumentsPrompt(
    messages: List[Message],
    lastQueries: List[String],
    lastChunks: List[Chunk]
): String =

  val impersonation =
    """You're a smart and reliable agent that builds natural language queries
    |to search information to help another agent to answer the user's question.""".stripMargin

  val queryOrNewQuery = lastQueries.match
    case List() => "queries"
    case _      => "new queries"

  val instruction =
    s"""The $queryOrNewQuery will be used for semantic searches in the user's documents
    | chunks using text embeddings by multilingual-e5-base.
    | The output MUST BE only the $queryOrNewQuery, nothing more.
    | Do NOT answer the user's question, only build the $queryOrNewQuery.
    | You can write multiple queries if necessary but only one per line.
    | All queries MUST BE about different subjects.
    | Identify ALL unknown entities (like names of people, companies, things, places, etc)
    | and make a query like "What is X?", "How X works?", or "Who is X?"
    | for each of them to know more about.
    | Less queries is better so you are limited to 3 queries.""".stripMargin
      .replace("\n", "")

  val additionalInstruction = lastQueries match
    case List() => ""
    case queries =>
      s"""Your last queries was
      |
      |${queries.mkString("\n")}
      |
      |and the output documents chunks weren't helpful.
      |The new queries MUST BE different from the last queries.""".stripMargin

  s"**$impersonation $instruction $additionalInstruction**"

val buildQueriesExample = List(
  Message(
    from = MessageFrom.User,
    message = "What did Davi say at the last meeting?"
  ),
  Message(
    from = MessageFrom.Bot,
    message = """Who is Davi?
      |What was the last meeting involving Davi about?""".stripMargin
  ),
  Message(
    from = MessageFrom.User,
    message = "Compare Mike and John's CVs."
  ),
  Message(
    from = MessageFrom.Bot,
    message = """Mike's CV
      |John's CV""".stripMargin
  ),
  Message(
    from = MessageFrom.User,
    message = "Summarize my presentation about AI."
  ),
  Message(
    from = MessageFrom.Bot,
    message = "Presentation about AI."
  )
)

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
