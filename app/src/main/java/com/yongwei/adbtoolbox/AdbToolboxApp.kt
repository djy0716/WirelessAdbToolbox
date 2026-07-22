package com.yongwei.adbtoolbox

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import java.io.File

class AdbToolboxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val keyFile = File(filesDir, "adb-host-private-key.pem")
        KadbCert.configure(OkioFilePrivateKeyStore(keyFile.absolutePath.toPath()))
    }
}
