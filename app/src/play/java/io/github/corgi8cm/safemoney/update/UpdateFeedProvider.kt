package io.github.corgi8cm.safemoney.update

/**
 * play 版：Google Play 規定 App 不能引導到 Play 以外的地方更新，所以不檢查 GitHub。
 * 之後上架時改用 Play 的「應用程式內更新」。
 */
object UpdateFeedProvider {
    val feed: ReleaseFeed? = null
}
