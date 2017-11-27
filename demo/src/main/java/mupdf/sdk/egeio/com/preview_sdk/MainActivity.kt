package mupdf.sdk.egeio.com.preview_sdk

import android.content.Intent
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import com.artifex.mupdf.DocumentActivity2
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var button = findViewById(R.id.button)
        button.setOnClickListener {
            val intent = Intent()
            intent.data = Uri.fromFile(
                    File(Environment.getExternalStorageDirectory().absolutePath + File.separator + "pdf" + File.separator + "sthm.pdf"))
            intent.setClass(this, DocumentActivity2::class.java)
//            intent.action = Intent.ACTION_VIEW
            startActivity(intent)
        }
    }
}
