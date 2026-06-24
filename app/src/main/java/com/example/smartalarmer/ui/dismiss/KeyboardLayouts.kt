package com.example.smartalarmer.ui.dismiss

object KeyboardLayouts {
    fun getLayoutForLanguage(language: String): List<List<Char>> {
        return when (language) {
            "ru" -> listOf(
                listOf('й', 'ц', 'у', 'к', 'е', 'н', 'г', 'ш', 'щ', 'з', 'х', 'ъ'),
                listOf('ф', 'ы', 'в', 'а', 'п', 'р', 'о', 'л', 'д', 'ж', 'э'),
                listOf('я', 'ч', 'с', 'м', 'и', 'т', 'ь', 'б', 'ю', '.', '!')
            )
            "de" -> listOf(
                listOf('q', 'w', 'e', 'r', 't', 'z', 'u', 'i', 'o', 'p', 'ü'),
                listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ö', 'ä'),
                listOf('y', 'x', 'c', 'v', 'b', 'n', 'm', 'ß', '.', '!')
            )
            "es" -> listOf(
                listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
                listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ñ'),
                listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', '.', '!')
            )
            else -> listOf(
                listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
                listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
                listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', '.', '!')
            )
        }
    }
}
