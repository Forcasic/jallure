package io.github.forcasic.jmeter.allure

import org.apache.jmeter.services.FileServer

/**
 * Thin wrapper around JMeter JSR223 binding objects.
 */
class JMeterContext {
    def ctx
    def vars
    def prev
    def sampler
    def log
    String parameters

    JMeterContext(ctx, vars, prev, sampler, log, String parameters) {
        this.ctx = ctx
        this.vars = vars
        this.prev = prev
        this.sampler = sampler
        this.log = log
        this.parameters = parameters ?: ''
    }

    // ------------------------------------------------------------------
    // Parameter mode detection
    // ------------------------------------------------------------------
    boolean isStart() {
        parameters.startsWith('start') || parameters.contains(',start') || parameters.contains(' start')
    }

    boolean isStop() {
        parameters.startsWith('stop') || parameters.contains(',stop') || parameters.contains(' stop')
    }

    boolean isContinue() {
        parameters.startsWith('continue') || parameters.contains(',continue') || parameters.contains(' continue')
    }

    boolean isSolo() {
        !isStart() && !isStop() && !isContinue()
    }

    boolean isEffectiveStart() {
        if (isStart()) { return true }
        if (!isContinue() || !hasModifier('continue_as_start')) { return false }

        boolean hasNoAnnotations =
            (vars.get('allure.label.epic') == null || vars.get('allure.label.epic').isEmpty()) &&
            (vars.get('allure.label.story') == null || vars.get('allure.label.story').isEmpty()) &&
            (vars.get('allure.label.feature') == null || vars.get('allure.label.feature').isEmpty())

        boolean noActiveCase = getCaseUUID() == null || getCaseUUID().isEmpty()

        return noActiveCase && hasNoAnnotations
    }

    boolean isStartAndStop() {
        isStart() && isStop()
    }

    boolean hasModifier(String mod) {
        parameters.contains(mod)
    }

    // ------------------------------------------------------------------
    // Paths
    // ------------------------------------------------------------------
    String getReportPath() {
        String customPath = vars.get('_ALLURE_REPORT_PATH')
        if (customPath != null && !customPath.isEmpty()) {
            File customDir = new File(customPath)
            if (!customDir.exists()) { customDir.mkdirs() }
            return customPath
        }
        String baseDir = FileServer.getFileServer().getBaseDir()
        if (baseDir == null || baseDir.isEmpty()) {
            baseDir = System.getProperty("user.dir")
        }
        File resultsDir = new File(baseDir, "allureReport/allureResults")
        if (!resultsDir.exists()) { resultsDir.mkdirs() }
        return resultsDir.absolutePath
    }

    // ------------------------------------------------------------------
    // Variable helpers
    // ------------------------------------------------------------------
    String getVar(String name) { vars.get(name) }
    void setVar(String name, String value) { vars.put(name, value) }
    void removeVar(String name) { vars.remove(name) }

    // ------------------------------------------------------------------
    // Property helpers (JMeter properties – shared across threads)
    // ------------------------------------------------------------------
    Object getProp(String name) { ctx.getProperties().get(name) }
    void setProp(String name, Object value) { ctx.getProperties().put(name, value) }

    // ------------------------------------------------------------------
    // Allure-specific state variables
    // ------------------------------------------------------------------
    String getCaseUUID() { vars.get('_allureCaseUUID') }
    void setCaseUUID(String uuid) { vars.put('_allureCaseUUID', uuid) }

    String getStepUUID() { vars.get('attachment-UUID') }
    void setStepUUID(String uuid) {
        vars.put('attachment-UUID', uuid)
        vars.put('pngUUID', uuid)
    }

    String getPrevMainSteps() { vars.get('prevMainSteps') ?: '' }
    void setPrevMainSteps(String value) { vars.put('prevMainSteps', value) }

    String getAllureCaseResult() { vars.get('allureCaseResult') ?: 'passed' }
    void setAllureCaseResult(String value) { vars.put('allureCaseResult', value) }

    String getAllureCaseFailReason() { vars.get('allureCaseFailReason') ?: '' }
    void setAllureCaseFailReason(String value) { vars.put('allureCaseFailReason', value) }

    String getCaseTimeStart() { vars.get('caseTimeStart') }
    void setCaseTimeStart(String value) { vars.put('caseTimeStart', value) }

    String getLastStepStart() { vars.get('_allureLastStepStart') }
    void setLastStepStart(String value) { vars.put('_allureLastStepStart', value) }

    String getLastCaseStart() { vars.get('_allureLastCaseStart') }
    void setLastCaseStart(String value) { vars.put('_allureLastCaseStart', value) }

    String getLoopCounter() { vars.get('loopCounter') }
    void setLoopCounter(String value) { vars.put('loopCounter', value) }
    void setLoopCounter(Integer value) { vars.putObject('loopCounter', value) }

    String getSummaryCountTests() { getProp('SummaryCountTests') ?: '0' }
    void setSummaryCountTests(Integer value) { setProp('SummaryCountTests', value.toString()) }

    String getPassedCountTests() { getProp('PassedCountTests') ?: '0' }
    void setPassedCountTests(Integer value) { setProp('PassedCountTests', value.toString()) }

    String getFailedCountTests() { getProp('FailedCountTests') ?: '0' }
    void setFailedCountTests(Integer value) { setProp('FailedCountTests', value.toString()) }

    String getSkippedCountTests() { getProp('SkippedCountTests') ?: '0' }
    void setSkippedCountTests(Integer value) { setProp('SkippedCountTests', value.toString()) }

    String getCritical() { vars.get('critical') ?: '' }
    void setCritical(String value) { vars.put('critical', value) }

    String getAllureName() { vars.get('allure.name') }
    void setAllureName(String value) { vars.put('allure.name', value) }

    String getAllureDescription() { vars.get('allure.description') }
    void setAllureDescription(String value) { vars.put('allure.description', value) }

    String getFeatureSuffix() { vars.get('allure.feature.suffix') }

    // ------------------------------------------------------------------
    // Preserving / clearing annotations
    // ------------------------------------------------------------------
    Map<String, String> preserveAnnotations() {
        Map<String, String> preserved = [:]
        vars.entrySet().each { var ->
            String key = var.getKey()
            if (key.startsWith('allure.label') ||
                key.startsWith('allure.links') ||
                key == 'allure.name' ||
                key == 'allure.description' ||
                key == 'allure.parameters' ||
                key == 'mainParameters') {
                preserved.put(key, var.getValue())
            }
        }
        return preserved
    }

    void restoreAnnotations(Map<String, String> preserved, Set<String> excludeKeys = null) {
        preserved.each { k, v ->
            if (excludeKeys == null || !excludeKeys.contains(k)) {
                vars.put(k, v)
            }
        }
    }

    void clearAllureState() {
        setPrevMainSteps('')
        setVar('AResult', '')
        setVar('SummarySubSteps', '')
        setCritical('')
        setAllureCaseResult('passed')
        setAllureCaseFailReason('')
        setVar('allure.parameters', null)
        setVar('mainParameters', '')
        setLoopCounter(null)
        setLastStepStart('0')
        clearAllLabels()
        removeVar('allure.name')
        removeVar('allure.description')
        removeVar('_allureCaseUUID')
        removeVar('caseTimeStart')
    }

    void clearAllLabels() {
        Set copy = new HashSet(vars.entrySet())
        for (Iterator iter = copy.iterator(); iter.hasNext();) {
            def var = iter.next()
            String key = var.getKey()
            if (key.startsWith("allure.label") ||
                key.startsWith("allure.links") ||
                key.startsWith("allure.label.AS_ID")) {
                vars.remove(key)
            }
        }
        vars.put('_allureLabelsCleared', 'true')
    }
}
