package com.gridoai.adapters.rerankApi.mocked

import cats.Applicative

import scala.util.Random
import com.gridoai.adapters.rerankApi.RerankAPI
import com.gridoai.adapters.rerankApi.RerankPayload
import com.gridoai.domain.RelevantChunk
import cats.Id
import cats.effect.kernel.Sync

// Assuming the definitions of UID, Source, Chunk, RelevantChunk, RerankPayload are as provided

class MockRerankAPI[F[_]: Sync] extends RerankAPI[F] {

  override def rerank(
      payload: RerankPayload
  ): F[Either[String, List[RelevantChunk]]] = Sync[F].blocking {
    Thread.sleep(500)
    // Mock logic to simulate reranking
    val rerankedChunks = payload.chunks.map { chunk =>
      RelevantChunk(chunk, Random.nextFloat())
    }

    // Return a successful result
    (Right(rerankedChunks))
  }
}
