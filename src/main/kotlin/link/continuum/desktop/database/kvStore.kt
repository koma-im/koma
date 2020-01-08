package link.continuum.desktop.database

import javafx.collections.FXCollections
import javafx.stage.Stage
import koma.matrix.UserId
import koma.matrix.room.naming.RoomId
import koma.storage.persistence.settings.encoding.ProxyList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * consolidate operations to reduce risk of getting string keys wrong
 */
class KeyValueStore(
        private val mvStore: MVStore
) {
    val windowSizeMap = mvStore.openMap<String, Double>("window-size-settings")
    private val map1 = mvStore.openMap<String, String>("strings")
    private val proxies = mvStore.openMap<String,Long>("proxies")
    val activeAccount = Entry("active-account", map1)
    val proxyList = ProxyList(proxies)
    private val accountRooms = ConcurrentHashMap<UserId, AccountRooms>()
    fun saveStageSize(stage: Stage) {
        val prefs = windowSizeMap
        prefs.put("chat-stage-x", stage.x)
        prefs.put("chat-stage-y", stage.y)
        prefs.put("chat-stage-w", stage.width)
        prefs.put("chat-stage-h", stage.height)
    }
    fun roomsOf(userId: UserId): AccountRooms {
        return accountRooms.computeIfAbsent(userId) {
            // mvStore.openMap should be short and simple enough
            val j = mvStore.openMap<String, Int>("account,${userId.full},joins")
            val l = mvStore.openMap<String, Int>("account,${userId.full},leaves")
            AccountRooms(j, l)
        }
    }
    fun close() {
        mvStore.close()
    }
}

/**
 * rooms a locally logged in user is participating in
 */
class AccountRooms(
        private val joinedMap: MVMap<String, Int>,
        private val leftMap: MVMap<String, Int>
) {
    private val scope = MainScope()
    val joinedRoomList = FXCollections.observableArrayList<RoomId>()
    private val mutex = Mutex(locked = true)
    init {
        scope.launch {
            try {
                joinedRoomList.addAll(joinedMap.keys.map {RoomId(it)})
            } finally {
                mutex.unlock()
            }
        }
    }
    suspend fun join(roomIds: Collection<RoomId>) {
        mutex.withLock {
            roomIds.forEach {
                if (leftMap.remove(it.full) != null) {
                    logger.info { "rejoining room $it"}
                }
            }
            val new = roomIds.filter { joinedMap.put(it.full, 0) == null }
            if (new.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    logger.info { "joining rooms $new" }
                    joinedRoomList.addAll(new)
                }
            }
        }
    }
    suspend fun leave(roomIds: Collection<RoomId>) {
        mutex.withLock {
            roomIds.forEach {
                val prev = joinedMap.remove(it.full)
                val prevJoined = prev != null
                logger.info { "leaving room $it, was inside = $prevJoined"}
            }
            val new = roomIds.filter { leftMap.put(it.full, 0) == null }
            if (new.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    logger.info { "leaving rooms $new" }
                    joinedRoomList.removeAll(new)
                }
            }
        }
    }
}
class Entry<K, V: Any>(
        private val key: K,
        private val map: MutableMap<K, V>
) {
    fun getOrNull(): V?{
        return map.get(key)
    }
    fun put(value: V) {
        map.put(key, value)
    }
    fun remove() {
        map.remove(key)
    }
}