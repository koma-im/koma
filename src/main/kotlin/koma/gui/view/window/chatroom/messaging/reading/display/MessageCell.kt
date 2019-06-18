package koma.gui.view.window.chatroom.messaging.reading.display

import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import koma.gui.view.window.chatroom.messaging.reading.display.room_event.m_message.MRoomMessageViewNode
import koma.gui.view.window.chatroom.messaging.reading.display.room_event.member.MRoomMemberViewNode
import koma.gui.view.window.chatroom.messaging.reading.display.room_event.room.MRoomCreationViewNode
import koma.matrix.event.room_message.MRoomMessage
import koma.matrix.event.room_message.RoomEvent
import koma.matrix.event.room_message.state.MRoomCreate
import koma.matrix.event.room_message.state.MRoomMember
import koma.matrix.json.MoshiInstance
import koma.storage.message.MessageManager
import koma.util.formatJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import link.continuum.database.models.RoomEventRow
import link.continuum.database.models.getEvent
import link.continuum.desktop.gui.list.user.UserDataStore
import link.continuum.desktop.gui.showIf
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import tornadofx.*

private val logger = KotlinLogging.logger {}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class MessageCell(
        private val server: HttpUrl,
        private val manager: MessageManager,
        store: UserDataStore,
        client: OkHttpClient
) {
    private val center = StackPane()
    private val loading = Label("Loading older messages...")

    val node = VBox(3.0).apply {
        hbox {
            alignment = Pos.CENTER
            add(loading)
        }
        add(center)
    }
    private val contextMenu: ContextMenu
    private val contextMenuShowSource = MenuItem("View Source").apply {
        action { current?.let {
            sourceViewer.showAndWait(it)
        }
        }
    }
    private var current: RoomEventRow? = null

    private val memberView = MRoomMemberViewNode(store, client)
    private val messageView by lazy { MRoomMessageViewNode(server, store, client) }
    private val creationView by lazy { MRoomCreationViewNode(store, client) }


    fun updateEvent(message: RoomEventRow) {
        loading.managedProperty().unbind()
        loading.visibleProperty().unbind()
        loading.showIf(false)
        if(!message.preceding_stored) {
            logger.debug { "messages before ${message.event_id} are not stored yet" }
            loading.showIf(true)
            val status = manager.fetchPrecedingRows(message)
            loading.managedProperty().bind(status)
            loading.visibleProperty().bind(status)
        }
        current = message
        center.children.clear()
        contextMenu.items.clear()
        val ev = message.getEvent()
        val vn = when(ev) {
            is MRoomMember -> {
                memberView.update(ev, server)
                memberView
            }
            is MRoomCreate -> {
                creationView.update(ev)
                creationView
            }
            is MRoomMessage -> {
                messageView.update(ev)
                messageView
            }
            else -> null
        }
        if (vn!= null) {
            center.children.add(vn.node)
            contextMenu.items.addAll(vn.menuItems)
            contextMenu.items.add(contextMenuShowSource)
        }
    }
    init {
        contextMenu = node.contextmenu()
    }
}

interface ViewNode {
    val node: Region
    val menuItems: List<MenuItem>
}

private val sourceViewer by lazy { EventSourceViewer() }

class EventSourceViewer{
    private val dialog = Dialog<Unit>()
    private val textArea = TextArea()
    private var raw: String = ""
    private var processed: String = ""
    fun showAndWait(roomEvent: RoomEventRow) {
        raw = formatJson(roomEvent.json)
        processed = formatJson(MoshiInstance.roomEventAdapter.toJson(roomEvent.getEvent()))
        textArea.text = raw
        dialog.showAndWait()
    }

    init {
        textArea.isEditable = false
        textArea.hgrow = Priority.ALWAYS
        textArea.vgrow = Priority.ALWAYS
        val head = HBox().apply {
            vbox {
                text("Room Event Source")
                alignment = Pos.CENTER_LEFT
                hgrow = Priority.ALWAYS
            }
            buttonbar {
                button("Raw") {
                    tooltip = Tooltip("Json string from server")
                    setOnAction {
                        textArea.text = raw
                    }
                }
                button("Processed") {
                    tooltip = Tooltip("Portion of json that is supported")
                    setOnAction {
                        textArea.text = processed
                    }
                }
            }
        }
        dialog.apply {
            title = "Room Event Source"
            isResizable = true
            dialogPane.apply {
                content = VBox(5.0, head, textArea).apply {
                    vgrow = Priority.ALWAYS
                }
                buttonTypes.add(ButtonType.CLOSE)
            }
        }
    }
}