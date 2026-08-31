package eu.kanade.tachiyomi.animeextension.en.miruro

import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

@Serializable
internal class MiruroProxyConfig(
    @SerialName("VITE_PROXY_A") private val proxyA: String = "",
    @SerialName("VITE_PROXY_B") private val proxyB: String = "",
    @SerialName("VITE_PROXY_OBF_KEY") private val obfuscationKey: String = "",
) {
    val urls: List<String>
        get() = listOf(proxyA, proxyB).filter { it.isNotBlank() }.map { url ->
            val parsed = url.toHttpUrlOrNull()
                ?.takeIf { it.isHttps }
                ?: throw IOException("Invalid Miruro proxy address")
            "${parsed.toString().trimEnd('/')}/"
        }.ifEmpty { throw IOException("Miruro proxy addresses are unavailable") }

    val key: ByteArray
        get() = obfuscationKey.takeIf { it.isNotEmpty() && it.length % 2 == 0 }
            ?.let { runCatching { it.decodeHex() }.getOrNull() }
            ?: throw IOException("Miruro proxy key is unavailable or invalid")

    companion object {
        fun parse(script: String): MiruroProxyConfig {
            // env2.js assigns a JSON string through window.env = JSON.parse(...).
            val encoded = script.substringAfter("JSON.parse(", "").substringBeforeLast(")", "")
            val config = encoded.parseAs<String>().parseAs<MiruroProxyConfig>()
            config.urls
            config.key
            return config
        }
    }
}
