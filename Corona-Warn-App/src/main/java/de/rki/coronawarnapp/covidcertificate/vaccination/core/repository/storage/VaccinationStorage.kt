package de.rki.coronawarnapp.covidcertificate.vaccination.core.repository.storage

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.rki.coronawarnapp.covidcertificate.common.certificate.CwaCovidCertificate
import de.rki.coronawarnapp.covidcertificate.recovery.core.storage.StoredRecoveryCertificateData
import de.rki.coronawarnapp.util.di.AppContext
import de.rki.coronawarnapp.util.serialization.BaseGson
import de.rki.coronawarnapp.util.serialization.fromJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.Instant
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccinationStorage @Inject constructor(
    @AppContext val context: Context,
    @BaseGson val baseGson: Gson,
    private val containerPostProcessor: ContainerPostProcessor,
) {
    private val mutex = Mutex()
    private val prefs by lazy {
        context.getSharedPreferences("vaccination_localdata", Context.MODE_PRIVATE)
    }

    private val gson by lazy {
        // Allow for custom type adapter.
        baseGson.newBuilder().apply {
            registerTypeAdapterFactory(containerPostProcessor)
            registerTypeAdapterFactory(CwaCovidCertificate.State.typeAdapter)
        }.create()
    }

    suspend fun load(): Set<VaccinatedPersonData> = mutex.withLock {
        Timber.tag(TAG).d("load()")
        val persons = prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(PKEY_PERSON_PREFIX)) {
                return@mapNotNull null
            }
            value as String
            gson.fromJson<VaccinatedPersonData>(value).also { personData ->
                Timber.tag(TAG).v("Person loaded: %s", personData)
                requireNotNull(personData.identifier)
            }
        }
        return persons.toSet().groupDataByIdentifier()
    }

    suspend fun loadNew(): Set<VaccinationContainer> = mutex.withLock {
        Timber.tag(TAG).d("vaccinationCertificates - load()")
        return gson
            .fromJson<Set<VaccinationContainer>>(
                prefs.getString(PKEY_VACCINATION_CERT, null) ?: return emptySet(), TYPE_TOKEN
            )
            .onEach { Timber.tag(TAG).v("Stored vaccination data loaded: %s", it) }
    }

    suspend fun save(persons: Set<VaccinatedPersonData>) = mutex.withLock {
        Timber.tag(TAG).d("save(%s)", persons)

        prefs.edit(commit = true) {
            prefs.all.keys.filter { it.startsWith(PKEY_PERSON_PREFIX) }.forEach {
                Timber.tag(TAG).v("Removing data for %s", it)
                remove(it)
            }
            persons.forEach {
                if (it.vaccinations.isNotEmpty()) {
                    val raw = gson.toJson(it)
                    val identifier = it.identifier
                    Timber.tag(TAG).v("Storing vaccinatedPerson %s -> %s", identifier, raw)
                    putString("$PKEY_PERSON_PREFIX${identifier.groupingKey}", raw)
                }
            }
        }
    }

    suspend fun saveNew(certificates:Set<VaccinationContainer>) = mutex.withLock {
        Timber.tag(TAG).d("vaccinationCertificates - save(%s)", certificates)

        prefs.edit(commit = true) {
            if(certificates.isEmpty()){
                remove(PKEY_VACCINATION_CERT)
            } else {
                val rawJson = gson.toJson(certificates, TYPE_TOKEN)
                putString(PKEY_VACCINATION_CERT, rawJson)
            }
        }
    }

    companion object {
        private const val TAG = "VaccinationStorage"
        private const val PKEY_VACCINATION_CERT = "vaccination.certificate"
        private const val PKEY_PERSON_PREFIX = "vaccination.person."
        private val TYPE_TOKEN = object : TypeToken<Set<VaccinationContainer>>() {}.type
    }
}

internal fun Set<VaccinatedPersonData>.groupDataByIdentifier(): Set<VaccinatedPersonData> =
    filterNot { it.vaccinations.isNullOrEmpty() }
        .groupBy { it.identifier }
        .mapNotNull { entry ->
            val personDataList = entry.value
            if (personDataList.isEmpty()) {
                Timber.v("Person data list was empty, returning early")
                return@mapNotNull null
            }

            val newestData = personDataList.maxByOrNull {
                it.lastBoosterNotifiedAt ?: Instant.EPOCH
            }
            val vaccinations = personDataList.flatMap { it.vaccinations }.toSet()
            newestData?.copy(vaccinations = vaccinations)
        }.toSet()
