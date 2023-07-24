package com.gridoai.adapters.fileStorage

import cats.effect.IO

trait FileStorage[F[_]]:
  def listContent(path: String): IO[Either[String, List[String]]]
  def downloadFiles(files: List[String]): IO[Either[String, List[Array[Byte]]]]

def getFileStorageByName(name: String): String => FileStorage[IO] =
  name match
    case "gdrive" => GDriveClient.apply
    case "mocked" => _ => LocalFileStorage

def getFileStorage(name: String): String => FileStorage[IO] =
  sys.env.get("USE_LOCAL_FILESTORAGE") match
    case Some("1") => _ => LocalFileStorage
    case _         => getFileStorageByName(name)
