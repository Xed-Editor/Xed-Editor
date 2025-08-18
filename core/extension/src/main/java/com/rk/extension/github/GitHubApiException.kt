package com.rk.extension.github

class GitHubApiException(
    message: String, val statusCode: Int, val response: String
) : Exception(message)
