package com.maxrave.simpmusic.update

// iOS has no APK sideloading: updates ship through the App Store / TestFlight.
// Callers offer "open releases page" on this platform instead.
actual fun downloadUpdateApk(
    url: String,
    fileName: String,
) = Unit
