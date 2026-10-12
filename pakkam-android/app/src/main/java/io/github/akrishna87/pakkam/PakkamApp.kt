package io.github.akrishna87.pakkam

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PakkamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
