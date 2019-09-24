package tin.thurein.androiddownloadapplication

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    lateinit var mNotifyManager: NotificationManager
    val REQUEST_CODE = 234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mNotifyManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
       
        btnNoti.setOnClickListener {
            val url = etUrl.text.toString()
            if (!etUrl.text.toString().equals("")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermission())  {
                    requestPermission()
                } else {
                    startDownload(url)
                }
            } else {
                Toast.makeText(this@MainActivity, "Enter your download link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun hasPermission(): Boolean {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        return false
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE);
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            grantResults.apply {
                if (size > 0 && get(0) == Activity.RESULT_OK) {
                    startDownload(etUrl.text.toString())
                }
            }
        }
    }

    private fun startDownload(url: String) {
        val intent = Intent(this, DownloadIntentService::class.java)
        intent.putExtra("url", url)
        startService(intent)
    }
    
}
