# Recommended Project Structure for JMeter + Allure

## Folder Structure (Current)

```
jallure/
├── build.gradle                        # Gradle build configuration (Groovy DSL)
├── settings.gradle
├── gradlew / gradlew.bat / gradle/wrapper/  # Gradle wrapper
├── docker/
│   ├── Dockerfile                      # Docker image for JMeter + Allure
│   ├── docker-compose.yml              # Docker Compose for local run
│   └── .dockerignore
├── docs/
│   ├── jallure_usage.md                # Detailed usage instructions
│   ├── project_structure.md            # This file
│   └── test_composition_guide.md       # Guide for composing JMX test plans
├── src/
│   ├── main/
│   │   ├── groovy/
│   │   │   └── io/github/forcasic/jmeter/allure/
│   │   │       ├── JsonUtils.groovy          # JSON escaping & content-type
│   │   │       ├── JMeterContext.groovy      # Wrapper around JMeter binding
│   │   │       ├── AttachmentWriter.groovy   # Writing attachment files
│   │   │       ├── AllureResultBuilder.groovy# Building Allure JSON
│   │   │       └── AllureReporter.groovy     # Main reporter logic
│   │   └── resources/
│   │       └── jmeter/
│   │           └── jmeter.properties   # Custom JMeter configuration
│   └── test/
│       ├── groovy/
│       │   └── io/github/forcasic/jmeter/allure/
│       │       ├── AllureResultBuilderSpec.groovy       # Unit tests for ResultBuilder
│       │       ├── AllureResultValidatorSpec.groovy     # Unit tests for the validator
│       │       ├── AllureResultValidator.groovy         # Integration test validator
│       │       ├── AllureResultsSpec.groovy             # Result validation tests
│       │       ├── AllureEpicLeakRegressionSpec.groovy  # Label leak regression tests
│       │       └── AllureReporterEdgeCaseTest.groovy    # Edge-case scenario tests
│       └── resources/
│           ├── allure-jmeter-example.jmx       # Example test plan
│           └── expected_results.json           # Validation reference
├── build/                              # [gitignore] Build, allure-results, allure-report
│   └── assembled-groovy/               # Assembled self-contained scripts
│       └── jallure.groovy
├── README.md
├── NOTICE                              # Attribution
└── LICENSE
```

**Important:** the canonical source is the **modular classes** under `src/main/groovy/io/github/forcasic/jmeter/allure/`. The script `jallure.groovy` is assembled automatically by the `./gradlew assembleJallureScripts` task and placed in `build/assembled-groovy/`. Edit only the classes, not the assembled script.

---

## Result File Format

Each test case = one JSON file `<uuid>-result.json` in `allureResults/` (by default `build/allure-results/`):

```json
{
  "name": "Test case name",
  "description": "Description",
  "status": "passed|failed|broken|skipped",
  "statusDetails": { "message": "" },
  "stage": "finished",
  "start": 1716555555000,
  "stop": 1716555558000,
  "uuid": "unique-uuid",
  "historyId": "unique-uuid",
  "fullName": "org.jmeter.com.epic.feature.story.test_name",
  "steps": [
    {
      "name": "POST /users/otps",
      "status": "passed",
      "stage": "finished",
      "start": 1716555555000,
      "stop": 1716555556000,
      "steps": [
        { "name": "Response Assertion", "status": "passed", "stage": "finished" }
      ],
      "attachments": [
        { "name": "Request", "source": "<uuid>-request-attachment", "type": "application/json" },
        { "name": "Response", "source": "<uuid>-response-attachment", "type": "application/json" }
      ]
    }
  ],
  "labels": [
    { "name": "epic", "value": "Demo Project" },
    { "name": "feature", "value": "Authorization" },
    { "name": "story", "value": "OTP login" },
    { "name": "severity", "value": "critical" },
    { "name": "host", "value": "Thread Group 1-1" }
  ],
  "parameters": [],
  "links": []
}
```

**Critical Allure requirements:**
- `start` and `stop` must be **numbers** (unix timestamp in milliseconds), NOT strings
- `status` must be one of: `passed`, `failed`, `broken`, `skipped`
- `uuid` is a unique identifier for the test case

---

## Script Operation Modes

| JSR223 Parameter | Mode        | Description                                   |
|------------------|-------------|-----------------------------------------------|
| `start`          | Start       | Creates a new test case, resets state         |
| `continue`       | Continue    | Appends a step to the current test case       |
| `stop`           | End         | Finalizes the test case, writes the result    |
| _(empty)_        | Solo        | Single-step test case (start+stop at once)    |

### Typical Pattern in JMX

```
Transaction Controller "Authorization"
│
├─ JSR223Sampler "Allure annotations"
│   vars.put("allure.name", "User authorization")
│   vars.put("allure.label.feature", "Auth")
│   vars.put("allure.label.story", "OTP login")
│
├─ HTTP Sampler: POST /users/otps
│   └─ JSR223Assertion (parameters=start)    <- jallure.groovy
│
├─ JDBC Sampler: get OTP
│   └─ JSR223Assertion (parameters=continue) <- jallure.groovy
│
└─ HTTP Sampler: POST /users/sessions
    └─ JSR223Assertion (parameters=stop)      <- jallure.groovy
```

---

## Report Generation and Viewing

```bash
# Clean old results (recommended before every run)
./gradlew cleanAllure

# Run JMeter (example test plan)
./gradlew jmeterRun

# Generate and open the report
./gradlew allureServe

# Or manually:
allure generate build/allure-results -o build/allure-report --clean
allure open build/allure-report
```

---

## Troubleshooting

### Tests Not Displayed in the Report

1. Check for result files:
   ```bash
   ls -la build/allure-results/*-result.json
   ```

2. Check JSON validity:
   ```bash
   for f in build/allure-results/*-result.json; do
     python3 -m json.tool "$f" > /dev/null && echo "OK: $f" || echo "FAIL: $f"
   done
   ```

3. Check timestamp format (must be numbers, not strings):
   ```bash
   grep '"start":' build/allure-results/*-result.json | grep '"start":"'
   # If there are matches — timestamps are strings (bug)
   ```

4. Check the JMeter log for errors:
   ```bash
   grep "Allure reporter" build/jmeter-run.log
   grep "NullPointerException" build/jmeter-run.log
   ```

### Common Causes

| Symptom                              | Cause                                | Solution                          |
|--------------------------------------|--------------------------------------|----------------------------------|
| Test exists but has no steps / incorrect status | `stop` was not called         | Check ResultAction in JMX; ensure every `start` has a `stop` |
| Test is completely missing           | NPE in buildAllureFullName()          | Check allure.label.feature       |
| All tests are "passed" despite errors | Assertion not added to Sampler       | Add a JSR223Assertion            |
| Invalid JSON in the log              | Control characters in response       | Fixed in v1.0.0 (escapeJson)     |
| Steps in the report are out of order | Time-drift / NTP sync                | Already fixed in v1.0.0 (monotonicity of `_allureLastStepStart`) |
