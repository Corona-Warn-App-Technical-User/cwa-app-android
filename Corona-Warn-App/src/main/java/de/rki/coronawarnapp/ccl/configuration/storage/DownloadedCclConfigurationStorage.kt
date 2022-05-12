package de.rki.coronawarnapp.ccl.configuration.storage

import de.rki.coronawarnapp.ccl.configuration.CclConfiguration
import de.rki.coronawarnapp.tag
import de.rki.coronawarnapp.util.reset.Resettable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DownloadedCclConfigurationStorage @Inject constructor(
    @CclConfiguration private val cclFile: File
) : Resettable {

    private val mutex = Mutex()
    private val cclConfigurationRawFile = File(cclFile, "ccl_config_raw")

    suspend fun load(): ByteArray? = mutex.withLock {
        cclConfigurationRawFile.load()
    }

    suspend fun save(rawData: ByteArray) = mutex.withLock {
        cclConfigurationRawFile.save(rawData = rawData)
    }

    override suspend fun reset() {
        mutex.withLock {
            Timber.tag(TAG).d("Clearing ccl storage")
            cclFile.deleteRecursively().also { Timber.tag(TAG).d("Clearing was successful %b", it) }
        }
    }

    private fun File.load(): ByteArray? = try {
        when (exists()) {
            true -> readBytes()
            false -> null
        }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to load %s", name)
        null
    }

    private fun File.save(rawData: ByteArray) {
        if (exists()) {
            Timber.tag(TAG).d("Overwriting %s with new data", name)
        }
        parentFile?.mkdirs()
        writeBytes(array = rawData)
    }
}

private val TAG = tag<DownloadedCclConfigurationStorage>()
