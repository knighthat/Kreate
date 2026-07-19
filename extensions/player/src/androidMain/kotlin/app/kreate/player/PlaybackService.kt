package app.kreate.player

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import app.kreate.internal.player.SessionCallback
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import androidx.media3.common.Player as MediaPlayer


class PlaybackService : MediaLibraryService(), KoinComponent {

    private val logger = Logger.withTag( "PlaybackService" )

    private lateinit var player: Player
    private var session: MediaLibrarySession? = null

    override fun onGetSession( controller: MediaSession.ControllerInfo ): MediaLibrarySession? {
        logger.v { "Received request for new session from $controller" }

        // TODO: Implement a detector that hands unrestricted access to [Player] if controller is
        //  requested by internal components while giving [ForwardingPlayer] to other controllers.
        return session
    }

    override fun onCreate() {
        logger.v { "Creating new service" }

        super.onCreate()

        this.player = get()
        this.session = MediaLibrarySession.Builder(this, player as MediaPlayer, SessionCallback()).build()

        logger.d { "Service started" }
    }

    override fun onDestroy() {
        logger.v { "Destroying service" }

        session?.run {
            player.release()
            release()
            session = null
        }

        super.onDestroy()

        logger.d { "Service destroyed" }
    }
}