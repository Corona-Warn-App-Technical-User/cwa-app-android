package de.rki.coronawarnapp.profile.storage

import de.rki.coronawarnapp.profile.model.Profile
import de.rki.coronawarnapp.profile.model.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
) {
    val profilesFlow: Flow<Set<Profile>> = dao.getAll().mapLatest { list ->
        list.map { it.fromEntity() }.toSet()
    }

    suspend fun deleteProfile(id: ProfileId) {
        dao.delete(id)
    }

    suspend fun upsertProfile(profile: Profile): ProfileId {
        val entity = profile.toEntity()
        return if (entity.id == 0)
            dao.insert(entity).toInt()
        else {
            dao.update(entity)
            entity.id
        }
    }

    suspend fun clear() {
        dao.deleteAll()
    }
}

internal fun Profile.toEntity() = ProfileEntity(
    id = id ?: 0,
    firstName = firstName,
    lastName = lastName,
    birthDate = birthDate,
    street = street,
    zipCode = zipCode,
    city = city,
    phone = phone,
    email = email
)

internal fun ProfileEntity.fromEntity() = Profile(
    id = id,
    firstName = firstName,
    lastName = lastName,
    birthDate = birthDate,
    street = street,
    zipCode = zipCode,
    city = city,
    phone = phone,
    email = email
)
