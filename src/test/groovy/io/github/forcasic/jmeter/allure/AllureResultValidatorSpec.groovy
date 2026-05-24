package io.github.forcasic.jmeter.allure

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Unit tests for AllureResultValidator.
 * Covers mismatch scenarios that must be caught before integration tests run.
 */
class AllureResultValidatorSpec extends Specification {

    @TempDir
    File tempDir

    File resultsDir
    File expectedFile

    def setup() {
        resultsDir = new File(tempDir, 'allure-results')
        resultsDir.mkdirs()
        expectedFile = new File(tempDir, 'expected.json')
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private File writeResult(String name, Map data) {
        def f = new File(resultsDir, "${name}-result.json")
        f.text = groovy.json.JsonOutput.toJson(data)
        return f
    }

    private void writeExpected(Map data) {
        expectedFile.text = groovy.json.JsonOutput.toJson(data)
    }

    private Map makeTest(String name, List steps = [], String status = 'passed',
                         String epic = '', String feature = '', String story = '') {
        return [
            name  : name,
            status: status,
            labels: [
                [name: 'epic', value: epic],
                [name: 'feature', value: feature],
                [name: 'story', value: story]
            ].findAll { it.value },
            steps : steps
        ]
    }

    private Map makeStep(String name, List subSteps = [], List attachments = [],
                         def start = 1234567890123L, def stop = 1234567890124L) {
        return [
            name       : name,
            steps      : subSteps,
            attachments: attachments,
            start      : start,
            stop       : stop
        ]
    }

    // ------------------------------------------------------------------
    // Success scenario
    // ------------------------------------------------------------------

    def "validate returns success when actual and expected match perfectly"() {
        given:
        def result = makeTest('Login', [makeStep('POST /login', [], [[:], [:]])],
                              'passed', 'Auth', 'API', 'Positive')
        writeResult('uuid-1', result)
        writeExpected([
            total_tests   : 1,
            expected_tests: [
                [
                    name          : 'Login',
                    epic          : 'Auth',
                    feature       : 'API',
                    story         : 'Positive',
                    status        : 'passed',
                    mode          : 'solo',
                    main_steps    : 1,
                    steps         : [
                        [name_pattern: 'POST /login', sub_steps: 0, has_attachments: true]
                    ]
                ]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        outcome.success
        outcome.errors.isEmpty()
        outcome.actualCount == 1
        outcome.expectedCount == 1
    }

    // ------------------------------------------------------------------
    // Count mismatch — the exact bug that required expected_results.json rebuild
    // ------------------------------------------------------------------

    def "validate fails when actual test count differs from expected total_tests"() {
        given:
        writeResult('uuid-1', makeTest('A', [], 'passed'))
        writeResult('uuid-2', makeTest('B', [], 'passed'))
        writeExpected([
            total_tests   : 1,
            expected_tests: [
                [name: 'A', mode: 'solo', main_steps: 1, steps: []]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains('Test count: expected 1, got 2') }
    }

    def "validate fails when expected_results.json is empty but results exist"() {
        given:
        writeResult('uuid-1', makeTest('A', [], 'passed'))
        writeExpected([
            total_tests   : 0,
            expected_tests: []
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains('Test count: expected 0, got 1') }
    }

    // ------------------------------------------------------------------
    // Missing / unexpected tests
    // ------------------------------------------------------------------

    def "validate fails when an expected test is missing from results"() {
        given:
        writeResult('uuid-1', makeTest('A', [], 'passed'))
        writeExpected([
            total_tests   : 2,
            expected_tests: [
                [name: 'A', mode: 'solo', main_steps: 1, steps: []],
                [name: 'B', mode: 'solo', main_steps: 1, steps: []]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains("NOT FOUND") && it.contains("'B'") }
    }

    // ------------------------------------------------------------------
    // Field mismatches
    // ------------------------------------------------------------------

    def "validate fails when main_steps does not match"() {
        given:
        writeResult('uuid-1', makeTest('A', [makeStep('S1'), makeStep('S2')], 'passed'))
        writeExpected([
            total_tests   : 1,
            expected_tests: [
                [name: 'A', mode: 'multi', main_steps: 5, steps: [
                    [name_pattern: 'S1', sub_steps: 0, has_attachments: false],
                    [name_pattern: 'S2', sub_steps: 0, has_attachments: false]
                ]]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains("main_steps expected 5, got 2") }
    }

    def "validate fails when status does not match"() {
        given:
        writeResult('uuid-1', makeTest('A', [], 'failed'))
        writeExpected([
            total_tests   : 1,
            expected_tests: [
                [name: 'A', mode: 'solo', main_steps: 1, status: 'passed', steps: []]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains("status expected 'passed', got 'failed'") }
    }

    def "validate fails when sub_steps does not match"() {
        given:
        writeResult('uuid-1', makeTest('A', [makeStep('S1', [makeStep('sub')])], 'passed'))
        writeExpected([
            total_tests   : 1,
            expected_tests: [
                [name: 'A', mode: 'solo', main_steps: 1, steps: [
                    [name_pattern: 'S1', sub_steps: 0, has_attachments: false]
                ]]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains("sub_steps expected 0, got 1") }
    }

    def "validate fails when expected attachments are missing"() {
        given:
        // only one attachment instead of >=2
        writeResult('uuid-1', makeTest('A', [makeStep('S1', [], [[:]])], 'passed'))
        writeExpected([
            total_tests   : 1,
            expected_tests: [
                [name: 'A', mode: 'solo', main_steps: 1, steps: [
                    [name_pattern: 'S1', sub_steps: 0, has_attachments: true]
                ]]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains("missing attachments") }
    }

    def "validate fails when step timestamps are strings instead of numbers"() {
        given:
        writeResult('uuid-1', makeTest('A', [makeStep('S1', [], [], '2023-01-01', '2023-01-02')], 'passed'))
        writeExpected([
            total_tests   : 1,
            expected_tests: [
                [name: 'A', mode: 'solo', main_steps: 1, steps: [
                    [name_pattern: 'S1', sub_steps: 0, has_attachments: false]
                ]]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains("timestamps are strings instead of numbers") }
    }

    // ------------------------------------------------------------------
    // Disambiguation by epic/feature/story
    // ------------------------------------------------------------------

    def "validate distinguishes duplicate names via epic, feature, story"() {
        given:
        writeResult('uuid-1', makeTest('Check capsules', [makeStep('S1')], 'passed', 'E1', 'F1', 'S1'))
        writeResult('uuid-2', makeTest('Check capsules', [makeStep('S2')], 'passed', 'E2', 'F2', 'S2'))
        writeExpected([
            total_tests   : 2,
            expected_tests: [
                [name: 'Check capsules', epic: 'E1', feature: 'F1', story: 'S1',
                 mode: 'solo', main_steps: 1, steps: [
                     [name_pattern: 'S1', sub_steps: 0, has_attachments: false]
                 ]],
                [name: 'Check capsules', epic: 'E2', feature: 'F2', story: 'S2',
                 mode: 'solo', main_steps: 1, steps: [
                     [name_pattern: 'S2', sub_steps: 0, has_attachments: false]
                 ]]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        outcome.success
        outcome.errors.isEmpty()
    }

    def "validate fails when duplicate names exist but epic/feature/story do not match"() {
        given:
        writeResult('uuid-1', makeTest('Check capsules', [], 'passed', 'E1', 'F1', 'S1'))
        writeExpected([
            total_tests   : 1,
            expected_tests: [
                [name: 'Check capsules', epic: 'WRONG', feature: 'F1', story: 'S1',
                 mode: 'solo', main_steps: 1, steps: []]
            ]
        ])

        when:
        def outcome = AllureResultValidator.validate(resultsDir, expectedFile)

        then:
        !outcome.success
        outcome.errors.any { it.contains("NOT FOUND") }
    }
}
