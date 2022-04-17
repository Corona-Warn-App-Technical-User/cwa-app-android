package de.rki.coronawarnapp.covidcertificate.revocation.update

import androidx.annotation.VisibleForTesting
import de.rki.coronawarnapp.covidcertificate.common.certificate.CertificateProvider
import de.rki.coronawarnapp.covidcertificate.revocation.storage.RevocationRepository
import de.rki.coronawarnapp.tag
import de.rki.coronawarnapp.util.TimeAndDateExtensions.toLocalDateUtc
import de.rki.coronawarnapp.util.TimeStamper
import de.rki.coronawarnapp.util.coroutine.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.Instant
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RevocationListUpdater @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val timeStamper: TimeStamper,
    private val certificatesProvider: CertificateProvider,
    private val revocationUpdateSettings: RevocationUpdateSettings,
    private val revocationRepository: RevocationRepository,
) {

    private val mutex = Mutex()

    init {
        appScope.launch {
            certificatesProvider.allCertificatesSize
                .filterForceUpdate()
                .drop(1) // App start emission
                .filter {
                    // The ones that actually matters (true), and if new changes are not requiring updates (false),
                    // any previous update (true) that was running won't get cancelled
                    it
                }.catch {
                    Timber.tag(TAG).d("filterForceUpdate flow failed -> %s", it.message)
                }.collectLatest { // Cancel previous update is a new one came in
                    Timber.tag(TAG).d("Update revocation list on new registration")
                    updateRevocationList(true)
                }
        }
    }

    suspend fun updateRevocationList(forceUpdate: Boolean = false) = mutex.withLock {
        try {
            val timeUpdateRequired = isUpdateRequired()
            Timber.tag(TAG).d("updateRevocationList(forceUpdate=$forceUpdate, timeUpdateRequired=$timeUpdateRequired)")
            when {
                forceUpdate || timeUpdateRequired -> {
                    Timber.tag(TAG).d("updateRevocationList is required")
                    revocationRepository.updateRevocationList(
                        certificatesProvider.certificateContainer.first().allCwaCertificates
                    )
                    revocationUpdateSettings.setUpdateTimeToNow()
                }
                else -> Timber.tag(TAG).d("updateRevocationList isn't required")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d("updateRevocationList failed ->%s", e.message)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun isUpdateRequired(now: Instant = timeStamper.nowUTC): Boolean {
        val lastExecution = revocationUpdateSettings.getLastUpdateTime() ?: return true

        // update is needed if the last update was on a different day
        return lastExecution.toLocalDateUtc() != now.toLocalDateUtc()
    }

    /**
     * Observes certificates count change and detect if force update is required.
     * Idea is to trigger updates only when a new certificate is registered
     * moving it to and from recycle bin and deleting it permanently don't count
     * ex:
     * From old=10 , new=10 -> false
     * From old=10 , new=09 -> false
     * From old=09 , new=08 -> false
     * From old=08 , new=07 -> false
     * From old=07 , new=08 -> true
     * From old=08 , new=09 -> true
     * From old=09 , new=00 -> false
     * From old=00 , new=01 -> true
     * From old=01 , new=02 -> true
     */
    private fun Flow<Int>.filterForceUpdate() = flow {
        var old = Int.MIN_VALUE
        collect { new ->
            emit(new > old)
            old = new
        }
    }

    companion object {
        private val TAG = tag<RevocationListUpdater>()
    }
}
