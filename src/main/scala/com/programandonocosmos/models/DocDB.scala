package com.programandonocosmos.models

import com.programandonocosmos.domain.Contains
import com.programandonocosmos.domain.Folder
import com.programandonocosmos.domain.ID
import com.programandonocosmos.domain.Page

trait DocDB[F[_]]:
  def addPage(page: Page): F[Unit]
  def addFolder(folder: Folder): F[Unit]
  def addContains(contains: Contains): F[Unit]
  def getPagesById(ids: List[ID]): F[List[Page]]
  def linkPageToFolder(relation: Contains): F[Unit]
  def addPages(pages: List[Page]): F[Unit]
  def addFolders(folders: List[Folder]): F[Unit]
  def addContainsRelations(contains: List[(Page, Folder)]): F[Unit]
