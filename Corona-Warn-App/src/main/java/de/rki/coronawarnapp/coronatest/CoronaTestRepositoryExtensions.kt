package de.rki.coronawarnapp.coronatest

import de.rki.coronawarnapp.coronatest.type.BaseCoronaTest
import de.rki.coronawarnapp.coronatest.type.PersonalCoronaTest
import de.rki.coronawarnapp.coronatest.type.pcr.PCRCoronaTest
import de.rki.coronawarnapp.coronatest.type.rapidantigen.RACoronaTest
import de.rki.coronawarnapp.util.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber

val CoronaTestRepository.latestPCRT: Flow<PCRCoronaTest?>
    get() = this.coronaTests
        .map { allTests ->
            allTests.singleOrNull {
                it.type == BaseCoronaTest.Type.PCR
            } as? PCRCoronaTest
        }
        .distinctUntilChanged()

val CoronaTestRepository.latestRAT: Flow<RACoronaTest?>
    get() = this.coronaTests
        .map { allTests ->
            allTests.singleOrNull {
                it.type == BaseCoronaTest.Type.RAPID_ANTIGEN
            } as? RACoronaTest
        }
        .distinctUntilChanged()

val CoronaTestRepository.positiveViewedTests: Flow<List<BaseCoronaTest>>
    get() = combine(latestPCRT, latestRAT) { testPcr, testRat ->
        listOfNotNull(testPcr, testRat).filter { it.isPositive && it.isViewed }
    }

// This in memory to not show duplicate errors
// CoronaTest.lastError is also only in memory
private val consumedErrors = mutableMapOf<String, Throwable?>()

val CoronaTestRepository.testErrorsSingleEvent: Flow<List<PersonalCoronaTest>>
    get() = coronaTests
        .map { tests ->
            tests
                .filter {
                    val consumedClass = consumedErrors[it.identifier]?.javaClass
                    consumedClass != it.lastError?.javaClass
                }
                .onEach {
                    Timber.v("Unconsumed error for %s: %s", it.identifier, it.lastError?.toString())
                    consumedErrors[it.identifier] = it.lastError
                }
                .filter { it.lastError != null }
        }
        .flatMapMerge { tests ->
            // First we emit the tests with errors
            // then an empty list because the errors should only be displayed once
            flowOf(tests, emptyList())
        }
