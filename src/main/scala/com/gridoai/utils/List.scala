package com.gridoai.utils
import com.gridoai.adapters.contextHandler.DocResponseItem

def keepTotalWordsUnderN(
    list: List[DocResponseItem],
    N: Int
): List[DocResponseItem] =
  list
    .foldLeft((0, List.empty[DocResponseItem])) {
      case ((accTotal, accList), item) =>
        val newTotal = accTotal + item.content.length
        if (newTotal <= N) (newTotal, item :: accList)
        else (accTotal, accList)
    }
    ._2
    .reverse
