/*
 * Copyright 2026 Forcasic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.forcasic.jmeter.allure

import java.time.LocalDateTime
import java.util.regex.Matcher
import groovy.json.JsonSlurper

/**
 * Main Allure reporter logic for JMeter.
 */
class AllureReporter {

    JMeterContext jm
    AttachmentWriter writer
    String version = '1.0.0'

    // Internal state (previously script-level variables)
    String allureDisplayName = ''
    String allureCaseDescription = ''
    String allureCaseFailReason = ''
    String allureStepFailReason = ''
    String allureStepDisplayName = ''
    String allureStepResult = ''
    String allureCaseResult = ''
    String labels = ''
    String links = ''
    String mainParameters = ''
    String stepParameters = ''
    String summarySubSteps = ''
    String allureFullName = ''
    String requestType = ''
    String responseType = ''
    String requestData = ''
    String responseData = ''
    String stage = 'finished'
    boolean solotest = false
    String critical = ''
    int summaryCountTests = 0
    int passedCountTests = 0
    int failedCountTests = 0
    int skippedCountTests = 0
    Set<String> leakedLabelKeys = new HashSet<>()

    AllureReporter(ctx, vars, prev, sampler, log, String parameters) {
        this.jm = new JMeterContext(ctx, vars, prev, sampler, log, parameters)
        this.writer = new AttachmentWriter(jm.getReportPath())
    }

    void run() {
        long prevStop = jm.prev.getEndTime()
        Map<String, String> preservedAnnotations = null

        if ((jm.isEffectiveStart() || jm.isSolo()) && !jm.getPrevMainSteps().isEmpty() && jm.getCaseUUID() != null) {
            jm.log.warn("run-before-auto: prevUUID=${jm.getCaseUUID()}, prevMainStepsSize=${jm.getPrevMainSteps().size()}, prevAResultLen=${jm.vars.get('AResult')?.length()}")
            autoFinalizePreviousCase(prevStop)
        }

        if (jm.isEffectiveStart() || jm.isSolo()) {
            preservedAnnotations = jm.preserveAnnotations()
            computeLeakedLabelKeys(preservedAnnotations)
        }

        init()
        handleFeatureSuffix()
        resolveRequestResponseData()
        resolveContentTypes()
        resolveModeAndUUID(preservedAnnotations)
        handleTika()

        // Set case start timestamp for new cases
        if (jm.isEffectiveStart() || jm.isSolo()) {
            long caseStart = jm.prev.getStartTime()
            String lastCaseStartStr = jm.getLastCaseStart()
            long lastCaseStart = (lastCaseStartStr != null && !lastCaseStartStr.isEmpty()) ? lastCaseStartStr.toLong() : 0L
            if (caseStart <= lastCaseStart) { caseStart = lastCaseStart + 1 }
            jm.setCaseTimeStart(caseStart.toString())
            jm.setLastCaseStart(caseStart.toString())
        }

        // Orphaned stop: case was never started
        if (jm.isStop() && !jm.isStart() && jm.getCaseTimeStart() == null) {
            jm.setCaseTimeStart('0')
        }

        if (jm.isSolo()) {
            jm.setAllureCaseResult('passed')
            jm.setPrevMainSteps('')
            allureDisplayName = jm.sampler.getName()
            solotest = true
            String comment = jm.sampler.getComment()
            allureCaseDescription = comment != null ? comment : ''
        }

        mainParameters = AllureResultBuilder.buildMainParameters(jm)
        stepParameters = AllureResultBuilder.buildStepParameters(jm.parameters, jm)

        addAllSteps()

        boolean addComma = !jm.isEffectiveStart() && !jm.isSolo()

        addMoreMainStep(addComma)

        if (!jm.hasModifier('no_report')) {
            writeAttachments()
            handleLoopCounter()
            writeResult()
            if (jm.isSolo() || jm.isStop()) {
                handleFinalization()
            }
        }
    }

    // ------------------------------------------------------------------
    // Init
    // ------------------------------------------------------------------
    void init() {
        allureCaseFailReason = jm.getAllureCaseFailReason()
        stage = 'finished'

        summaryCountTests = (jm.getProp('SummaryCountTests') ?: '0').toInteger()
        passedCountTests = (jm.getProp('PassedCountTests') ?: '0').toInteger()
        failedCountTests = (jm.getProp('FailedCountTests') ?: '0').toInteger()
        skippedCountTests = (jm.getProp('SkippedCountTests') ?: '0').toInteger()

        critical = jm.getCritical()
    }

    // ------------------------------------------------------------------
    // Feature suffix
    // ------------------------------------------------------------------
    void handleFeatureSuffix() {
        String featureSuffix = jm.getFeatureSuffix()
        if (featureSuffix != null && !featureSuffix.isEmpty()) {
            String currentFeature = jm.vars.get("allure.label.feature")
            if (currentFeature != null && !currentFeature.endsWith(featureSuffix)) {
                jm.vars.put("allure.label.feature", currentFeature + featureSuffix)
                jm.log.info("Feature changed: " + jm.vars.get("allure.label.feature"))
            }
        }
    }

    // ------------------------------------------------------------------
    // Request / Response data & content types
    // ------------------------------------------------------------------
    void resolveRequestResponseData() {
        boolean hasRequestContentType = !jm.prev.getRequestHeaders().findAll("[cC]ontent-[tT]ype:?(.*)").toString().contains('[]')
        boolean isHttp = jm.sampler.getClass().getName().contains('HTTPSampler')

        if (hasRequestContentType && isHttp) {
            requestData = jm.sampler.getUrl().toString() + '\n\n' +
                    jm.prev.getRequestHeaders().replaceAll(/[aA]uthorization:.*/, "Authorization: XXX (Has been replaced for safety)").
                            replaceAll(/[xX]-[aA]pi-[tT]oken:.*/, "X-Api-Token: XXX (Has been replaced for safety)") + '\n' +
                    jm.prev.getHTTPMethod() + ":" + '\n' + jm.prev.getQueryString()
        } else {
            requestData = jm.prev.getSamplerData()
        }

        responseData = jm.prev.getResponseDataAsString()
    }

    void resolveContentTypes() {
        boolean hasRequestContentType = !jm.prev.getRequestHeaders().findAll("[cC]ontent-[tT]ype:?(.*)").toString().contains('[]')
        boolean isHttp = jm.sampler.getClass().getName().contains('HTTPSampler')

        if (hasRequestContentType && isHttp) {
            requestType = jm.prev.getRequestHeaders().findAll("[cC]ontent-[tT]ype:?(.*)").toString().replaceAll('].*', "")
                    .replaceAll(';.*', "").replaceAll(".*:", "").replaceAll(" ", "")
        } else {
            requestType = 'application/json'
        }

        if (jm.prev.getContentType().replaceAll(";.*", "").contains('/')) {
            responseType = jm.prev.getContentType().replaceAll(";.*", "")
        } else {
            responseType = 'application/json'
        }

        if (jm.parameters.contains('response_content_type=')) {
            if (jm.parameters =~ ~/response_content_type=\[(.+?)\]/) {
                def contentMemory = Matcher.lastMatcher[0][1].split(',')
                responseType = contentMemory[0]
            }
        }

        if (jm.parameters.contains('request_content_type=')) {
            if (jm.parameters =~ ~/request_content_type=\[(.+?)\]/) {
                def contentMemory = Matcher.lastMatcher[0][1].split(',')
                requestType = contentMemory[0]
            }
        }
    }

    // ------------------------------------------------------------------
    // Mode & UUID management
    // ------------------------------------------------------------------
    void resolveModeAndUUID(Map<String, String> preserved = null) {
        if (jm.isEffectiveStart()) {
            Map<String, String> preservedLocal = preserved ?: jm.preserveAnnotations()
            jm.log.warn("preserveAnnotations keys: ${preservedLocal.keySet()}")
            jm.clearAllureState()
            jm.log.warn("clearAllureState called")
            jm.setPrevMainSteps('')
            jm.log.warn("restoreAnnotations with leakedLabelKeys: ${leakedLabelKeys}")
            jm.restoreAnnotations(preservedLocal, leakedLabelKeys)
        }

        String attachUUID
        if (jm.isEffectiveStart() || jm.isSolo()) {
            attachUUID = UUID.randomUUID().toString()
            jm.setCaseUUID(attachUUID)
        } else {
            attachUUID = jm.getCaseUUID()
            if (attachUUID == null || attachUUID.isEmpty()) {
                attachUUID = UUID.randomUUID().toString()
                jm.setCaseUUID(attachUUID)
            }
        }

        String attachStepUUID = UUID.randomUUID().toString()
        jm.setStepUUID(attachStepUUID)
    }

    // ------------------------------------------------------------------
    // Tika support
    // ------------------------------------------------------------------
    void handleTika() {
        if (jm.parameters.contains('tika_xml')) {
            byte[] samplerData = jm.ctx.getPreviousResult().getResponseData()
            String converted = org.apache.jmeter.util.Document.getTextFromDocument(samplerData)
            def m = (converted =~ /sharedStrings.xml\n(.*)/)
            if (m) {
                responseData = m[0].toString()
            } else {
                responseData = converted.toString()
            }
            responseType = 'text/plain'
        }
    }

    // ------------------------------------------------------------------
    // Steps (assertions)
    // ------------------------------------------------------------------
    void addAllSteps() {
        int countAssertions = jm.prev.getAssertionResults().size().toInteger()
        jm.vars.putObject("countAssertions", countAssertions)

        String timeoutFailureText = 'java.net.SocketTimeoutException: Read timed out'

        for (int i = 0; i < countAssertions; i++) {
            def assertionResult = jm.prev.getAssertionResults()[i]

            if (assertionResult.isFailure()) {
                jm.log.info("**** org.allure.reporter: " + ("****" * i) + '[' + i + '] Step: ' + assertionResult.toString()
                        + ': failed; reason: ' + assertionResult.getFailureMessage().toString())

                allureStepDisplayName = assertionResult.toString()

                if (responseData.contains(timeoutFailureText)) {
                    allureStepFailReason = jm.prev.getQueryString().findAll('"method"?"(.*)"').toString().replace("\"", "") + ' ' + timeoutFailureText
                    allureCaseFailReason = allureStepFailReason
                } else if (allureCaseFailReason.contains(timeoutFailureText)) {
                    allureCaseFailReason = allureCaseFailReason
                } else {
                    allureStepFailReason = assertionResult.getFailureMessage().toString()
                    String allureMainFailReason = '[Sample: ' + jm.sampler.getName() + ' in sub step: ' +
                            allureStepDisplayName + ' failed with reason: ' + assertionResult
                            .getFailureMessage().toString() + ']' + '\\' + 'n'
                    allureCaseFailReason = allureCaseFailReason + allureMainFailReason
                }

                jm.setAllureCaseFailReason(allureCaseFailReason)
                allureCaseResult = 'failed'
                jm.setAllureCaseResult('failed')
                allureStepResult = 'failed'

                addMoreSubStep()
            } else {
                jm.log.info("**** org.allure.reporter: " + ("****" * i) + '[' + i + '] Step: ' + assertionResult.toString()
                        + ': passed')
                allureStepDisplayName = assertionResult.toString()
                allureStepResult = 'passed'
                allureStepFailReason = ''
                addMoreSubStep()
            }
        }

        if (countAssertions == 0) {
            if (responseData.contains(timeoutFailureText)) {
                allureCaseFailReason = jm.prev.getQueryString().findAll('"method"?"(.*)"').toString().replace("\"", "") + ' ' + timeoutFailureText
                jm.setAllureCaseFailReason(allureCaseFailReason)
            }
        }
    }

    void addMoreSubStep() {
        if (!summarySubSteps.empty) { summarySubSteps = summarySubSteps + ',' }
        summarySubSteps += AllureResultBuilder.buildSubStep(allureStepDisplayName, allureStepResult, stage, allureStepFailReason)
    }

    // ------------------------------------------------------------------
    // Main step & test-case JSON
    // ------------------------------------------------------------------
    void addMoreMainStep(boolean addPoint) {
        if (summarySubSteps.contains('"status":"failed"')) { allureStepResult = 'failed' }
        else { allureStepResult = 'passed' }

        String prevMainSteps = jm.getPrevMainSteps()
        if (addPoint && prevMainSteps != '') { prevMainSteps = prevMainSteps + ',' }

        // Monotonic timestamp protection
        long stepStart = jm.prev.getStartTime()
        long stepStop = jm.prev.getEndTime()
        String lastStartStr = jm.getLastStepStart()
        long lastStart = (lastStartStr != null && !lastStartStr.isEmpty()) ? lastStartStr.toLong() : 0L
        if (stepStart <= lastStart) {
            long originalDuration = stepStop - stepStart
            stepStart = lastStart + 1
            stepStop = stepStart + Math.max(originalDuration, 1)
        }
        jm.setLastStepStart(stepStart.toString())

        String stepJson = AllureResultBuilder.buildMainStep(
                jm.sampler.getName(),
                allureStepResult,
                stage,
                summarySubSteps,
                '',
                jm.vars.get('attachment-UUID'),
                requestType,
                responseType,
                stepParameters,
                stepStart,
                stepStop
        )

        prevMainSteps += stepJson

        if (prevMainSteps.contains('"status":"failed"')) {
            allureCaseResult = 'failed'
        } else {
            allureCaseResult = 'passed'
        }

        if (jm.parameters.contains('skipped')) {
            allureCaseResult = 'skipped'
        }

        if (jm.parameters.contains('critical') && allureStepResult == 'failed') {
            jm.setCritical('yes')
            critical = 'yes'
        }

        resolveMainFields()
        if (!allureDisplayName || allureDisplayName.empty) {
            allureDisplayName = jm.sampler.getName()
        }

        allureFullName = AllureResultBuilder.buildFullName(jm, allureDisplayName)
        labels = AllureResultBuilder.buildLabels(jm, jm.parameters)
        links = AllureResultBuilder.buildLinks(jm, jm.parameters)

        String caseTimeStartStr = jm.getCaseTimeStart()
        if (caseTimeStartStr == null || caseTimeStartStr.isEmpty()) {
            jm.setCaseTimeStart(stepStart.toString())
            caseTimeStartStr = stepStart.toString()
        }
        long caseStart = caseTimeStartStr.toLong()

        String aResult = AllureResultBuilder.buildTestCaseJson(
                jm,
                allureDisplayName,
                allureCaseDescription,
                allureCaseResult,
                allureCaseFailReason,
                stage,
                prevMainSteps,
                caseStart,
                jm.prev.getEndTime(),
                jm.getCaseUUID(),
                caseTimeStartStr + '_' + JsonUtils.escapeJson(allureFullName),
                allureFullName,
                mainParameters,
                labels,
                links
        )

        jm.setPrevMainSteps(prevMainSteps)
        jm.setVar('AResult', aResult)
    }

    void resolveMainFields() {
        jm.vars.entrySet().each { var ->
            String key = var.getKey()
            if (key =~ 'allure.' && !solotest) {
                if (key.replaceAll('allure.', '') == 'name') {
                    allureDisplayName = var.getValue()
                }
                if (key.replaceAll('allure.', '') == 'description') {
                    allureCaseDescription = var.getValue()
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------
    void writeAttachments() {
        try {
            writer.writeRequestAttachment(jm.vars.get('attachment-UUID'), requestData, requestType)

            boolean isBinary = JsonUtils.isBinaryContentType(responseType) ||
                    jm.sampler.getClass().getName().contains('JSR223Sampler')
            writer.writeResponseAttachment(
                    jm.vars.get('attachment-UUID'),
                    jm.prev.getResponseData(),
                    responseType,
                    isBinary
            )
        } catch (IOException ioEx) {
            jm.log.error("Allure reporter: failed to write attachment files: " + ioEx.getMessage())
        }
    }

    // ------------------------------------------------------------------
    // Loop counter
    // ------------------------------------------------------------------
    void handleLoopCounter() {
        String loopFailureText = 'org.jmeter.com.LoopException: test ' + allureFullName + ' is looped. More then 100 steps.'
        int loopCounter = 1
        if (jm.getLoopCounter() == null) {
            jm.setLoopCounter(1)
        } else {
            loopCounter = jm.getLoopCounter().toInteger() + 1
            jm.setLoopCounter(loopCounter)
        }

        if (loopCounter > 100) {
            println('' + LocalDateTime.now() + '\tWARN' + '\t[test: ' + summaryCountTests + ']' + '\t[thread: ' +
                    jm.prev.getThreadName().toString().toLowerCase() + ']\t' + '\t[' + loopFailureText + ' step: ' + JsonUtils.escapeJson(jm.sampler.getName()) + ']')
        }
    }

    // ------------------------------------------------------------------
    // Result JSON file
    // ------------------------------------------------------------------
    void writeResult() {
        try {
            String aResult = jm.vars.get('AResult') ?: ''
            jm.log.warn("writeResult: caseUUID=${jm.getCaseUUID()}, aResultLen=${aResult.length()}")
            writer.writeResultJson(jm.getCaseUUID(), aResult)
        } catch (IOException ioEx) {
            jm.log.error('Allure reporter: failed to write result file: ' + ioEx.getMessage())
        }
    }

    void autoFinalizePreviousCase(long forcedStopTime) {
        String prevUUID = jm.getCaseUUID()
        String prevAResult = jm.vars.get('AResult') ?: ''
        if (prevUUID == null || prevUUID.isEmpty() || prevAResult.isEmpty()) {
            jm.log.warn("autoFinalizePreviousCase: skipping, prevUUID=${prevUUID}, prevAResultEmpty=${prevAResult.isEmpty()}")
            return
        }

        String updatedAResult
        if (prevAResult.contains('"stop"')) {
            updatedAResult = prevAResult.replaceFirst(/"stop":\d+/, "\"stop\":${forcedStopTime}")
        } else {
            updatedAResult = prevAResult.replaceFirst(/}(\s*)$/, ",\"stop\":${forcedStopTime}\$1")
        }

        jm.vars.put('AResult', updatedAResult)
        writer.writeResultJson(prevUUID, updatedAResult)
        jm.log.warn("autoFinalizePreviousCase: finalized prevUUID=${prevUUID} with stop=${forcedStopTime}")
    }

    // ------------------------------------------------------------------
    // Leaked label detection (value-based)
    // ------------------------------------------------------------------
    void computeLeakedLabelKeys(Map<String, String> preserved) {
        leakedLabelKeys.clear()
        boolean wasCleared = 'true' == jm.vars.get('_allureLabelsCleared')
        jm.vars.put('_allureLabelsCleared', 'false')
        String aResult = jm.vars.get('AResult') ?: ''
        if (aResult.isEmpty()) {
            return
        }
        try {
            def slurper = new JsonSlurper()
            def json = slurper.parseText(aResult)
            Map<String, String> previousLabels = [:]
            Map<String, String> labelNameToVarKey = [
                'tag':'allure.label.tags',
                'issue':'allure.label.issues'
            ]
            json.get('labels', []).each { l ->
                String labelName = l['name']
                if (labelName == 'host') { return }
                String varKey = labelNameToVarKey.get(labelName) ?: "allure.label.${labelName}"
                previousLabels[varKey] = l['value']
            }
            Set<String> currentLabelKeys = new HashSet<>()
            preserved.each { k, v ->
                if (k.startsWith('allure.label.')) {
                    currentLabelKeys.add(k)
                }
            }
            // Labels are leaked only if current test does not explicitly set them
            leakedLabelKeys = previousLabels.findAll { k, v ->
                !currentLabelKeys.contains(k)
            }.keySet()
            jm.log.warn("computeLeakedLabelKeys: wasCleared=${wasCleared}, previous=${previousLabels.keySet()}, current=${currentLabelKeys}, leaked=${leakedLabelKeys}")
        } catch (ex) {
            jm.log.warn("Failed to parse AResult for leaked label detection: ${ex.message}")
        }
    }

    // ------------------------------------------------------------------
    // Finalization (stop / solo)
    // ------------------------------------------------------------------
    void handleFinalization() {
        if (allureCaseResult == 'failed') {
            failedCountTests += 1
        } else if (allureCaseResult == 'skipped') {
            skippedCountTests += 1
        } else {
            passedCountTests += 1
        }

        if (summaryCountTests == 0) {
            println(('====' * 20) + '\n' + (' ' * 30) + "Allure-reporter: v" + version + '\n' + ('====' * 20))
        }
        println('' + LocalDateTime.now() + '\tINFO' + '\t[test: ' + summaryCountTests + ']' + '\t[thread: ' +
                jm.prev.getThreadName().toString().toLowerCase() + ']\t' + '\t[' + allureFullName + ']: [status: ' + allureCaseResult.toUpperCase() + ']')

        def slurper = new groovy.json.JsonSlurper()
        try {
            slurper.parseText(jm.vars.get('AResult'))
        } catch (ex) {
            println('' + LocalDateTime.now() + '\tWARN' + '\t[test: ' + summaryCountTests + ']' + '\t[JSON Validate failed. Input data same error]')
            println('' + LocalDateTime.now() + '\tWARN' + '\t[test: ' + summaryCountTests + ']' + '\t[JSON]\t' + jm.vars.get('AResult'))
            println "\n${ex.message}"
        }

        if (critical == 'yes') {
            println('' + LocalDateTime.now() + '\tWARN' + '\t[test: ' + summaryCountTests + ']' + '\t[Critical test case failed. Stopping thread]')
            jm.prev.setStopThread(true)
        }

        summaryCountTests += 1
        jm.clearAllureState()

        jm.setSummaryCountTests(summaryCountTests)
        jm.setPassedCountTests(passedCountTests)
        jm.setFailedCountTests(failedCountTests)
        jm.setSkippedCountTests(skippedCountTests)

        jm.log.info("************ SummaryCountTests: " + summaryCountTests)
        jm.log.info("************ PassedCountTests: " + passedCountTests)
        jm.log.info("************ FailedCountTests: " + failedCountTests)
        jm.log.info("************ SkippedCountTests: " + skippedCountTests)
        double rate = summaryCountTests > 0 ? passedCountTests / summaryCountTests * 100 : 0
        jm.log.info("************ Success Rate: " + rate + " %")
    }
}
