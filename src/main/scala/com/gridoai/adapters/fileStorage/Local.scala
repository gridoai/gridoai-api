package com.gridoai.adapters.fileStorage

import java.nio.file.Files
import java.nio.file.Paths
import cats.effect.IO
import cats.implicits._
import cats.data.EitherT
import scala.jdk.CollectionConverters._

import com.gridoai.utils._

object LocalFileStorage extends FileStorage[IO] {

  def listFiles(paths: List[String]): EitherT[IO, String, List[FileMeta]] =
    paths
      .traverse(path =>
        val dirPath = Paths.get(path)
        if (Files.isDirectory(dirPath))
          val directoryStream = Files.newDirectoryStream(dirPath)
          val filePaths = directoryStream.asScala.toList
          val fileNames = filePaths.map(_.getFileName.toString)
          Right(fileNames.map(file => FileMeta(file, file, file)))
        else Left(s"The path $path is not a directory.")
      )
      .map(_.flatten)
      .pure[IO]
      .asEitherT

  def downloadFiles(
      files: List[FileMeta]
  ): EitherT[IO, String, List[File]] =
    IO:
      val contents = files.map: file =>
        val path = Paths.get(file.id)
        if (Files.isReadable(path))
          val bytes = Files.readAllBytes(path)
          Some(File(meta = file, content = bytes))
        else None
      if (contents.contains(None))
        Left(s"One or more files could not be read.")
      else
        Right(contents.flatten)
    .asEitherT

  def isFolder(fileId: String): EitherT[IO, String, Boolean] =
    IO:
      val path = Paths.get(fileId)
      if (Files.exists(path))
        Right(Files.isDirectory(path))
      else
        Left(s"The path $fileId does not exist.")
    .asEitherT

  def fileInfo(fileIds: List[String]): EitherT[IO, String, List[FileMeta]] =
    EitherT.rightT(fileIds.map(fileId => FileMeta(fileId, fileId, fileId)))
}
