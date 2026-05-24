package io.github.forcasic.jmeter.allure

import org.apache.jmeter.threads.JMeterVariables
import spock.lang.Specification

class AllureResultBuilderSpec extends Specification {

    def "buildFullName uses literal 'null' when epic is missing"() {
        given:
        def vars = new JMeterVariables()
        vars.put('allure.label.feature', 'SpaceX Capsules')
        vars.put('allure.label.story', 'Positive')
        def ctx = new JMeterContext(null, vars, null, null, null, '')

        when:
        def fullName = AllureResultBuilder.buildFullName(ctx, 'Check capsules')

        then:
        fullName == 'org.jmeter.com.null.spacex_capsules.positive.check_capsules'
    }

    def "buildFullName uses literal 'null' when feature is missing"() {
        given:
        def vars = new JMeterVariables()
        vars.put('allure.label.epic', 'Capsules')
        vars.put('allure.label.story', 'Positive')
        def ctx = new JMeterContext(null, vars, null, null, null, '')

        when:
        def fullName = AllureResultBuilder.buildFullName(ctx, 'Check capsules')

        then:
        fullName == 'org.jmeter.com.capsules.null.positive.check_capsules'
    }

    def "buildFullName uses literal 'null' when story is missing"() {
        given:
        def vars = new JMeterVariables()
        vars.put('allure.label.epic', 'Capsules Without Story')
        vars.put('allure.label.feature', 'SpaceX Capsules')
        def ctx = new JMeterContext(null, vars, null, null, null, '')

        when:
        def fullName = AllureResultBuilder.buildFullName(ctx, 'Folder with checking param types N2')

        then:
        fullName == 'org.jmeter.com.capsules_without_story.spacex_capsules.null.folder_with_checking_param_types_n2'
    }

    def "buildFullName uses literal 'null' for all missing labels"() {
        given:
        def vars = new JMeterVariables()
        def ctx = new JMeterContext(null, vars, null, null, null, '')

        when:
        def fullName = AllureResultBuilder.buildFullName(ctx, 'Solo test')

        then:
        fullName == 'org.jmeter.com.null.null.null.solo_test'
    }

    def "buildFullName includes all labels when present"() {
        given:
        def vars = new JMeterVariables()
        vars.put('allure.label.epic', 'Capsules')
        vars.put('allure.label.feature', 'SpaceX Capsules')
        vars.put('allure.label.story', 'Positive')
        def ctx = new JMeterContext(null, vars, null, null, null, '')

        when:
        def fullName = AllureResultBuilder.buildFullName(ctx, 'Check capsules')

        then:
        fullName == 'org.jmeter.com.capsules.spacex_capsules.positive.check_capsules'
    }

    def "buildFullName replaces spaces with underscores and lowercases everything"() {
        given:
        def vars = new JMeterVariables()
        vars.put('allure.label.epic', 'My Epic')
        vars.put('allure.label.feature', 'My Feature')
        vars.put('allure.label.story', 'My Story')
        def ctx = new JMeterContext(null, vars, null, null, null, '')

        when:
        def fullName = AllureResultBuilder.buildFullName(ctx, 'My Test Name')

        then:
        fullName == 'org.jmeter.com.my_epic.my_feature.my_story.my_test_name'
    }
}
