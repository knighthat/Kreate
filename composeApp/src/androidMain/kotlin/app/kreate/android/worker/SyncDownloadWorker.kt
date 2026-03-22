package app.kreate.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.kreate.android.service.download.CacheState
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class SyncDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val cacheState: CacheState by inject()

    override suspend fun doWork(): Result = withContext( Dispatchers.IO ) {
        try {
            cacheState.sync()

            Result.success()
        } catch( e: Exception ) {
            Logger.e( "", e, this::class.java.simpleName )

            Result.failure()
        }
    }
}