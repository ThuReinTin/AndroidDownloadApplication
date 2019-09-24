package tin.thurein.androiddownloadapplication

import android.app.Activity.RESULT_OK
import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import java.time.temporal.TemporalAccessor
import java.util.*

class DownloadIntentService(private val name: String) : IntentService(name) {

    private val TAG = "Download Service"

    constructor () : this("hello") {
    }

    private lateinit var builder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var downloadReceiver: DonwloadBroadcastReceiver

    private val notificationId: Int = 1
    private val channelId = "channel_download"

    companion object {
        val CANCEL_ACTION = "tin.thurein.download.cancel"
        var isCancelled = false
    }


    val PROGRESS_MAX = 100

    override fun onCreate() {
        super.onCreate()
        downloadReceiver = DonwloadBroadcastReceiver()
        builder = NotificationCompat.Builder(baseContext, channelId).apply {
            setContentTitle("File download")
            setContentText("Downloading")
            setSmallIcon(R.drawable.download)
            setPriority(NotificationCompat.PRIORITY_LOW)

            val cancelIntent = Intent(baseContext, DonwloadBroadcastReceiver::class.java).apply {
                action = CANCEL_ACTION
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(baseContext, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            addAction(R.drawable.cancel, "Cancel", cancelPendingIntent)
        }

        notificationManager = NotificationManagerCompat.from(baseContext)
        notificationManager.apply {

            builder.setProgress(PROGRESS_MAX, 0, true)
            notify(notificationId, builder.build())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_LOW
                )
                createNotificationChannel(channel)
                builder.setChannelId(channelId)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentFilter = IntentFilter()
        intentFilter.addAction(CANCEL_ACTION)
        registerReceiver(downloadReceiver, intentFilter)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        intent?.apply {
            download(getStringExtra("url"))
        }
    }

    override fun onDestroy() {
        unregisterReceiver(downloadReceiver)
        super.onDestroy()
    }

    private fun updateNotification(currentProgress: Int, maxProgress: Int, indeterminate: Boolean, msg: String) {
        builder.apply {
            setProgress(maxProgress, currentProgress, indeterminate)
            if (maxProgress != -1 && currentProgress >= maxProgress) {
                setContentText(msg)
                setProgress(0, 0, indeterminate)
                mActions.clear()
            }
        }
        notificationManager.notify(notificationId, builder.build())
    }

    private fun download(urlString: String) {
        var input: InputStream? = null
        var output: OutputStream? = null
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            var fileLength : Int
            var fileName : String = ""
            var extension = ""
            connection.apply {
                connect()
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, responseMessage)
                    updateNotification(101, 100, false, "Invalid download link.")
                    return
                }
                fileLength = contentLength
                input = inputStream

                val disposition = getHeaderField("Content-Disposition")
                val contentType = getContentType()
                val contentLength = getContentLength()

                if (disposition != null) {
                    // extracts file name from header field
                    val index = disposition!!.indexOf("filename=")
                    if (index > 0) {
                        fileName = disposition!!.substring(
                            index + 10,
                            disposition!!.length - 1
                        )
                    }
                } else {
                    // extracts file name from URL
                    fileName = Date().time.toString()
                }
                Log.e(TAG, "FILENAME : $fileName")
                Log.e(TAG, "CONTENT TYPE : $contentType")
                extension = contentType.split("/")[1]
            }

            output = FileOutputStream("/sdcard/$fileName.$extension")

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int = -1

            input?.apply { count = read(data) }
            while (count != -1) {
                // allow canceling with back button
                if (isCancelled) {
                    input?.apply {
                        updateNotification(101, 100, false, "Download cancelled")
                        close()
                        isCancelled = false
                    }
                    return
                }
                total += count.toLong()
                // publishing the progress....
                if (fileLength > 0) {
                    // only if total length is known
                    updateNotification(total.toInt(), fileLength, false, "Downloading")
                } else {
                    updateNotification(total.toInt(), fileLength, true, "Downloading")
                }
                output.apply { write(data, 0, count) }
                input?.apply { count = read(data) }
            }

            updateNotification((total + 1).toInt(), total.toInt(), false, "Download complete!")
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            updateNotification(101, 100, false, "Invalid download link.")
            return
        } finally {
            try {
                output?.apply { close() }
                input?.apply { close() }
            } catch (ignored: IOException) {
                Log.e(TAG, ignored.toString())
                updateNotification(101, 100, false, "Invalid download link.")
            }

            connection?.apply { disconnect() }
        }
        return
    }

    class DonwloadBroadcastReceiver() : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.apply {
                if (action.equals(CANCEL_ACTION)) {
                    isCancelled = true
                }
            }
        }

    }
}