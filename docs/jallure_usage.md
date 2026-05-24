# Allure Reporter for JMeter — Usage Guide

## 1. Purpose
`jallure.groovy` is a Groovy script that generates `allure-results` (*.json + attachments) directly during a JMeter test run.  
It is invoked via a **JSR223 Assertion** (or JSR223 Sampler) with specific parameters.

> **Canonical source** — the classes under `src/main/groovy/io/github/forcasic/jmeter/allure/`. The script itself is assembled automatically via `./gradlew assembleJallureScripts`.

---

## 2. How to Obtain the Script

### 2.1. Build from Source

```bash
# Clone the repository
git clone <repo-url>
cd jallure

# Assemble self-contained scripts from modular classes
./gradlew assembleJallureScripts

# Result:
# build/assembled-groovy/jallure.groovy
```

### 2.2. Where to Get a Ready-Made Script

If you do not plan to change the logic — simply copy the assembled files:
```bash
cp build/assembled-groovy/jallure.groovy /path/to/jmeter/scripts/
```

---

## 3. How to Connect to a JMX

### 3.1. Script Path
Place `jallure.groovy` in a location known to JMeter (for example, next to the JMX or in `bin/`).  
In the JSR223 Assertion specify:
- **Filename:** `${_ALLURE_CONFIG_PATH}` (or absolute path)
- **Language:** `groovy`
- **Parameters:** `start` / `continue` / `stop` / `solo` / combinations
- **Script:** leave **empty** (code is loaded from the file)

### 3.2. User-Defined Variable `_ALLURE_REPORT_PATH`
In `User Defined Variables` (or Setup Thread Group) you can set:
```groovy
vars.put('_ALLURE_REPORT_PATH','/result/allure-results');
```
If not set — results are written to `allureReport/allureResults` relative to JMeter's baseDir.

---

## 4. Invocation Parameters (JSR223 Assertion → Parameters)

| Parameter | When to Use | What Happens |
|-----------|-------------|--------------|
| `start` | First step of a multi-step test | Cleanup + new UUID + begin accumulating steps |
| `continue` | Intermediate steps of a multi-step test | Appends steps to the current case |
| `stop` | Last step of a multi-step test | Finalizes the case, writes JSON, counters, **clearAllureVariable()** |
| *(empty)* | Single (solo) request | Full start→stop cycle in a single call |
| `start,stop` | One request = entire case | Cleanup + new UUID + finalization + clear |
| `start ...` + other modifiers | For example `start tika_xml` | `start` is processed wherever it appears in the parameter string |

**Important:** parameters are checked via `Parameters.contains('...')`, so you can write `start tika_xml`, `stop no_report`, etc.

---

## 5. JMeter Variables (`vars`) for Allure Annotations

Set **BEFORE** calling `jallure.groovy` (usually in a separate JSR223 Sampler named *"Declare allure annotations"*).

| Variable | Purpose | Example |
|----------|---------|---------|
| `allure.name` | Test case name in the report | `vars.put("allure.name","Check capsules")` |
| `allure.description` | Test case description | `vars.put("allure.description","Lorem ipsum...")` |
| `allure.label.epic` | Epic (top level of Behaviors) | `vars.put("allure.label.epic","Capsules")` |
| `allure.label.feature` | Feature (level below Epic) | `vars.put("allure.label.feature","SpaceX-Capsules")` |
| `allure.label.story` | Story (level below Feature) | `vars.put("allure.label.story","Positive")` |
| `allure.label.severity` | Severity (blocker, critical, normal, minor, trivial) | `vars.put("allure.label.severity","critical")` |
| `allure.label.layer` | Layer (for example, "jmeter") | `vars.put("allure.label.layer","jmeter")` |
| `allure.label.owner` | Owner | `vars.put("allure.label.owner","Tester")` |
| `allure.label.tags` | Comma-separated tags | `vars.put("allure.label.tags","smoke,api")` |
| `allure.label.issues` | Comma-separated issues (for Allure TMS) | `vars.put("allure.label.issues","PROJECT-1,PROJECT-2")` |
| `allure.links` | Links in `type,url,type,url` format | `vars.put("allure.links","issue,https://github.com/...")` |
| `allure.parameters` | Comma-separated JMeter variable names | `vars.put("allure.parameters","capsule_serial,status")` |
| `allure.feature.suffix` | Suffix automatically appended to Feature | `vars.put("allure.feature.suffix","_DEBUG")` |

**Prefix `allure.label.*`:** everything starting with `allure.label.` goes into the `labels` section of the JSON result.

---

## 6. Test Case Lifecycle

### 6.1. Multi-Step Test
Used when one test case consists of several HTTP requests.

```
JSR223 Sampler  "Declare allure annotations"
  └─ vars.put("allure.name","My Case")
  └─ vars.put("allure.label.story","Positive")

HTTP Request 1  "Get all capsules"
  └─ JSR223 Assertion  Parameters = start

HTTP Request 2  "Get capsule details"
  └─ JSR223 Assertion  Parameters = continue

HTTP Request 3  "Get past capsules"
  └─ JSR223 Assertion  Parameters = stop
```

**Result:** one `*-result.json` containing a `steps` array with 3 elements.

### 6.2. Solo Test (Single Step)
Used when test case = one HTTP request.

```
JSR223 Sampler  "Declare allure annotations"
  └─ vars.put("allure.name","Solo check")

HTTP Request  "Get capsule with serial: ABC"
  └─ JSR223 Assertion  Parameters = (empty)  →  invoked as solo
```

Or without annotations altogether — then the case name is taken from `sampler.getName()`, and the description from `sampler.getComment()`.

### 6.3. Start + Stop in a Single Call
```
JSR223 Sampler  "Declare allure annotations"
HTTP Request  "Get all capsules"
  └─ JSR223 Assertion  Parameters = start,stop
```
Equivalent to a multi-step test with a single step.  
Cleanup runs on `start`, finalization on `stop`.

---

## 7. Cleanup Logic (`clearAllureVariable`)

Cleanup is triggered:
1. **Always when `start` is present** — before the case begins (current case annotations are preserved).
2. **On `stop` or `solo`** — after the case is finalized.

### What is reset:
- `prevMainSteps` — steps of the current case
- `SummarySubSteps` — assertion steps
- `AResult` — JSON body of the result
- `allureCaseFailReason` — error text
- `allureCaseResult` → `'passed'`
- `critical`
- `loopCounter`
- `_allureLastStepStart` — protection against time-drift between cases
- `allure.name`, `allure.description`
- **All `allure.label.*`**, `allure.links`

### What is preserved during `start`-cleanup:
- All `allure.label.*`
- `allure.name`, `allure.description`
- `allure.links`
- `allure.parameters`
- `mainParameters`

This is necessary because annotations are usually set **BEFORE** the `start` call.

---

## 8. UUID Generation

| Variable | Purpose |
|----------|---------|
| `_allureCaseUUID` | UUID of the test case itself. Generated on `start` or `solo`. Reused on `continue`/`stop`. |
| `attachment-UUID` / `pngUUID` | Attachment UUID (Request/Response). Generated **anew for each step**. |

Result files:
- `${attachUUID}-result.json` — overwritten on every step, accumulating new data.
- `${attachStepUUID}-request-attachment` — request body.
- `${attachStepUUID}-response-attachment` — response body.

---

## 9. JMX Structure Examples

### 9.1. Multi-Step with Annotations
```xml
<GenericController testname="My Test Case">
  <hashTree>
    <JSR223Sampler testname="Declare allure annotations">
      <stringProp name="script">
        vars.put("allure.name","Check capsules flow");
        vars.put("allure.label.epic","Capsules");
        vars.put("allure.label.feature","SpaceX-Capsules");
        vars.put("allure.label.story","Positive");
      </stringProp>
    </JSR223Sampler>
    <hashTree/>

    <HTTPSamplerProxy testname="Get all capsules">
      <hashTree>
        <JSR223Assertion testname="Allure.log.info">
          <stringProp name="filename">${_ALLURE_CONFIG_PATH}</stringProp>
          <stringProp name="parameters">start</stringProp>
        </JSR223Assertion>
      </hashTree>
    </HTTPSamplerProxy>

    <HTTPSamplerProxy testname="Get capsule by id">
      <hashTree>
        <JSR223Assertion testname="Allure.log.info">
          <stringProp name="filename">${_ALLURE_CONFIG_PATH}</stringProp>
          <stringProp name="parameters">continue</stringProp>
        </JSR223Assertion>
      </hashTree>
    </HTTPSamplerProxy>

    <HTTPSamplerProxy testname="Get past capsules">
      <hashTree>
        <JSR223Assertion testname="Allure.log.info">
          <stringProp name="filename">${_ALLURE_CONFIG_PATH}</stringProp>
          <stringProp name="parameters">stop</stringProp>
        </JSR223Assertion>
      </hashTree>
    </HTTPSamplerProxy>
  </hashTree>
</GenericController>
```

### 9.2. Solo with Annotations
```xml
<JSR223Sampler testname="Declare allure annotations">
  <stringProp name="script">
    vars.put("allure.name","Solo capsule check");
    vars.put("allure.label.severity","critical");
  </stringProp>
</JSR223Sampler>
<hashTree/>

<HTTPSamplerProxy testname="Get capsule">
  <hashTree>
    <JSR223Assertion testname="Allure.log.info">
      <stringProp name="filename">${_ALLURE_CONFIG_PATH}</stringProp>
      <stringProp name="parameters"></stringProp>  <!-- solo -->
    </JSR223Assertion>
  </hashTree>
</HTTPSamplerProxy>
```

---

## 10. Typical Problems and Solutions

### 10.1. Leakage of `allure.label.epic` / `story` into the Next Test
**Cause:** the previous multi-step test does not have a `stop`, so `clearAllureVariable()` is not called. Labels remain in `vars` and leak into the next test case unless that case explicitly overrides them.  
**Solution:** finish every multi-step test with `stop`. If `stop` may be absent (orphaned case), use the `allure.clearLabels` pattern:

```groovy
// One-time initialization at the start of the ThreadGroup
vars.putObject("allure.clearLabels", { ->
    Set copy = new HashSet(vars.entrySet())
    for (Iterator iter = copy.iterator(); iter.hasNext();) {
        def var = iter.next()
        String key = var.getKey()
        if (key.startsWith("allure.label") || key.startsWith("allure.links") || key.startsWith("allure.label.AS_ID")) {
            vars.remove(key)
        }
    }
})
```

```groovy
// At the beginning of every "Declare allure annotations" that requires a clean state
vars.getObject("allure.clearLabels")?.call()
vars.put("allure.name","With JSR223 Sampler step")
vars.put("allure.label.feature","SpaceX-Capsules")
// ... remaining annotations
```

### 10.2. Tests Merged into One
**Cause:** `start` was not called, or `_allureCaseUUID` was left over from a previous test.  
**Solution:** make sure the first step of a multi-step test has `start`.

### 10.3. Duplicates in the Report
**Cause:** `stop` was called multiple times for one `_allureCaseUUID`.  
**Solution:** there must be exactly one `stop` per case.

### 10.4. Labels Not Displayed
**Cause:** `allure.label.*` are set AFTER the `start` call, not BEFORE.  
**Solution:** annotations must be in `vars` **before** the JSR223 Assertion with `jallure.groovy` runs.

### 10.5. `start,stop` Did Not Create a New Case (obsolete)
**Cause (before fix):** `isStart` required the absence of `stop`.  
**Solution (after fix):** `isStart` now simply checks for the presence of `start`. Cleanup on `start` is always performed.

### 10.6. Steps in the Report Displayed in the Wrong Order
**Cause:** Allure sorts steps by `start` timestamp. If the machine time was adjusted (NTP, VM suspend/migrate), the `start` of the next step may be less than the `start` of the previous one, and Allure will reorder them.  
**Solution:** Starting from version 1.0.0, the script automatically guarantees monotonicity of each step's `start` via `_allureLastStepStart`, and also monotonicity of the whole test case's `start` via `_allureLastCaseStart`. If a timestamp is less than or equal to the previous one, 1 ms is added (while preserving the original step duration).

---

## 11. Additional Parameter Modifiers

| Modifier | Effect |
|----------|--------|
| `no_report` | Do not write result.json and attachments (useful for debugging) |
| `tika_xml` | Use Apache Tika for parsing binary responses (xlsx, etc.) |
| `critical` | If the step fails — stop the thread (`prev.setStopThread(true)`) |
| `skipped` | Force the case status to `skipped` |
| `ignore_tags` | Do not add `allure.label.tags` to JSON |
| `ignore_links` | Do not add `allure.links` to JSON |
| `parameters=[var1,var2]` | Add step-level parameters to the current step |

Combination examples:
```
start tika_xml
stop critical
start no_report
```

---

## 12. How It Works Under the Hood (Briefly)

1. **JSR223 Assertion** is triggered after the sampler.
2. `jallure.groovy` reads `prev` (PreviousResult) — request/response/assertions.
3. Constructs the JSON string `AResult` according to the Allure 2.x specification.
4. Writes `*-result.json` and attachment files to `allureReportPath`.
5. On the final step (`stop`/`solo`) — validates JSON, updates global counters, logs to console.

---
