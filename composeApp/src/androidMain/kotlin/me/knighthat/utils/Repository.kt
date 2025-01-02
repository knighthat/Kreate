package me.knighthat.utils

object Repository {

    const val GITHUB = "https://github.com"
    const val GITHUB_API = "https://api.github.com"

    const val OWNER_PATH = "/knighthat"
    const val OWNER_URL = "$GITHUB$OWNER_PATH"

    const val REPO_PATH = "$OWNER_PATH/RiMusic"
    const val REPO_URL = "$GITHUB$REPO_PATH"

    const val TAG_PATH = "$REPO_PATH/releases/tag/weekly-kbuild"
    const val API_TAG_PATH = "/repos$REPO_PATH/releases/tags/weekly-kbuild"
}