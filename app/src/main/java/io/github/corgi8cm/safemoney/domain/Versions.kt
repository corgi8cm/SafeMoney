package io.github.corgi8cm.safemoney.domain

/**
 * 語意化版本：「v1.2.3」「1.2.3」「0.8.0-debug」都可以，後綴（-debug、-rc1）不影響比較。
 * 無法解析時回傳 null。
 */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:[-+].*)?$""")

        fun parse(text: String): Version? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = m.destructured
            return Version(major.toInt(), minor.toInt(), patch.ifEmpty { "0" }.toInt())
        }
    }
}

/** [latest] 是否比 [current] 新；任一個無法解析就當作沒有更新。 */
fun isNewerVersion(latest: String, current: String): Boolean {
    val l = Version.parse(latest) ?: return false
    val c = Version.parse(current) ?: return false
    return l > c
}
