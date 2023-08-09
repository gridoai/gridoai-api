package com.gridoai.adapters.fileStorage

import java.nio.file.{Files, Paths}
import cats.effect.{IO}
import scala.jdk.CollectionConverters._

object LocalFileStorage extends FileStorage[IO] {

  def listFiles(path: String): IO[Either[String, List[FileMeta]]] =
    IO:
      val dirPath = Paths.get(path)
      if (Files.isDirectory(dirPath))
        val directoryStream = Files.newDirectoryStream(dirPath)
        val filePaths = directoryStream.asScala.toList
        val fileNames = filePaths.map(_.getFileName.toString)
        Right(fileNames.map(file => FileMeta(file, file, file)))
      else Left(s"The path $path is not a directory.")

  def listFolders(path: String): IO[Either[String, List[FileMeta]]] =
    IO:
      val dirPath = Paths.get(path)
      if (Files.isDirectory(dirPath))
        val directoryStream = Files.newDirectoryStream(dirPath)
        val filePaths = directoryStream.asScala.toList
        val fileNames = filePaths.map(_.getFileName.toString)
        Right(fileNames.map(file => FileMeta(file, file, file)))
      else Left(s"The path $path is not a directory.")

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
}
