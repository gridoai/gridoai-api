package com.gridoai.utils

def keepTotalWordsUnderN(
    list: List[(String, Float)],
    N: Int
): List[(String, Float)] =
  list
    .foldLeft((0, List.empty[(String, Float)])) {
      case ((accTotal, accList), (word, score)) =>
        val newTotal = accTotal + word.length
        if (newTotal <= N) (newTotal, (word, score) :: accList)
        else (accTotal, accList)
    }
    ._2
    .reverse
