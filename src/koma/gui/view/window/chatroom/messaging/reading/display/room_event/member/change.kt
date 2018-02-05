package koma.gui.view.window.chatroom.messaging.reading.display.room_event.member

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconFactory
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.layout.StackPane
import koma.gui.media.getMxcImagePropery
import koma.gui.view.window.chatroom.messaging.reading.display.room_event.util.showDatetime
import koma.gui.view.window.chatroom.messaging.reading.display.room_event.util.showUser
import koma.matrix.event.room_message.state.MRoomMember
import koma.matrix.room.participation.Membership
import tornadofx.*

fun renderMemberChange(message: MRoomMember): Node {
    val _node = StackPane()
    val content = message.content
    if (content.membership != Membership.join) return _node
    val pc = message.prev_content
    if (pc == null) {
        _node.apply {
            hbox(spacing = 5.0) {
                alignment = Pos.CENTER
                showUser(this, message.sender)
                text("joined this room.")
            }
            showDatetime(this, message.origin_server_ts)
        }
    } else {
        _node.apply {
            hbox(spacing = 5.0) {
                alignment = Pos.CENTER
                showUser(this, message.sender)
                vbox {
                    alignment = Pos.CENTER
                    if (pc.avatar_url != content.avatar_url) {
                        hbox(spacing = 5.0) {
                            alignment = Pos.CENTER
                            text("updated avatar") {
                                opacity = 0.5
                            }
                            stackpane {
                                pc.avatar_url?.let { imageview(getMxcImagePropery(it, 32.0, 32.0)) }
                                minWidth = 40.0
                            }
                            val arrowico = FontAwesomeIconFactory.get().createIcon(FontAwesomeIcon.ARROW_RIGHT)
                            arrowico.opacity = 0.3
                            add(arrowico)
                            stackpane {
                                content.avatar_url?.let { imageview(getMxcImagePropery(it, 32.0, 32.0)) }
                                minWidth = 40.0
                            }
                        }
                    }
                    if (pc.displayname != content.displayname) {
                        hbox(spacing = 5.0) {
                            text("updated name") {
                                opacity = 0.5
                            }
                            stackpane {
                                minWidth = 50.0
                                text(pc.displayname)
                            }
                            val arrowico = FontAwesomeIconFactory.get().createIcon(FontAwesomeIcon.ARROW_RIGHT)
                            arrowico.opacity = 0.3
                            add(arrowico)
                            stackpane{
                                text(content.displayname)
                                minWidth = 50.0
                            }
                        }
                    }
                }
            }
            showDatetime(this, message.origin_server_ts)
        }
    }
    return _node
}
