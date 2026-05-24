package io.github.forcasic.jmeter.allure

import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Regression test for the repeated epic-leak bug.
 *
 * Bug: when a multi-step case is auto-finalized by the next 'start',
 * labels from the previous orphaned case (e.g. epic) leak into the new
 * case unless explicitly removed in the JMX.
 *
 * This test directly inspects the generated Allure result file for
 * 'With JSR223 Sampler step' and asserts that epic is empty.
 * It does NOT depend on expected_results.json, so it cannot be
 * accidentally invalidated by a blind regeneration of that file.
 */
class AllureEpicLeakRegressionSpec extends Specification {

    static File RESULTS_DIR = new File('build/allure-results')

    def "With JSR223 Sampler step must not inherit epic from previous orphaned case"() {
        given: "Integration results must be available"
        if (!RESULTS_DIR.exists()) {
            println "Skipping: no Allure results found. Run './gradlew jmeterRun' first."
            return
        }

        def resultFiles = RESULTS_DIR.listFiles().findAll { it.name.endsWith('-result.json') }
        if (resultFiles.isEmpty()) {
            println "Skipping: no result files in ${RESULTS_DIR}"
            return
        }

        when: "We locate the result for 'With JSR223 Sampler step'"
        def slurper = new JsonSlurper()
        def targetFile = resultFiles.find { f ->
            def d = slurper.parse(f)
            d.get('name', '') == 'With JSR223 Sampler step'
        }

        then: "The test case must exist"
        targetFile != null

        when: "We parse its labels"
        def data = slurper.parse(targetFile)
        def epicLabel = data.get('labels', []).find { it['name'] == 'epic' }

        then: "Epic must be absent or empty"
        epicLabel == null || epicLabel['value'] == ''
    }
}
