package com.maxrave.simpmusic.update

// Desktop has no APK installer: callers offer "open releases page"
// on this platform instead.
actual fun downloadUpdateApk(
    url: String,
    fileName: String,
) = Unit
