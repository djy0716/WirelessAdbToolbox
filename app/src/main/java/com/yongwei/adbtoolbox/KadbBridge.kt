package com.yongwei.adbtoolbox

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.runBlocking

object KadbBridge {
    @JvmStatic
    fun pairBlocking(host: String, port: Int, code: String) = runBlocking {
        Kadb.pair(host, port, code, "ADB Toolbox")
    }

    @JvmStatic
    fun create(host: String, port: Int): Kadb =
        Kadb.create(host, port, connectTimeout = 20_000, socketTimeout = 120_000)
}
