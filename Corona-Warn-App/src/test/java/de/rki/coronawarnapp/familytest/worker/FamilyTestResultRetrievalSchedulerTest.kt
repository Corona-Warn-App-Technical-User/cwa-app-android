package de.rki.coronawarnapp.familytest.worker

import androidx.work.WorkManager
import de.rki.coronawarnapp.coronatest.type.BaseCoronaTest
import de.rki.coronawarnapp.familytest.core.model.FamilyCoronaTest
import de.rki.coronawarnapp.familytest.core.repository.FamilyTestRepository
import de.rki.coronawarnapp.util.TimeStamper
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.joda.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutines.runBlockingTest2

class FamilyTestResultRetrievalSchedulerTest : BaseTest() {

    @MockK lateinit var workManager: WorkManager
    @MockK lateinit var repository: FamilyTestRepository
    @MockK lateinit var timeStamper: TimeStamper

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns mockk()
        every { workManager.cancelUniqueWork(any()) } returns mockk()
        every { timeStamper.nowUTC } returns Instant.parse("2021-03-20T07:00:00.000Z")
    }

    private fun createInstance(scope: CoroutineScope = TestCoroutineScope()) = FamilyTestResultRetrievalScheduler(
        appScope = scope,
        repository = repository,
        workManager = workManager,
        timeStamper = timeStamper,
    )

    @Test
    fun `setup works`() = runBlockingTest2(ignoreActive = true) {
        val flow = MutableStateFlow(setOf<FamilyCoronaTest>())
        every { repository.familyTests } returns flow
        every { repository.familyTestsToRefresh } returns flowOf(
            setOf(
                mockk<FamilyCoronaTest>().apply {
                    every { identifier } returns "id1"
                    every { registeredAt } returns Instant.parse("2021-03-20T06:00:00.000Z")
                    every { type } returns BaseCoronaTest.Type.PCR
                }
            )
        )
        createInstance(this).setup()
        flow.emit(
            setOf(
                mockk<FamilyCoronaTest>().apply {
                    every { identifier } returns "id1"
                    every { registeredAt } returns Instant.parse("2021-03-20T06:00:00.000Z")
                    every { type } returns BaseCoronaTest.Type.PCR
                }
            )
        )
        verify { workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, any(), any()) }
    }

    @Test
    fun `frequent polling needs to be scheduled`() {
        every { repository.familyTestsToRefresh } returns flowOf(
            setOf(
                mockk<FamilyCoronaTest>().apply {
                    every { registeredAt } returns Instant.parse("2021-03-20T06:00:00.000Z")
                    every { type } returns BaseCoronaTest.Type.RAPID_ANTIGEN
                }
            )
        )

        runBlockingTest {
            createInstance().checkPollingSchedule()
        }

        verify { workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, any(), any()) }
    }

    @Test
    fun `polling needs to be scheduled`() {
        every { repository.familyTestsToRefresh } returns flowOf(
            setOf(
                mockk<FamilyCoronaTest>().apply {
                    every { registeredAt } returns Instant.parse("2021-03-20T06:00:00.000Z")
                    every { type } returns BaseCoronaTest.Type.PCR
                }
            )
        )

        runBlockingTest {
            createInstance().checkPollingSchedule()
        }

        verify { workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, any(), any()) }
    }

    @Test
    fun `cancel worker without tests`() {
        every { repository.familyTestsToRefresh } returns flowOf(emptySet())

        runBlockingTest {
            createInstance().checkPollingSchedule()
        }

        verify { workManager.cancelUniqueWork(PERIODIC_WORK_NAME) }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, any(), any()) }
    }
}
