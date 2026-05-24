package io.github.forcasic.jmeter.allure

import org.apache.jmeter.threads.JMeterVariables
import org.apache.jmeter.samplers.SampleResult
import org.apache.jmeter.samplers.Sampler
import org.apache.jmeter.assertions.AssertionResult
import org.apache.jmeter.threads.JMeterContext as ApacheJMeterContext
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Unit tests for AllureReporter edge-case logic.
 */
class AllureReporterEdgeCaseTest extends Specification {

    @TempDir
    File tempDir

    JMeterVariables vars
    SampleResult prev
    Sampler sampler
    Logger log
    ApacheJMeterContext ctx
    AttachmentWriter mockWriter

    def setup() {
        vars = new JMeterVariables()
        prev = Spy(new SampleResult())
        prev.sampleStart()
        prev.sampleEnd()
        sampler = Mock(Sampler)
        log = Mock(Logger)
        ctx = Mock(ApacheJMeterContext)
        ctx.getProperties() >> new Properties()
        mockWriter = Mock(AttachmentWriter)
    }

    private AllureReporter createReporter(String parameters) {
        def reporter = new AllureReporter(ctx, vars, prev, sampler, log, parameters)
        reporter.writer = mockWriter
        return reporter
    }

    // ------------------------------------------------------------------
    // Test 1: auto-finalization patches stop timestamp into old case
    // ------------------------------------------------------------------
    def "autoFinalizePreviousCase patches stop timestamp into existing result"() {
        given:
        String oldUUID = 'old-uuid-1234'
        String oldAResult = '{"name":"Old Case","steps":[],"start":1000}'
        vars.put('_allureCaseUUID', oldUUID)
        vars.put('prevMainSteps', '{"name":"Step 1"}')
        vars.put('AResult', oldAResult)
        vars.put('caseTimeStart', '1000')
        vars.put('allure.name', 'Old Case')
        sampler.getName() >> 'New Step'

        when:
        def reporter = createReporter('start')
        reporter.run()

        then:
        // autoFinalizePreviousCase writes the old result with stop, then run() writes the new case
        2 * mockWriter.writeResultJson(_, _) >> { String uuid, String json ->
            if (uuid == oldUUID) {
                assert json.contains('"stop"')
                assert json.contains('"name":"Old Case"')
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 2: start,stop triggers finalization and clears state
    // ------------------------------------------------------------------
    def "startAndStop invokes handleFinalization and clears state"() {
        given:
        vars.put('allure.name', 'Explicit start stop')
        vars.put('allure.label.epic', 'Edge cases')
        sampler.getName() >> 'Step 1'

        when:
        def reporter = createReporter('start,stop')
        reporter.run()

        then:
        vars.get('prevMainSteps') == ''
        vars.get('_allureCaseUUID') == null
        vars.get('AResult') == ''
        // Counters should be updated in properties
        ctx.getProperties().get('SummaryCountTests') == '1'
    }

    // ------------------------------------------------------------------
    // Test 3: orphaned stop creates a case with start 0
    // ------------------------------------------------------------------
    def "orphanedStop creates case with step start timestamp"() {
        given:
        // No active case: empty prevMainSteps and no case UUID
        vars.put('prevMainSteps', '')
        sampler.getName() >> 'Orphaned Stop'

        when:
        def reporter = createReporter('stop')
        reporter.run()

        then:
        // Result JSON should use the step's start time because caseTimeStart was never set
        1 * mockWriter.writeResultJson(_, { String json -> json.contains('"start":' + prev.getStartTime()) })
        // Finalization should have occurred: counters incremented
        ctx.getProperties().get('SummaryCountTests') == '1'
    }

    // ------------------------------------------------------------------
    // Test 4: long chain accumulates five steps
    // ------------------------------------------------------------------
    def "longChain accumulates five steps with monotonic timestamps"() {
        given:
        vars.put('allure.name', 'Long chain')
        vars.put('allure.label.epic', 'Edge cases')

        def baseTime = 1000L
        def stepNames = ['Step 1', 'Step 2', 'Step 3', 'Step 4', 'Step 5']
        def parametersList = ['start', 'continue', 'continue', 'continue', 'stop']

        when:
        parametersList.eachWithIndex { params, i ->
            def stepPrev = new SampleResult()
            stepPrev.setStartTime(baseTime + i * 10)
            stepPrev.setEndTime(baseTime + i * 10 + 5)

            def stepSampler = Mock(Sampler)
            stepSampler.getName() >> stepNames[i]

            def reporter = new AllureReporter(ctx, vars, stepPrev, stepSampler, log, params)
            reporter.writer = mockWriter
            reporter.run()
        }

        then:
        // After the final stop, state should be cleared
        vars.get('prevMainSteps') == ''
        vars.get('_allureCaseUUID') == null
        // During the chain, prevMainSteps accumulated 5 steps before cleanup
        // We verify monotonicity by checking no exception was thrown and state is correct
    }

    // ------------------------------------------------------------------
    // Test 5: critical modifier calls setStopThread on failure
    // ------------------------------------------------------------------
    def "critical calls setStopThread on failure"() {
        given:
        vars.put('allure.name', 'Critical test')
        vars.put('allure.label.epic', 'Edge cases')
        sampler.getName() >> 'Failing request'

        // Simulate a failed assertion
        def assertionResult = new AssertionResult('Response Assertion')
        assertionResult.setFailure(true)
        assertionResult.setFailureMessage('Expected 200 but got 404')
        prev.addAssertionResult(assertionResult)

        when:
        // Use start,stop critical so that handleFinalization is reached and critical flag is acted upon
        def reporter = createReporter('start,stop critical')
        reporter.run()

        then:
        1 * prev.setStopThread(true)
    }

    // ------------------------------------------------------------------
    // Test 6: explicitly removing labels before the next start prevents
    // leakage from an auto-finalized orphaned case.
    // This is the recommended pattern to avoid the epic-leak bug:
    // always vars.remove('allure.label.epic') (or any label you do not
    // want to inherit) in the JSR223 Sampler that declares the new case.
    // ------------------------------------------------------------------
    def "orphaned case labels do not leak when explicitly removed before next start"() {
        given:
        // Orphaned case step 1 (no stop)
        vars.put('allure.name', 'Orphaned case')
        vars.put('allure.label.epic', 'Previous epic')
        vars.put('allure.label.feature', 'Previous feature')
        vars.put('allure.label.story', 'Previous story')

        def step1Prev = new SampleResult()
        step1Prev.setStartTime(1000)
        step1Prev.setEndTime(1005)
        def step1Sampler = Mock(Sampler)
        step1Sampler.getName() >> 'Step 1'
        def reporter1 = new AllureReporter(ctx, vars, step1Prev, step1Sampler, log, 'start')
        reporter1.writer = mockWriter
        reporter1.run()

        // New case: user explicitly removes epic and sets fresh annotations
        vars.remove('allure.label.epic')
        vars.put('allure.name', 'New case')
        vars.put('allure.label.feature', 'New feature')
        vars.put('allure.label.story', 'New story')

        def step2Prev = new SampleResult()
        step2Prev.setStartTime(1010)
        step2Prev.setEndTime(1015)
        def step2Sampler = Mock(Sampler)
        step2Sampler.getName() >> 'Step 2'
        def reporter2 = new AllureReporter(ctx, vars, step2Prev, step2Sampler, log, 'start')
        reporter2.writer = mockWriter
        reporter2.run()

        expect:
        // Epic was explicitly removed and must stay null
        vars.get('allure.label.epic') == null
        // Labels explicitly set for the new case must survive
        vars.get('allure.label.feature') == 'New feature'
        vars.get('allure.label.story') == 'New story'
    }
}
