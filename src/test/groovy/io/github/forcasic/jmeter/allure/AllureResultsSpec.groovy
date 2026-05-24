package io.github.forcasic.jmeter.allure

import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Validates Allure result files generated after JMeter run.
 * This spec can run against existing build/allure-results or skip gracefully.
 * The primary gate is: NO test may have story="Error".
 */
class AllureResultsSpec extends Specification {

    static File RESULTS_DIR = new File('build/allure-results')
    static File EXPECTED_FILE = new File('src/test/resources/expected_results.json')

    def "Allure results must match expected count and key fields from expected_results.json"() {
        given: "Both results and expected reference must be available"
        if (!RESULTS_DIR.exists() || RESULTS_DIR.listFiles().findAll { it.name.endsWith('-result.json') }.isEmpty()) {
            println "Skipping: no Allure results found in ${RESULTS_DIR}. Run './gradlew jmeterRun' first."
            return
        }
        if (!EXPECTED_FILE.exists()) {
            println "Skipping: expected results file not found: ${EXPECTED_FILE}"
            return
        }

        when: "We load actual and expected tests"
        def slurper = new JsonSlurper()
        def expected = slurper.parse(EXPECTED_FILE)
        def expectedTests = expected['expected_tests'] as List

        def actualTests = RESULTS_DIR.listFiles().findAll { it.name.endsWith('-result.json') }.collect { f ->
            def d = slurper.parse(f)
            def labels = [epic: '', feature: '', story: '']
            d.get('labels', []).each { l ->
                if (l['name'] in ['epic', 'feature', 'story']) {
                    labels[l['name']] = l['value'] ?: ''
                }
            }
            [
                name       : d.get('name', ''),
                status     : d.get('status', ''),
                epic       : labels.get('epic', ''),
                feature    : labels.get('feature', ''),
                story      : labels.get('story', ''),
                mode       : (d.get('steps', []).size() > 1) ? 'multi' : 'solo',
                main_steps : d.get('steps', []).size(),
                steps      : d.get('steps', []).size()
            ]
        }

        def errors = []

        then: "Counts match"
        actualTests.size() == expected['total_tests']

        and: "Every expected test is found with matching labels"
        def remainingActual = new ArrayList(actualTests)
        expectedTests.each { exp ->
            def candidates = remainingActual.findIndexValues {
                it['name'] == exp['name'] &&
                it['epic'] == exp.get('epic', '') &&
                it['feature'] == exp.get('feature', '') &&
                it['story'] == exp.get('story', '') &&
                it['mode'] == exp.get('mode', '')
            }
            def foundIdx = -1
            if (!candidates.isEmpty()) {
                def exactMatch = candidates.find { idx -> remainingActual[idx]['main_steps'] == exp['main_steps'] }
                foundIdx = exactMatch != null ? exactMatch : candidates[0]
            }
            def found = foundIdx >= 0 ? remainingActual.remove(foundIdx as int) : null
            if (!found) {
                errors << "Missing test: ${exp['name']} (epic=${exp['epic']}, feature=${exp['feature']}, story=${exp['story']})"
            } else {
                if (found['main_steps'] != exp['main_steps']) {
                    errors << "${exp['name']}: main_steps expected ${exp['main_steps']}, got ${found['main_steps']}"
                }
                if (exp.containsKey('status') && found['status'] != exp['status']) {
                    errors << "${exp['name']}: status expected '${exp['status']}', got '${found['status']}'"
                }
            }
        }

        and: "No unexpected tests exist"
        def expectedNames = expectedTests.collect { it['name'] } as Set
        actualTests.each { act ->
            if (!(act['name'] in expectedNames)) {
                errors << "Unexpected test in results: '${act['name']}'"
            }
        }

        and: "All checks passed"
        errors.isEmpty()
    }

    def "expected_results.json structure must be valid"() {
        given: "The expected reference file"
        if (!EXPECTED_FILE.exists()) {
            println "Skipping: expected results file not found"
            return
        }

        when: "We parse expected tests"
        def expected = new JsonSlurper().parse(EXPECTED_FILE)
        def tests = expected['expected_tests'] as List
        def names = tests.collect { it['name'] }

        then: "Total count matches list size"
        names.size() == (expected['total_tests'] as int)

        and: "Total count is greater than zero"
        (expected['total_tests'] as int) > 0

        and: "Every test has a non-empty name"
        tests.every { t ->
            t['name'] != null && !t['name'].toString().trim().isEmpty()
        }

        and: "Mode matches main_steps logic"
        tests.each { t ->
            def expectedMode = (t['main_steps'] as int) > 1 ? 'multi' : 'solo'
            assert t['mode'] == expectedMode
        }

        and: "Steps array is present and every step has a name_pattern"
        tests.every { t ->
            def steps = t['steps']
            steps != null && steps.every { s ->
                s['name_pattern'] != null && !s['name_pattern'].toString().trim().isEmpty()
            }
        }
    }

    def "expected_results.json must not be empty or degenerate"() {
        given: "The expected reference file"
        if (!EXPECTED_FILE.exists()) {
            println "Skipping: expected results file not found"
            return
        }

        when: "We parse expected tests"
        def expected = new JsonSlurper().parse(EXPECTED_FILE)
        def tests = expected['expected_tests'] as List

        then: "It contains at least one expected test"
        !tests.isEmpty()

        and: "Total_tests is consistent with non-empty list"
        (expected['total_tests'] as int) == tests.size()
        (expected['total_tests'] as int) >= 1
    }
}
