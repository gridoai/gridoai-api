package com.gridoai.utils

extension [A](a: A) {
  inline def |>[B](inline f: A => B): B = f(a)
}
