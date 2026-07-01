package com.rk.utils

import com.rk.resources.drawables
import com.rk.resources.strings
import java.net.URL

enum class SourceCodeProvider(val drawableRes: Int, val viewStringRes: Int) {
    GitHub(drawables.github, strings.view_github),
    GitLab(drawables.gitlab, strings.view_gitlab),
    BitBucket(drawables.bitbucket, strings.view_bitbucket),
    Other(drawables.xml, strings.view_repo);

    companion object {
        fun fromUrl(url: String): SourceCodeProvider {
            val hostName = URL(url).host
            return when (hostName) {
                "github.com" -> GitHub
                "gitlab.com" -> GitLab
                "bitbucket.org" -> BitBucket
                else -> Other
            }
        }
    }
}
