package com.gridoai.services

case class PaginatedResponse[T](data: T, total: Int)
