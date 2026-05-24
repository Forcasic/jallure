package io.github.forcasic.jmeter.allure

import java.util.regex.Matcher

/**
 * Builds Allure 2.x JSON strings.
 */
class AllureResultBuilder {

    // ------------------------------------------------------------------
    // Labels & Links
    // ------------------------------------------------------------------
    static String buildLabels(JMeterContext ctx, String parameters) {
        String labels = ''

        ctx.vars.entrySet().each { var ->
            String key = var.getKey()

            if (key =~ 'allure.label' && !(key =~ 'allure.label.tags') && !(key =~ 'allure.label.issues')) {
                labels += '{' +
                        '"name":"' + key.replaceAll('allure.label.', '').toLowerCase() + '",' +
                        '"value":"' + JsonUtils.escapeJson(var.getValue().toString()) + '"' +
                        '},'
            }

            if ((key =~ 'allure.label.tags') && !parameters.contains('ignore_tags')) {
                String tagsRaw = ctx.vars.get('allure.label.tags')
                if (tagsRaw != null && tagsRaw =~ ~/(.+)/) {
                    def tags = Matcher.lastMatcher[0][1].split(',')
                    tags.each { tag ->
                        labels += '{' +
                                '"name":"tag",' +
                                '"value":"' + JsonUtils.escapeJson(tag.toString()) + '"' +
                                '},'
                    }
                }
            }

            if (key =~ 'allure.label.issues') {
                String issuesRaw = ctx.vars.get('allure.label.issues')
                if (issuesRaw != null && issuesRaw =~ ~/(.+)/) {
                    def issues = Matcher.lastMatcher[0][1].split(',')
                    issues.each { issue ->
                        labels += '{' +
                                '"name":"issue",' +
                                '"value":"' + JsonUtils.escapeJson(issue.toString()) + '"' +
                                '},'
                    }
                }
            }
        }
        ctx.log.warn("buildLabels: generated labels=${labels}")
        return labels
    }

    static String buildLinks(JMeterContext ctx, String parameters) {
        String links = ''
        String allureLinks = ctx.vars.get('allure.links')
        if (allureLinks != null && !parameters.contains('ignore_links')) {
            if (allureLinks =~ ~/(.+)/) {
                def linkMatch = Matcher.lastMatcher[0][1].toString().split(',')
                for (int i = 1; i < linkMatch.size(); i += 2) {
                    links += '{' +
                            '"name":"' + JsonUtils.escapeJson(linkMatch[i - 1]) + '",' +
                            '"url":"' + JsonUtils.escapeJson(linkMatch[i]) + '"' +
                            '},'
                }
                if (links.length() > 0) { links = links[0..-2] }
            }
        }
        return links
    }

    // ------------------------------------------------------------------
    // Parameters
    // ------------------------------------------------------------------
    static String buildMainParameters(JMeterContext ctx) {
        String params = ''
        String allureParams = ctx.vars.get('allure.parameters')
        if (allureParams != null && allureParams =~ ~/(.+)/) {
            def parametersMemory = Matcher.lastMatcher[0][1].split(',')
            parametersMemory.eachWithIndex { param, i ->
                String tempVar = ctx.vars.get(param)
                params += '{' +
                        '"name":"' + JsonUtils.escapeJson(param) + '",' +
                        '"value":"' + JsonUtils.escapeJson(tempVar?.toString()) + '"' +
                        '}'
                if (i < parametersMemory.size() - 1) { params += ',' }
            }
        }
        return params
    }

    static String buildStepParameters(String parameters, JMeterContext ctx) {
        String stepParameters = ''
        if (parameters.contains('parameters=')) {
            if (parameters =~ ~/parameters=\[(.+?)\]/) {
                def parametersMemory = Matcher.lastMatcher[0][1].split(',')
                parametersMemory.eachWithIndex { param, i ->
                    String tempVar = ctx.vars.get(param)
                    stepParameters += '{' +
                            '"name":"' + JsonUtils.escapeJson(param) + '",' +
                            '"value":"' + JsonUtils.escapeJson(tempVar?.toString()) + '"' +
                            '}'
                    if (i < parametersMemory.size() - 1) { stepParameters += ',' }
                }
            }
        }
        return stepParameters
    }

    // ------------------------------------------------------------------
    // Full name
    // ------------------------------------------------------------------
    static String buildFullName(JMeterContext ctx, String displayName) {
        String epic = ctx.vars.get('allure.label.epic')
        String feature = ctx.vars.get('allure.label.feature')
        String story = ctx.vars.get('allure.label.story')
        return 'org.jmeter.com.' +
                (epic?.toLowerCase()?.replace(' ', '_') ?: 'null') + '.' +
                (feature?.toLowerCase()?.replace(' ', '_') ?: 'null') + '.' +
                (story?.toLowerCase()?.replace(' ', '_') ?: 'null') + '.' +
                displayName.toLowerCase().replace(' ', '_')
    }

    // ------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------
    static String buildSubStep(String name, String status, String stage, String message) {
        return '{' +
                '"name":"' + JsonUtils.escapeJson(name?.toString()) + '",' +
                '"status":"' + status + '",' +
                '"stage":"' + stage + '",' +
                '"statusDetails":{' +
                '"message":"' + JsonUtils.escapeJson(message) + '"' +
                '}' +
                '}'
    }

    static String buildMainStep(String name, String status, String stage,
                                String subSteps, String message,
                                String attachStepUUID, String requestType, String responseType,
                                String stepParameters, long start, long stop) {
        return '{' +
                '"name":"' + JsonUtils.escapeJson(name) + '",' +
                '"status":"' + status + '",' +
                '"stage":"' + stage + '",' +
                '"steps":[' + subSteps + '],' +
                '"statusDetails": {"message":"' + JsonUtils.escapeJson(message) + '"},' +
                '"attachments":[' +
                '{' +
                '"name":"Request",' +
                '"source":"' + attachStepUUID + '-request-attachment",' +
                '"type":"' + requestType + '"' +
                '},' +
                '{' +
                '"name":"Response",' +
                '"source":"' + attachStepUUID + '-response-attachment",' +
                '"type":"' + responseType + '"' +
                '}' +
                '],' +
                '"parameters":[' + stepParameters + '],' +
                '"start":' + start + ',' +
                '"stop":' + stop +
                '}'
    }

    // ------------------------------------------------------------------
    // Test case
    // ------------------------------------------------------------------
    static String buildTestCaseJson(JMeterContext ctx,
                                    String name, String description, String status,
                                    String statusMessage, String stage,
                                    String steps, long start, long stop,
                                    String uuid, String historyId, String fullName,
                                    String parameters, String labels, String links) {
        return '{' +
                '"name":"' + JsonUtils.escapeJson(name) + '",' +
                '"description":"' + JsonUtils.escapeJson(description) + '",' +
                '"status":"' + status + '",' +
                '"statusDetails":{' +
                '"message":"' + JsonUtils.escapeJson(statusMessage) + '"' +
                '},' +
                '"stage":"' + stage + '",' +
                '"steps":[' + steps + '],' +
                '"start":' + start + ',' +
                '"stop":' + stop + ',' +
                '"uuid":"' + uuid + '","historyId":"' + historyId + '",' +
                '"fullName":"' + JsonUtils.escapeJson(fullName) + '",' +
                '"parameters":[' + parameters + '],' +
                '"labels":[' + labels + '{' +
                '"name":"host",' +
                '"value":"' + JsonUtils.escapeJson(ctx.prev.getThreadName().toString()) + '"' +
                '}],' +
                '"links":[' + links + ']' +
                '}'
    }
}
