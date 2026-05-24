package io.github.forcasic.jmeter.allure

import groovy.json.JsonSlurper

/**
 * Validates generated Allure result files against an expected reference state.
 * Replaces the legacy validate.sh Python/Bash script.
 *
 * Usage: AllureResultValidator <results-dir> <expected-json>
 */
class AllureResultValidator {

    static void main(String[] args) {
        if (args.length < 2) {
            println "Usage: AllureResultValidator <results-dir> <expected-json>"
            System.exit(1)
        }

        def resultsDir = new File(args[0])
        def expectedFile = new File(args[1])

        if (!resultsDir.exists()) {
            println "ERROR: Results directory does not exist: ${resultsDir}"
            System.exit(1)
        }

        if (!expectedFile.exists()) {
            println "ERROR: Expected results file does not exist: ${expectedFile}"
            System.exit(1)
        }

        def result = validate(resultsDir, expectedFile)
        System.exit(result.success ? 0 : 1)
    }

    /**
     * Validates Allure results against expected reference.
     * Returns a map with validation outcome (unit-test friendly).
     */
    static Map validate(File resultsDir, File expectedFile) {
        def slurper = new JsonSlurper()
        def expected = slurper.parse(expectedFile)
        def expectedTests = expected['expected_tests'] as List

        def resultFiles = resultsDir.listFiles().findAll { it.name.endsWith('-result.json') }.sort()
        def actualTests = resultFiles.collect { f ->
            def d = slurper.parse(f)
            def test = [
                name       : d.get('name', ''),
                status     : d.get('status', ''),
                description: d.get('description', ''),
                start      : d.get('start', 0),
                epic       : '',
                feature    : '',
                story      : '',
                mode       : (d.get('steps', []).size() > 1) ? 'multi' : 'solo',
                main_steps : d.get('steps', []).size(),
                steps      : [],
                has_numeric_timestamps: true
            ]
            d.get('labels', []).each { l ->
                if (l['name'] in ['epic', 'feature', 'story']) {
                    test[l['name']] = l['value']
                }
            }
            d.get('steps', []).each { s ->
                def stepName = s.get('name', '')
                def namePattern = stepName.contains(':') ? stepName.split(':')[0] : stepName.take(30)
                test['steps'] << [
                    name           : stepName,
                    name_pattern   : namePattern,
                    sub_steps      : s.get('steps', []).size(),
                    has_attachments: s.get('attachments', []).size() >= 2
                ]
                if (s.get('start') instanceof String || s.get('stop') instanceof String) {
                    test['has_numeric_timestamps'] = false
                }
            }
            return test
        }

        def errors = []
        def warnings = []
        def passed = 0

        def expectedCount = expected['total_tests'] as int
        def actualCount = actualTests.size()
        if (actualCount != expectedCount) {
            errors << "Test count: expected ${expectedCount}, got ${actualCount}"
        } else {
            passed++
        }

        def remainingActualTests = new ArrayList(actualTests)

        expectedTests.each { exp ->
            // Find all candidates matching the key fields
            def candidates = remainingActualTests.findIndexValues {
                it['name'] == exp['name'] &&
                it['epic'] == exp.get('epic', '') &&
                it['feature'] == exp.get('feature', '') &&
                it['story'] == exp.get('story', '') &&
                it['mode'] == exp.get('mode', '')
            }
            def foundIdx = -1
            if (!candidates.isEmpty()) {
                // Prefer candidate whose main_steps matches expected
                def exactMatch = candidates.find { idx -> remainingActualTests[idx]['main_steps'] == exp['main_steps'] }
                foundIdx = exactMatch != null ? exactMatch : candidates[0]
            }
            def found = foundIdx >= 0 ? remainingActualTests.remove(foundIdx as int) : null
            if (!found) {
                errors << "Expected test '${exp['name']}' (epic='${exp.get('epic', '')}', feature='${exp.get('feature', '')}', story='${exp.get('story', '')}') NOT FOUND in results"
                return
            }

            if (found['main_steps'] != exp['main_steps']) {
                errors << "'${exp['name']}': main_steps expected ${exp['main_steps']}, got ${found['main_steps']}"
            } else {
                passed++
            }

            if (exp.containsKey('status') && found['status'] != exp['status']) {
                errors << "'${exp['name']}': status expected '${exp['status']}', got '${found['status']}'"
            } else if (exp.containsKey('status')) {
                passed++
            }

            if (exp.containsKey('description_contains')) {
                def desc = found['description'] ?: ''
                if (!desc.contains(exp['description_contains'])) {
                    errors << "'${exp['name']}': description does not contain '${exp['description_contains']}'"
                } else {
                    passed++
                }
            }

            if (exp.containsKey('start_must_be_zero') && exp['start_must_be_zero']) {
                def start = found['start'] ?: 0
                if (start != 0) {
                    errors << "'${exp['name']}': expected start == 0, got ${start}"
                } else {
                    passed++
                }
            }

            if (exp.containsKey('epic') && found['epic'] != exp['epic']) {
                errors << "'${exp['name']}': epic expected '${exp['epic']}', got '${found['epic']}'"
            } else if (exp.containsKey('epic')) {
                passed++
            }

            exp['steps'].eachWithIndex { expStep, j ->
                if (j >= found['steps'].size()) {
                    errors << "'${exp['name']}' step[${j}]: MISSING (expected '${expStep['name_pattern']}')"
                    return
                }
                def actStep = found['steps'][j]
                if (!actStep['name'].contains(expStep['name_pattern'])) {
                    warnings << "'${exp['name']}' step[${j}]: name expected '${expStep['name_pattern']}', got '${actStep['name']}'"
                }
                if (actStep['sub_steps'] != expStep['sub_steps']) {
                    errors << "'${exp['name']}' step[${j}] '${actStep['name']}': sub_steps expected ${expStep['sub_steps']}, got ${actStep['sub_steps']}"
                } else {
                    passed++
                }
                if (expStep['has_attachments'] && !actStep['has_attachments']) {
                    errors << "'${exp['name']}' step[${j}]: missing attachments"
                }
            }

            if (!found['has_numeric_timestamps']) {
                errors << "'${exp['name']}': step timestamps are strings instead of numbers"
            }
        }

        // Note: story='Error' is intentionally set in JMX for some tests and is allowed

        def expectedNames = expectedTests.collect { it['name'] } as Set
        remainingActualTests.each { act ->
            if (!(act['name'] in expectedNames)) {
                warnings << "Unexpected test in results: '${act['name']}'"
            }
        }

        println "  Expected: ${expectedCount} tests"
        println "  Actual:   ${actualCount} tests"
        println "  Checks passed: ${passed}"
        println "  Errors: ${errors.size()}"
        println "  Warnings: ${warnings.size()}"
        println ""

        if (errors) {
            println "  ERRORS:"
            errors.each { e -> println "    - ${e}" }
            println ""
        }

        if (warnings) {
            println "  WARNINGS:"
            warnings.each { w -> println "    - ${w}" }
            println ""
        }

        def success = errors.isEmpty()
        println success ? "RESULT: PASS" : "RESULT: FAIL"

        return [
            success      : success,
            errors       : errors,
            warnings     : warnings,
            passed       : passed,
            expectedCount: expectedCount,
            actualCount  : actualCount
        ]
    }
}
