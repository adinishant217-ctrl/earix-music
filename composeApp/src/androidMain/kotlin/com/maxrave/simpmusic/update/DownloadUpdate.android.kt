package com.maxrave.simpmusic.update

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import org.koin.mp.KoinPlatform.getKoin

actual fun downloadUpdateApk(
    url: String,
    fileName: String,
) {
    val context: AppCompatActivity = getKoin().get()
    val request =
        DownloadManager.Request(url.toUri()).apply {
            setTitle(fileName)
            setDescription("Earix update")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
        }
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}
