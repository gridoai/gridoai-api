package com.gridoai.adapters.fileStorage

import cats.effect.IO

trait FileStorage[F[_]]:
  def listContent(path: String): List[String]
  def downloadFiles(files: List[String]): List[Array[Byte]]

def getFileStorageByName(name: String): FileStorage[IO] =
  name match
    case "gdrive" => GDriveClient
    case "mocked" => MockedFileStorage

def getFileStorage(name: String): FileStorage[IO] =
  sys.env.get("USE_MOCKED_FILESTORAGE") match
    case Some("1") => MockedFileStorage
    case _         => getFileStorageByName(name)
