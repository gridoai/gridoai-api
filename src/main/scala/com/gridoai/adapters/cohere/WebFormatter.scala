package com.gridoai.adapters.cohere

import com.gridoai.adapters.cohere.CohereLLMResponse

def formatCohereFormatForWeb(response: CohereLLMResponse) = {
  f"""
${response.text}
<citations>
text,document_ids
${response.citations
      .map(c => s"${c.text},${c.document_ids.mkString(",")}")
      .mkString("\n")}
</citations>
<documents>
id,title,url
${response.documents
      .map(doc => s"${doc.id},${doc.title},${doc.url}")
      .mkString("\n")}
</documents>
          """
}
