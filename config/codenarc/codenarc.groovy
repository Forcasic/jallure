ruleset {
    description 'jallure CodeNarc rules — moderate set for a mature project'

    ruleset('rulesets/basic.xml')
    ruleset('rulesets/braces.xml')
    ruleset('rulesets/concurrency.xml')
    ruleset('rulesets/design.xml') {
        exclude 'MethodCount'
        exclude 'FactoryMethodName'
        exclude 'CyclomaticComplexity'
    }
    ruleset('rulesets/exceptions.xml')
    ruleset('rulesets/formatting.xml') {
        exclude 'ClassJavadoc'
        exclude 'ClassStartsWithBlankLine'
        exclude 'ClassEndsWithBlankLine'
        exclude 'LineLength'
        exclude 'SpaceAroundMapEntryColon'
        exclude 'TrailingWhitespace'
        exclude 'ConsecutiveBlankLines'
    }
    ruleset('rulesets/groovyism.xml') {
        exclude 'ExplicitHashSetInstantiation'
        exclude 'ClosureAsLastMethodParameter'
    }
    ruleset('rulesets/imports.xml')
    ruleset('rulesets/logging.xml') {
        exclude 'Println'
    }
    ruleset('rulesets/naming.xml') {
        exclude 'MethodName'
        exclude 'PropertyName'
    }
    ruleset('rulesets/security.xml') {
        exclude 'JavaIoPackageAccess'
        exclude 'SystemExit'
    }
    ruleset('rulesets/size.xml') {
        exclude 'ParameterCount'
        exclude 'MethodCount'
        exclude 'AbcMetric'
        exclude 'MethodSize'
    }
    ruleset('rulesets/unused.xml') {
        exclude 'UnusedMethodParameter'
    }
}
