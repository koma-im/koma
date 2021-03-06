package koma.gui.view.window.userinfo.actions

import javafx.stage.FileChooser
import koma.controller.requests.media.uploadFile
import koma.koma_app.appState
import koma.matrix.user.AvatarUrl
import koma.util.failureOrThrow
import koma.util.getOrThrow
import koma.util.onFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import link.continuum.desktop.gui.JFX
import org.controlsfx.control.Notifications

fun chooseUpdateUserAvatar() {
    val api = appState.apiClient
    api ?: return
    val dialog = FileChooser()
    dialog.title = "Upload a new avatar"
    val file = dialog.showOpenDialog(JFX.primaryStage)
    file ?: return
    GlobalScope.launch {
        val upload = uploadFile(api, file)
        when  {
            upload.isFailure -> {
                val message = upload.failureOrThrow().message
                launch(Dispatchers.JavaFx) {
                    Notifications.create()
                            .title("Failed to upload new avatar")
                            .text(message.toString())
                            .owner(JFX.primaryStage)
                            .showWarning()
                }
            }
            upload.isSuccess -> {
                val data = AvatarUrl(upload.getOrThrow().content_uri)
                val result = api.updateAvatar(api.userId, data)
                result.onFailure {
                    val message = it.message
                    launch(Dispatchers.JavaFx) {
                        Notifications.create()
                                .title("Failed to set new avatar")
                                .text(message.toString())
                                .owner(JFX.primaryStage)
                                .showWarning()
                    }
                }
            }
        }
    }
}
