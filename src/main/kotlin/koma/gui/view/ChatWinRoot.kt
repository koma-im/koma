package koma.gui.view

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ObservableList
import javafx.scene.control.Button
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import koma.controller.requests.membership.dialogInviteMember
import koma.controller.requests.membership.runAskBanRoomMember
import koma.controller.requests.room.createRoomInteractive
import koma.gui.view.window.preferences.PreferenceWindow
import koma.gui.view.window.roomfinder.RoomFinder
import koma.gui.view.window.userinfo.actions.chooseUpdateUserAvatar
import koma.gui.view.window.userinfo.actions.updateMyAlias
import koma.koma_app.AppStore
import koma.koma_app.appState
import koma.storage.persistence.settings.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.javafx.JavaFx
import link.continuum.database.KDataStore
import link.continuum.desktop.gui.UiDispatcher
import model.Room
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.controlsfx.control.NotificationPane
import org.controlsfx.control.action.Action
import tornadofx.*

private val settings: AppSettings = appState.store.settings

/**
 * everything inside the app window after login
 * including a menu bar at the top
 *
 * Created by developer on 2017/6/17.
 */
class ChatWindowBars(
        roomList: ObservableList<Room>, server: HttpUrl, kDataStore: KDataStore,
        store: AppStore,
        httpClient: OkHttpClient
) {
    private val content = BorderPane()
    val root = NotificationPane(content)
    val center = ChatView(roomList, server, kDataStore, store, httpClient)
    val status = SyncStatusBar(root)

    private val roomFinder by lazy { RoomFinder(server, client = httpClient) }
    init {
        with(content) {
            style {
                fontSize= settings.scaling.em
            }
            center = this@ChatWindowBars.center.root
            top = menubar {
                menu("File") {
                    item("Create Room").action { createRoomInteractive() }
                    item("Join Room") {
                        action { roomFinder.open() }
                    }
                    item("Preferences").action {
                        find(PreferenceWindow::class).openModal()
                    }
                    item("Quit").action {
                        FX.primaryStage.close()
                    }
                }
                menu("Room") {
                    item("Ban Member") {
                        action { runAskBanRoomMember() }
                    }
                }
                menu("Me") {
                    item("Update avatar").action { chooseUpdateUserAvatar() }
                    item("Update my name").action { updateMyAlias() }
                }
                contextmenu {
                    item("Update my avatar").action { chooseUpdateUserAvatar() }
                    item("Update my name").action { updateMyAlias() }
                    item("Join Room").action { roomFinder.open() }
                }
            }
        }
    }
}

class SyncStatusBar(
        private val pane: NotificationPane
) {
    val ctrl = Channel<Variants>(Channel.CONFLATED)

    /**
     * true unless there is error
     */
    private var syncing = true

    init {
        GlobalScope.launch(Dispatchers.JavaFx) {
            for (s in ctrl) {
                update(s)
            }
        }
    }

    private fun update(s: Variants) {
        when (s) {
            is Variants.Normal -> {
                pane.hide()
            }
            is Variants.FullSync -> {
                pane.show("Doing a full sync", null, null)
            }
            is Variants.NeedRetry -> {
                syncing = false
                val countDown = GlobalScope.launch(Dispatchers.JavaFx) {
                    for (i in 9 downTo 1) {
                        pane.text = "Network issue, retrying in $i seconds"
                        delay(1000)
                    }
                    setRetrying(s.retryNow)
                }
                pane.text = "Network issue"
                pane.actions.setAll(Action("Retry Now") {
                    countDown.cancel()
                    setRetrying(s.retryNow)
                })
                if (!pane.isShowing) {
                    pane.show()
                }
            }
        }
    }

    private fun setRetrying(retryNow: CompletableDeferred<Unit>) {
        syncing = true
        pane.actions.clear()
        pane.text = "Syncing"
        if (!retryNow.isCompleted) retryNow.complete(Unit)
        GlobalScope.launch(UiDispatcher) {
            delay(3000)
            // assume the long-polling sync api is working
            if (syncing) {
                pane.hide()
            }
        }
    }

    // various states
    sealed class Variants {
        class Normal(): Variants()
        class FullSync(): Variants()
        // network issue that may be temporary
        class NeedRetry(val err: Exception, val retryNow: CompletableDeferred<Unit>): Variants()
        // authentication error
        class NeedRelogin(val err: Exception, val restart: CompletableDeferred<Unit>): Variants()
    }
}