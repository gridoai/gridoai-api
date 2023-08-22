package com.gridoai.adapters.llm.chatGPT

def baseContextPrompt(basedOnDocsOnly: Boolean = true) =
  basedOnDocsOnly match
    case true =>
      "You're GridoAI, a smart and reliable chatbot that responds contextually. After searching the user's documents for their query, provide a single, intelligent response. Only answer based on the document's information and refuse to answer questions requiring external data."
    case false =>
      "You're GridoAI, a smart and reliable chatbot that responds contextually. After searching the user's documents for their query, provide a single, intelligent response. You can use information that is not in the documents, but make it clear that you are doing so."

val buildQueryToSearchDocumentsPrompt: String =
  "You're a smart and reliable chatbot that builds queries to search for documents to help answer the user's question. The output MUST BE only the query, nothing more."
