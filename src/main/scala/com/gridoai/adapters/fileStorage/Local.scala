package com.gridoai.adapters.fileStorage

import java.nio.file.{Files, Paths}
import cats.effect.IO
import cats.implicits.*
import scala.jdk.CollectionConverters._

object LocalFileStorage extends FileStorage[IO] {

  def listFiles(paths: List[String]): IO[Either[String, List[FileMeta]]] =
    IO:
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

  def downloadFiles(
      files: List[FileMeta]
  ): IO[Either[String, List[File]]] =
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

  def isFolder(fileId: String): IO[Either[String, Boolean]] =
    IO:
      val path = Paths.get(fileId)
      if (Files.exists(path))
        Right(Files.isDirectory(path))
      else
        Left(s"The path $fileId does not exist.")

  def fileInfo(fileIds: List[String]): IO[Either[String, List[FileMeta]]] =
    Right(fileIds.map(fileId => FileMeta(fileId, fileId, fileId))).pure[IO]
}
