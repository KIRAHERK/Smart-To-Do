package io.github.kiraherk.smarttodo

/** Who made Thakkirni. Shown in Settings → About, the guide and the README. */
object Credits {
    const val AUTHOR = "Hussein Al Herk"
    const val GITHUB_USER = "KIRAHERK"

    /** The always-newest version, for sharing the app. */
    const val DOWNLOAD_PAGE = "https://kiraherk.github.io/Thakkirni/"

    /** Label to URL. Links left blank are not shown. */
    val links: List<Pair<String, String>> = listOf(
        "GitHub" to "https://github.com/KIRAHERK",
        "LinkedIn" to "https://www.linkedin.com/in/husseinalherk",
        "Portfolio" to "https://kiraherk.github.io",
    ).filter { it.second.isNotBlank() }
}
