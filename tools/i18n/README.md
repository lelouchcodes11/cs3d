# Translations of the desktop screens

The Fluent screens are written in English in the code. `FText` (the one place all their text passes through) looks the English text up
in `app/src/main/resources/i18n/strings.tsv` and shows the translation of the app language (Settings > General > App language).
Android's own strings (`app/src/main/android-res/values-b+xx/strings.xml`) are separate and work as before.

## The table

`strings.tsv`: the first line is `en`, a tab and the language codes (`en  ar  tr`); every other line is the English text and its translation in
each language, separated by tabs. An empty cell = not translated yet (English is shown). `#` starts a comment. `\n`, `\t`, `\` are escapes.

* `{0}`, `{1}` are pieces cut out of the text: `Search {0} sources` matches "Search 12 sources". The pieces are looked up on their own.
  A key with hardly any words of its own (`E{0}`, `{0} ep`) only takes numbers.
* Plural forms: `{0,plural,one{# başlık}other{# başlık}}` (categories zero, one, two, few, many, other; Arabic uses all six).
* A line of several parts (`a · b`, `a • b`, `a - b`, `label: text`, one per line) is translated part by part when the whole is not in the table.
* Add a language: add a column (and `Tr.rtlLanguages` if it is written right to left). Arabic and Turkish are complete.
* Add a text: add its English wording as a new row. A text that is made of pieces in the code ("episode" + "s") has to be written
  as whole sentences for the table to translate it: use the plural forms or two rows ("{0} episode", "{0} episodes").

## Finding what is missing

Run the app in a language, open the pages, then ask the dev server for the texts that were shown as they are:
`http://127.0.0.1:<devport>/i18n` (add `?lang=tr` to switch the language first, `?res=<android string name>` to read an Android string).
`java tools/i18n/Extract.java app/src/main/kotlin/com/lagradost/desktop out.tsv` lists every text literal of the code (with `{0}` for
`${...}`), to compare with the table. `app/src/test/.../TrTest.kt` checks the table (same pieces in every language, no empty cell, no duplicate).
