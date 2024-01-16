package com.gridoai.adapters.fileStorage

import cats.effect.IO
import cats.data.EitherT

case class FileMeta(
    id: String,
    name: String,
    mimeType: String
)
case class File(meta: FileMeta, content: Array[Byte])

trait FileStorage[F[_]]:
  def listFiles(folderIds: List[String]): EitherT[F, String, List[FileMeta]]
  def downloadFiles(
      files: List[FileMeta]
  ): EitherT[F, String, List[File]]
  def isFolder(fileId: String): EitherT[F, String, Boolean]
  def fileInfo(fileIds: List[String]): EitherT[F, String, List[FileMeta]]

def getFileStorageByName(
    name: String
): String => FileStorage[IO] =
  name match
    case "gdrive" => GDriveClient.apply
    case "mocked" => _ => LocalFileStorage

def getFileStorage(
    name: String
): String => FileStorage[IO] =
  sys.env.get("USE_LOCAL_FILESTORAGE") match
    case Some("true") => getFileStorageByName("mocked")
    case _            => getFileStorageByName(name)
