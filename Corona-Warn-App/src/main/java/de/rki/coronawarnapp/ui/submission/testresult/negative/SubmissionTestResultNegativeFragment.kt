package de.rki.coronawarnapp.ui.submission.testresult.negative

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import de.rki.coronawarnapp.R
import de.rki.coronawarnapp.coronatest.server.CoronaTestResult
import de.rki.coronawarnapp.coronatest.type.BaseCoronaTest
import de.rki.coronawarnapp.coronatest.type.PersonalCoronaTest
import de.rki.coronawarnapp.coronatest.type.rapidantigen.RACoronaTest
import de.rki.coronawarnapp.covidcertificate.test.ui.details.TestCertificateDetailsFragment
import de.rki.coronawarnapp.databinding.FragmentSubmissionTestResultNegativeBinding
import de.rki.coronawarnapp.familytest.core.model.FamilyCoronaTest
import de.rki.coronawarnapp.reyclebin.ui.dialog.RecycleBinDialogType
import de.rki.coronawarnapp.reyclebin.ui.dialog.show
import de.rki.coronawarnapp.util.TimeAndDateExtensions.toDayFormat
import de.rki.coronawarnapp.util.TimeAndDateExtensions.toUserTimeZone
import de.rki.coronawarnapp.util.di.AutoInject
import de.rki.coronawarnapp.util.ui.observe2
import de.rki.coronawarnapp.util.ui.popBackStack
import de.rki.coronawarnapp.util.ui.viewBinding
import de.rki.coronawarnapp.util.viewmodel.CWAViewModelFactoryProvider
import de.rki.coronawarnapp.util.viewmodel.cwaViewModelsAssisted
import javax.inject.Inject

class SubmissionTestResultNegativeFragment : Fragment(R.layout.fragment_submission_test_result_negative), AutoInject {

    private val navArgs by navArgs<SubmissionTestResultNegativeFragmentArgs>()

    @Inject lateinit var viewModelFactory: CWAViewModelFactoryProvider.Factory
    private val viewModel: SubmissionTestResultNegativeViewModel by cwaViewModelsAssisted(
        factoryProducer = { viewModelFactory },
        constructorCall = { factory, _ ->
            factory as SubmissionTestResultNegativeViewModel.Factory
            factory.create(navArgs.testIdentifier)
        }
    )

    private val binding: FragmentSubmissionTestResultNegativeBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            submissionTestResultButtonNegativeRemoveTest.setOnClickListener {
                showMoveToRecycleBinDialog()
            }
            toolbar.setNavigationOnClickListener { popBackStack() }
            testCertificateCard.setOnClickListener { viewModel.onCertificateClicked() }
        }

        viewModel.testResult.observe2(this) { uiState ->
            val coronaTest = uiState.coronaTest
            showTestResult(coronaTest)
            with(binding) {
                when (coronaTest) {
                    is FamilyCoronaTest -> {
                        familyMemberName.isVisible = true
                        familyMemberName.text = coronaTest.personName
                        toolbar.title = getText(R.string.submission_test_result_headline)

                        testResultStepsTestAdded.setEntryText("")
                        testResultStepsRemoveTest.isVisible = false
                        testResultStepsTestCertificate.setEntryTitle(
                            getText(
                                R.string.submission_family_test_result_pending_steps_certificate_heading
                            )
                        )
                        testResultStepsTestCertificate.setEntryText(
                            getText(
                                R.string.submission_family_test_result_negative_steps_certificate_text
                            )
                        )
                    }
                    is PersonalCoronaTest -> {
                        familyMemberName.isVisible = false
                        toolbar.title = getText(R.string.submission_test_result_toolbar_text)
                    }
                }

                when (coronaTest.type) {
                    BaseCoronaTest.Type.PCR -> {
                        testResultStepsTestAdded.setEntryTitle(
                            getText(
                                R.string.submission_family_test_result_steps_added_pcr_heading
                            )
                        )

                        testResultStepsNegativeResult.setEntryText(
                            getText(
                                R.string.submission_test_result_negative_steps_negative_body
                            )
                        )
                    }
                    BaseCoronaTest.Type.RAPID_ANTIGEN -> {
                        testResultStepsTestAdded.setEntryTitle(
                            getText(
                                R.string.submission_family_test_result_steps_added_rat_heading
                            )
                        )

                        testResultStepsNegativeResult.setEntryText(
                            getText(
                                R.string.coronatest_negative_antigen_result_second_info_body
                            )
                        )
                    }
                }

                // test certificate state
                when (uiState.certificateState) {
                    SubmissionTestResultNegativeViewModel.CertificateState.NOT_REQUESTED -> {
                        testResultStepsRemoveTest.setIsFinal(true)
                        testResultStepsTestCertificate.isGone = true
                        testCertificateCard.isGone = true
                    }
                    SubmissionTestResultNegativeViewModel.CertificateState.PENDING -> {
                        testResultStepsRemoveTest.setIsFinal(false)
                        testResultStepsTestCertificate.isGone = false
                        testResultStepsTestCertificate.setEntryText(
                            getText(
                                R.string.submission_test_result_pending_steps_test_certificate_not_available_yet_body
                            )
                        )
                        testResultStepsTestCertificate.setIcon(
                            getDrawable(requireContext(), R.drawable.ic_result_pending_certificate_info)
                        )
                        testCertificateCard.isGone = true
                    }
                    SubmissionTestResultNegativeViewModel.CertificateState.AVAILABLE -> {
                        testResultStepsRemoveTest.setIsFinal(false)
                        testResultStepsTestCertificate.isGone = false
                        testResultStepsTestCertificate.setEntryText(
                            getText(R.string.coronatest_negative_result_certificate_info_body)
                        )
                        testResultStepsTestCertificate.setIcon(
                            getDrawable(requireContext(), R.drawable.ic_qr_code_illustration)
                        )
                        testCertificateCard.isGone = false
                    }
                }
            }
        }

        viewModel.events.observe(viewLifecycleOwner) {
            when (it) {
                is SubmissionTestResultNegativeNavigation.Back -> popBackStack()
                is SubmissionTestResultNegativeNavigation.OpenTestCertificateDetails ->
                    findNavController().navigate(TestCertificateDetailsFragment.uri(it.containerId.qrCodeHash))
            }
        }

        viewModel.certificate.observe(viewLifecycleOwner) { certificate ->
            if (certificate?.isPCRTestCertificate == true) {
                R.string.test_certificate_pcr_test_type
            } else {
                R.string.test_certificate_rapid_test_type
            }.also { binding.testCertificateType.setText(it) }

            binding.certificateDate.text = getString(
                R.string.test_certificate_sampled_on,
                certificate?.sampleCollectedAt?.toUserTimeZone()?.toDayFormat()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        binding.submissionTestResultContainer.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
    }

    private fun showMoveToRecycleBinDialog() {
        RecycleBinDialogType.RecycleTestConfirmation.show(
            fragment = this,
            positiveButtonAction = { viewModel.moveTestToRecycleBinStorage() }
        )
    }

    private fun showTestResult(test: BaseCoronaTest) {
        with(binding) {
            when {
                test is RACoronaTest && test.testResult == CoronaTestResult.RAT_NEGATIVE -> {
                    submissionTestResultSection.isVisible = false

                    personalRapidTestResultNegative.isVisible = true
                    personalRapidTestResultNegative.setTestResultSection(test)
                }
                else -> {
                    personalRapidTestResultNegative.isVisible = false

                    submissionTestResultSection.isVisible = true
                    submissionTestResultSection.setTestResultSection(test)
                }
            }
        }
    }
}
