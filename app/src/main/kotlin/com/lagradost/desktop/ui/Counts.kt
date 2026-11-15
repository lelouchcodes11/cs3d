package com.lagradost.desktop.ui

/** "1 episode", "3 episodes": a count with the word in the right number (the pages said "1 episodes", "1 results") */
fun counted(n: Int, one: String, many: String = one + "s"): String = if (n == 1) "1 $one" else "$n $many"
