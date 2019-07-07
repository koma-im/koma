package link.continuum.desktop.util.http

import koma.Failure
import koma.Koma
import koma.network.media.MHUrl
import koma.network.media.downloadMedia
import koma.util.*
import koma.util.coroutine.adapter.okhttp.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import link.continuum.desktop.gui.switchGetDeferredOption
import link.continuum.desktop.util.*
import mu.KotlinLogging
import okhttp3.*
import java.util.*
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}
private typealias Option<T> = Optional<T>
typealias MediaServer = HttpUrl

suspend fun downloadHttp(
        url: HttpUrl, client: OkHttpClient, maxStale: Int? = null
): KResult<ByteArray, Failure> {
    val req = Request.Builder().url(url).given(maxStale) {
        cacheControl(CacheControl
                    .Builder()
                    .maxStale(it, TimeUnit.SECONDS)
                    .build())
    }.build()
    val res = client.newCall(req).await() getOr  { return Err(it)}
    val b = res.body()
    if (!res.isSuccessful || b == null) {
        return fmtErr { "failed to get response body for $url" }
    }
    return Ok(b.use { it.bytes() })
}

/**
 * given a channel of URLs, get the latest download
 */
typealias DL = Option<Pair<MHUrl, HttpUrl>>
fun CoroutineScope.urlChannelDownload(koma: Koma
): Pair<SendChannel<DL>, ReceiveChannel<Option<ByteArray>>> {
    val url = Channel<DL>(Channel.CONFLATED)
    val bytes = Channel<Option<ByteArray>>(Channel.CONFLATED)
    switchGetDeferredOption(url, { u ->
        deferredDownload(u.first, u.second, koma)
    }, bytes)
    return url to bytes
}

fun CoroutineScope.deferredDownload(url: MHUrl, server: HttpUrl, koma: Koma) = async {
    koma.downloadMedia(url, server).fold({
        Some(it)
    }, {
        logger.warn { "deferredDownload of $url error $it" }
        None<ByteArray>()
    })
}
