package io.github.akrishna87.pakkam

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PakkamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Creates Android/data/<app>/files with the app as its owner, so PDFs copied there over
        // USB (as the emulator smoke test does) can be opened with a file:// link.
        getExternalFilesDir(null)
    }
}
