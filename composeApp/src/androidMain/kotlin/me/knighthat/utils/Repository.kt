package me.knighthat.utils

object Repository {

    const val GITHUB = "https://github.com"
    const val GITHUB_API = "https://api.github.com"

    const val OWNER = "knighthat"
    const val REPO = "$OWNER/Kreate"
    const val REPO_URL = "$GITHUB/$REPO"

    const val LATEST_TAG_URL = "$REPO/releases/latest"

    const val ISSUE_TEMPLATE_PATH = "/issues"
    const val FEATURE_REQUEST_TEMPLATE_PATH = "/issues/new?assignees=&labels=feature_request&template=feature_request.yaml"
}