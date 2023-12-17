package com.gridoai.services.doc

import java.util.UUID
import cats.effect.IO
import cats.implicits._

import com.gridoai.domain.*
import com.gridoai.utils.*
import com.gridoai.adapters.embeddingApi.*
import com.gridoai.models.DocDB

def calculateChunkStarts(
    wordQuantity: Int,
    chunkSize: Int,
    overlapSize: Int
): List[Int] =
  println(s"wordQuantity: $wordQuantity")
  println(s"chunkSize: $chunkSize")
  println(s"overlapSize: $overlapSize")
  List
    .range(
      0,
      (wordQuantity - overlapSize).max(0) + 1,
      chunkSize - overlapSize
    )
    .traceFn: output =>
      s"output: $output"

def chunkContent(
    content: String,
    chunkSize: Int,
    overlapSize: Int
): List[(String, Int, Int)] =
  val words = content.split(" ")
  calculateChunkStarts(words.length, chunkSize, overlapSize)
    .map(i => (words.slice(i, i + chunkSize).mkString(" "), i, i + chunkSize))

def makeChunks(document: Document): List[Chunk] =
  chunkContent(document.content, 200, 100)
    .map((content, startPos, endPos) =>
      Chunk(
        documentUid = document.uid,
        documentName = document.name,
        documentSource = document.source,
        uid = UUID.randomUUID(),
        content = content,
        tokenQuantity = content.filter(_ != ' ').length / 4,
        startPos = startPos,
        endPos = endPos
      )
    )
    .traceFn: chunks =>
      val uids = chunks.map(_.uid).mkString(", ")
      s"generated chunks: $uids"

def mergeNewChunksToList(
    chunks: List[RelevantChunk],
    calculateChunkTokenQuantity: Chunk => Int,
    tokenLimit: Int,
    newChunks: List[RelevantChunk]
): List[RelevantChunk] =
  if newChunks.length > 0 then
    val (newChunk :: remainingChunks) = newChunks
    val chunkWithNewChunk = mergeNewChunkToList(chunks, newChunk)
    val newTotalTokens = chunkWithNewChunk
      .map(_.chunk)
      .map(calculateChunkTokenQuantity)
      .sum
    if newTotalTokens == tokenLimit then chunkWithNewChunk
    else if newTotalTokens > tokenLimit then chunks
    else
      mergeNewChunksToList(
        chunkWithNewChunk,
        calculateChunkTokenQuantity,
        tokenLimit,
        remainingChunks
      )
  else chunks

def mergeNewChunkToList(
    chunks: List[RelevantChunk],
    newChunk: RelevantChunk
): List[RelevantChunk] =

  val (nearChunks, notNearChunks) = chunks.partition: c =>
    c.chunk.documentUid == newChunk.chunk.documentUid
      && c.chunk.startPos < newChunk.chunk.endPos
      && c.chunk.endPos > newChunk.chunk.startPos

  (newChunk :: nearChunks).sortBy(_.chunk.startPos) match
    case List(singleChunk) => chunks :+ newChunk
    case chunksToMerge @ List(firstChunk, secondChunk) =>
      RelevantChunk(
        chunk = mergeTwoChunks(firstChunk.chunk, secondChunk.chunk),
        relevance = chunksToMerge.map(_.relevance).max
      ) :: notNearChunks
    case chunksToMerge @ List(firstChunk, secondChunk, thirdChunk) =>
      RelevantChunk(
        chunk = mergeTwoChunks(
          mergeTwoChunks(firstChunk.chunk, secondChunk.chunk),
          thirdChunk.chunk
        ),
        relevance = chunksToMerge.map(_.relevance).max
      ) :: notNearChunks

def mergeTwoChunks(firstChunk: Chunk, secondChunk: Chunk): Chunk =
  val allWords = firstChunk.content
    .split(" ")
    .slice(
      0,
      secondChunk.startPos - firstChunk.startPos
    ) ++ secondChunk.content.split(" ")
  Chunk(
    documentUid = firstChunk.documentUid,
    documentName = firstChunk.documentName,
    documentSource = firstChunk.documentSource,
    uid = UUID.randomUUID(),
    content = allWords.mkString(" "),
    tokenQuantity = 0,
    startPos = firstChunk.startPos,
    endPos = secondChunk.endPos
  )
