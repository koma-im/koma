package koma.storage.message.fetch

import koma.Failure
import koma.OtherFailure
import koma.koma_app.appState
import koma.matrix.MessageChunks
import koma.matrix.event.context.ContextResponse
import koma.matrix.event.room_message.RoomEvent
import koma.matrix.json.RawJson
import koma.matrix.pagination.FetchDirection
import koma.matrix.room.naming.RoomId
import koma.util.map
import link.continuum.database.models.RoomEventRow
import mu.KotlinLogging
import koma.util.KResult as Result

private val logger = KotlinLogging.logger {}

suspend fun fetchPreceding(
        row: RoomEventRow)
        : Result<FetchedBatch, Failure> {
    val service = appState.apiClient
    if (service == null) {
        return Result.failure(OtherFailure("no service for loading messages"))
    }
    val fetchkey = row.preceding_batch
    return if (fetchkey == null) {
        val eventid = row.event_id
        logger.warn { "trying to get pagination token by getting the context of $eventid" }
        service.getEventContext(RoomId(row.room_id), eventid).map { res ->
            FetchedBatch.fromContextBackward(res)
        }
    } else {
        service.getRoomMessages(RoomId(row.room_id), fetchkey, FetchDirection.Backward).map { res ->
            FetchedBatch.fromChunkedBackward(res)
        }
    }
}

class FetchedBatch(
        val messages: List<RawJson<RoomEvent>>,
        val prevKey: String?
) {
    companion object {
        fun fromChunkedBackward(chunked: MessageChunks): FetchedBatch {
            return FetchedBatch(
                    chunked.chunk.reversed(),
                    chunked.end
            )
        }

        fun fromContextBackward(res: ContextResponse): FetchedBatch {
            return FetchedBatch(
                    res.events_before.reversed(),
                    res.start
            )
        }
    }

    override fun toString(): String {
        return "FetchedBatch(messages=$messages, prevKey=$prevKey)"
    }
}
