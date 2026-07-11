# Graph Report - .  (2026-07-11)

## Corpus Check
- Corpus is ~18,425 words - fits in a single context window. You may not need a graph.

## Summary
- 109 nodes · 125 edges · 16 communities (15 shown, 1 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 2 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Core Allure Builders and Attachments|Core Allure Builders and Attachments]]
- [[_COMMUNITY_Allure Reporter and Usage Config|Allure Reporter and Usage Config]]
- [[_COMMUNITY_Result Validator Tests|Result Validator Tests]]
- [[_COMMUNITY_Reporter Edge Case Tests|Reporter Edge Case Tests]]
- [[_COMMUNITY_JSR223 Sampler Patterns|JSR223 Sampler Patterns]]
- [[_COMMUNITY_Result Builder Tests|Result Builder Tests]]
- [[_COMMUNITY_Results Spec Tests|Results Spec Tests]]
- [[_COMMUNITY_Expected Results Fixture|Expected Results Fixture]]
- [[_COMMUNITY_JMX Expected Results Fixture|JMX Expected Results Fixture]]
- [[_COMMUNITY_Epic Leak Regression Test|Epic Leak Regression Test]]

## God Nodes (most connected - your core abstractions)
1. `jallure` - 14 edges
2. `AllureResultValidatorSpec` - 13 edges
3. `AllureReporterEdgeCaseTest` - 12 edges
4. `start parameter` - 8 edges
5. `AllureResultBuilderSpec` - 7 edges
6. `stop parameter` - 7 edges
7. `JSR223 Assertion` - 6 edges
8. `Allure result file format` - 6 edges
9. `Multi-step test pattern` - 6 edges
10. `AttachmentWriter` - 5 edges

## Surprising Connections (you probably didn't know these)
- `Allure result file format` --conceptually_related_to--> `Allure Report`  [EXTRACTED]
  docs/project_structure.md → README.md
- `JSR223 Sampler` --conceptually_related_to--> `JSR223 Assertion`  [EXTRACTED]
  docs/jallure_usage.md → README.md
- `Solo test pattern` --references--> `JSR223 Assertion`  [EXTRACTED]
  docs/test_composition_guide.md → README.md
- `Multi-step test pattern` --conceptually_related_to--> `Orphaned Label Leak`  [EXTRACTED]
  docs/test_composition_guide.md → README.md
- `Orphaned Label Leak` --conceptually_related_to--> `clearAllureVariable`  [EXTRACTED]
  README.md → docs/jallure_usage.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **jallure modular source classes** — allure_jsonutils, allure_jmetercontext, allure_attachmentwriter, allure_allureresultbuilder, allure_allurereporter, assembled_groovy_jallure [INFERRED 0.85]
- **Test case lifecycle parameters** — docs_jallure_usage_start_parameter, docs_jallure_usage_continue_parameter, docs_jallure_usage_stop_parameter, docs_jallure_usage_solo_parameter [EXTRACTED 1.00]
- **Allure annotation variables** — docs_jallure_usage_allure_name, docs_jallure_usage_allure_label_epic, docs_jallure_usage_allure_label_feature, docs_jallure_usage_allure_label_story, docs_jallure_usage_allure_parameters, docs_jallure_usage_allure_links [EXTRACTED 1.00]

## Communities (16 total, 1 thin omitted)

### Community 0 - "Core Allure Builders and Attachments"
Cohesion: 0.13
Nodes (11): AllureResultBuilder, AttachmentWriter, Allure result file format, Allure Report, Apache JMeter, clearLabels Pattern, Docker, Gradle (+3 more)

### Community 1 - "Allure Reporter and Usage Config"
Cohesion: 0.13
Nodes (15): AllureReporter, jmeter Docker service, _ALLURE_CONFIG_PATH, allure.label.epic variable, allure.label.feature variable, allure.label.story variable, allure.links variable, allure.name variable (+7 more)

### Community 2 - "Result Validator Tests"
Cohesion: 0.14
Nodes (12): AllureResultValidatorSpec, "validate distinguishes duplicate names via epic, feature, story", "validate fails when actual test count differs from expected total_tests", "validate fails when an expected test is missing from results", "validate fails when duplicate names exist but epic/feature/story do not match", "validate fails when expected attachments are missing", "validate fails when expected_results.json is empty but results exist", "validate fails when main_steps does not match" (+4 more)

### Community 3 - "Reporter Edge Case Tests"
Cohesion: 0.15
Nodes (11): AllureReporterEdgeCaseTest, "autoFinalizePreviousCase patches stop timestamp into existing result", "critical calls setStopThread on failure", "feature suffix is preserved on effective start after state reset", "GET request without Content-Type includes request headers in attachment", "longChain accumulates five steps with monotonic timestamps", "orphaned case labels do not leak when explicitly removed before next start", "orphanedStop creates case with step start timestamp" (+3 more)

### Community 4 - "JSR223 Sampler Patterns"
Cohesion: 0.36
Nodes (11): _allureCaseUUID, clearAllureVariable, continue parameter, JSR223 Sampler, solo parameter, start parameter, stop parameter, Multi-step test pattern (+3 more)

### Community 5 - "Result Builder Tests"
Cohesion: 0.25
Nodes (7): AllureResultBuilderSpec, "buildFullName includes all labels when present", "buildFullName replaces spaces with underscores and lowercases everything", "buildFullName uses literal 'null' for all missing labels", "buildFullName uses literal 'null' when epic is missing", "buildFullName uses literal 'null' when feature is missing", "buildFullName uses literal 'null' when story is missing"

### Community 6 - "Results Spec Tests"
Cohesion: 0.40
Nodes (4): AllureResultsSpec, "Allure results must match expected count and key fields from expected_results.json", "expected_results.json must not be empty or degenerate", "expected_results.json structure must be valid"

### Community 8 - "Expected Results Fixture"
Cohesion: 0.50
Nodes (3): _comment, expected_tests, total_tests

### Community 9 - "JMX Expected Results Fixture"
Cohesion: 0.50
Nodes (3): _comment, expected_tests, total_tests

## Knowledge Gaps
- **53 isolated node(s):** `AllureReporter`, `"With JSR223 Sampler step must not inherit epic from previous orphaned case"`, `"autoFinalizePreviousCase patches stop timestamp into existing result"`, `"startAndStop invokes handleFinalization and clears state"`, `"orphanedStop creates case with step start timestamp"` (+48 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **1 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `jallure` connect `Core Allure Builders and Attachments` to `Allure Reporter and Usage Config`, `JSR223 Sampler Patterns`?**
  _High betweenness centrality (0.092) - this node is a cross-community bridge._
- **Why does `Allure result file format` connect `Core Allure Builders and Attachments` to `Expected Results Fixture`, `Allure Reporter and Usage Config`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **What connects `AllureReporter`, `"With JSR223 Sampler step must not inherit epic from previous orphaned case"`, `"autoFinalizePreviousCase patches stop timestamp into existing result"` to the rest of the system?**
  _53 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Core Allure Builders and Attachments` be split into smaller, more focused modules?**
  _Cohesion score 0.12631578947368421 - nodes in this community are weakly interconnected._
- **Should `Allure Reporter and Usage Config` be split into smaller, more focused modules?**
  _Cohesion score 0.1323529411764706 - nodes in this community are weakly interconnected._
- **Should `Result Validator Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._