package link.continuum.desktop.gui.icon.avatar

import javafx.beans.property.SimpleObjectProperty
import javafx.scene.image.Image
import koma.Server
import koma.network.media.MHUrl
import koma.util.getOr
import kotlinx.coroutines.*
import link.continuum.desktop.gui.UiDispatcher
import link.continuum.desktop.util.None
import link.continuum.desktop.util.Option
import link.continuum.desktop.util.Some
import mu.KotlinLogging
import org.cache2k.Cache
import org.cache2k.Cache2kBuilder
import org.cache2k.configuration.Cache2kConfiguration
import java.io.InputStream

private val logger = KotlinLogging.logger {}
private typealias Item = Deferred<Option<Image>>

class DeferredImage(
        val processing: (InputStream) -> Image
): CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val cache: Cache<MHUrl, Item>

    init {
        cache = createCache()
    }

    fun getDeferred(url: MHUrl, server: Server): Item {
        return cache.computeIfAbsent(url) { createImageProperty(url, server) }
    }

    private fun createImageProperty(url: MHUrl, server: Server): Item {
        return asyncImage(url, server)
    }

    private fun CoroutineScope.asyncImage(url: MHUrl, server: Server) = async {
        val bs = server.downloadMedia(url) getOr {
            logger.warn { "image $url not downloaded, ${it}, returning None" }
            return@async None<Image>()
        }
        val img = processing(bs.inputStream())
        Some(img)
    }

    private fun createCache(): Cache<MHUrl, Item> {
        val conf = Cache2kConfiguration<MHUrl, Item>()
        val cache = Cache2kBuilder.of(conf)
                .entryCapacity(100)
        return cache.build()
    }
}


private typealias ImageProperty = SimpleObjectProperty<Image>

fun CoroutineScope.downloadImageResized(url: MHUrl, size: Double, server: Server): ImageProperty {
    val prop = ImageProperty()
    launch {
        val bs = server.downloadMedia(url) getOr  {
            logger.error { "download of $url fails with ${it}" }
            return@launch
        }
        val img = bs.inputStream().use {
            Image(it, size, size, true , true)
        }
        withContext(UiDispatcher) { prop.set(img) }
    }
    return prop
}
