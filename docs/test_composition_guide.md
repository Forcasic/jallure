# Practical Guide: How to Compose Tests for Allure Reporter

> This document is about **composing** the JMX file. For the technical description of variables and modifiers see [`jallure_usage.md`](jallure_usage.md).

---

## 1. Main Rule: `start` Is the Starting Point

**`start` always begins a new test case.**  
Even if the previous test case "hung" (did not reach `stop`), the next `start` will:

1. Perform **cleanup** of the old state.
2. Generate a **new UUID** — a new `*-result.json` file.
3. **Preserve annotations** that were set before it.

**Answer to your question:** *yes, the next `start` will launch a new block.*  


---

## 2. Where to Insert the Script

**Always as a `JSR223 Assertion` under a sampler** (HTTP Request, JSR223 Sampler, etc.).

| JSR223 Assertion Field | Value |
|------------------------|-------|
| **Filename** | Path to the assembled `jallure.groovy` (or `${_ALLURE_CONFIG_PATH}`) |
| **Language** | `groovy` |
| **Parameters** | `start` / `continue` / `stop` / leave empty (solo) |
| **Script** | **empty** — code is loaded from the file |

```
HTTP Request "Get user"
  └─ JSR223 Assertion (jallure.groovy, Parameters = start)
```

It is an **Assertion**, not a Sampler, because the script reads `prev` (result of the previous request).

---

## 3. Three Basic Patterns

### 3.1. Solo — One Request = One Test Case

Leave the parameters **empty**.

```
Test Plan
└─ Thread Group
    ├─ JSR223 Sampler "Declare annotations"
    │     vars.put("allure.name","Check user profile")
    │     vars.put("allure.label.feature","Users")
    │
    └─ HTTP Request "GET /api/user/1"
         └─ JSR223 Assertion  Parameters = (empty)  → solo
```

**When to use:** simple API checks, smoke tests, requests inside a loop.

---

### 3.2. Multi-Step — Chain of Requests = One Test Case

```
Test Plan
└─ Thread Group
    ├─ JSR223 Sampler "Declare annotations"
    │     vars.put("allure.name","Create order flow")
    │     vars.put("allure.label.story","Order")
    │
    ├─ HTTP Request "POST /login"          → JSR223 Assertion Parameters = start
    ├─ HTTP Request "POST /cart/add"       → JSR223 Assertion Parameters = continue
    └─ HTTP Request "POST /order/checkout" → JSR223 Assertion Parameters = stop
```

**When to use:** business scenarios consisting of several steps (login → action → verification).

---

### 3.3. Start+Stop in a Single Step

Parameters: `start,stop` (comma-separated, space optional).

```
HTTP Request "GET /health"
  └─ JSR223 Assertion Parameters = start,stop
```

Equivalent to a multi-step test with a single request.  
**When to use:** when you want to explicitly mark case boundaries, but there is only one step.

---

## 4. What Happens If You Skip `stop`?

### Scenario 1: The Next `start` Will Clean Up

```
Case A:  start → continue
Case B:  start → continue → stop
```

When the `start` of case B runs:
- `prevMainSteps` from case A will be reset.
- `_allureCaseUUID` will become new.
- **Case A will not appear in the final summary** (no counters, no console log), but the **`*-result.json` file for case A has already been written** on every step — Allure will see it, just without final polish.

### Scenario 2: Thread Crashed or `critical` Stopped It

```
start → continue (failed, critical) → thread stopped → stop is never called
```

In this case:
- `prevMainSteps` and `_allureCaseUUID` remain in thread memory.
- The JSON file is written (was overwritten on every step), but **without final counters**.

### Scenario 3: Solo After an Unfinished Multi-Step Without `stop`

```
start → continue
(solo) HTTP Request "Get status"  → Parameters = (empty)
```

Solo will perform its own cleanup afterwards.  
But `prevMainSteps` from the previous `start` will remain in variables until a new `start` is encountered or teardown runs.

> **Important:** if the next case after an orphaned `start` does not override `epic`/`story`/`feature`, old labels will leak into the report. For guaranteed cleanup use the `allure.clearLabels` pattern (see `docs/jallure_usage.md` §10.1).

---

## 5. Complex Structures — Examples

### 5.1. Conditional `stop` (If Controller)

```
Thread Group
├─ JSR223 Sampler "Declare annotations"
│     vars.put("allure.name","Conditional flow")
│
├─ HTTP Request "Step 1"    → Assertion Parameters = start
├─ HTTP Request "Step 2"    → Assertion Parameters = continue
├─ If Controller (condition: some check)
│   └─ HTTP Request "Final step"
│       └─ Assertion Parameters = stop
└─ If Controller (condition: inverse check)
    └─ HTTP Request "Alt final step"
        └─ Assertion Parameters = stop
```

**Main point:** there must be **exactly one** `stop` per `start`, otherwise the case will be duplicated in counters.

---

### 5.2. Multi-Step Inside a Loop Controller

```
Thread Group
├─ JSR223 Sampler "Declare annotations"
│     vars.put("allure.name","Loop case")
│
└─ Loop Controller (loops = 3)
    ├─ HTTP Request "Action"  → Assertion Parameters = start      (iteration 1)
    ├─ HTTP Request "Action"  → Assertion Parameters = continue  (iteration 2)
    └─ HTTP Request "Action"  → Assertion Parameters = stop      (iteration 3)
```

**Important:** annotations (`allure.name`, etc.) must be set **before entering the loop**, otherwise on the second iteration `start` will perform cleanup and erase them.

If you need different names on each iteration — set them **before** `start` inside the loop:

```
Loop Controller
├─ JSR223 Sampler "Set name"
│     vars.put("allure.name","Iteration " + vars.get("__jm__Loop Controller__idx"))
│
├─ HTTP Request "Action"
│   └─ Assertion Parameters = start    (only on idx=0)
│
... (continue / stop)
```

---

### 5.3. Mixed Plan: Multi-Steps + Solo

```
Thread Group
├─ GenericController "Case 1: Auth flow"
│   ├─ JSR223 Sampler "Annotations"
│   ├─ HTTP Request "Login"       → start
│   ├─ HTTP Request "Get token"   → continue
│   └─ HTTP Request "Validate"    → stop
│
├─ GenericController "Case 2: Quick checks"
│   ├─ HTTP Request "Health"      → (solo, empty)
│   └─ HTTP Request "Metrics"     → (solo, empty)
│
└─ GenericController "Case 3: Order"
    ├─ JSR223 Sampler "Annotations"
    ├─ HTTP Request "Create"      → start
    └─ HTTP Request "Verify"      → stop
```

**Rule:** Solo and multi-step can be mixed freely. Each `start` is a new case, each solo is an independent case.

---

## 6. Where and When to Set Annotations

**Annotations must be in `vars` BEFORE calling `jallure.groovy`.**

Correct — **before the first step**:

```groovy
vars.put("allure.name","My Case")
vars.put("allure.description","Detailed info")
vars.put("allure.label.feature","Orders")
vars.put("allure.label.story","Positive")
vars.put("allure.label.severity","critical")
vars.put("allure.label.tags","smoke,api")
vars.put("allure.links","issue,https://jira.example.com/ORDER-123")
vars.put("allure.parameters","userId,orderId")
vars.put("allure.feature.suffix","_DEBUG")
```

**Why order matters:**
- `start` performs cleanup, **but preserves** annotations already present in `vars`.
- If you set `allure.name` **after** `start` — it will not be seen (or an old value will be seen).

**Step-level parameters:**
If you need to pass parameters for only one step (not for the whole case), use a modifier in Parameters:
```
start parameters=[userId,orderId]
```
These parameters will go only into the current step, unlike `allure.parameters`, which go at the test case level.

---

## 7. Checklist "Why the Report Looks Wrong"

| Symptom | Cause | Fix |
|---------|-------|-----|
| Two tests merged into one | First step of the second case lacks `start` | Add `start` to the first step |
| Test is duplicated in the report | `stop` called twice for one case | Leave `stop` only on the last step |
| Missing test cases | `stop` skipped, thread crashed | Make sure every `start` has a matching `stop` |
| No labels / name | Annotations set after `start` | Move the JSR223 Sampler with annotations **above** the first step |
| Extra labels (epic/story) in a test | Previous orphaned case did not reach `stop` | Use the `allure.clearLabels` pattern before declaring annotations (see `docs/jallure_usage.md` §10.1) |
| Steps in the report are out of order | Time-drift on the machine | Already fixed in v1.0.0 (script guarantees monotonicity of `start`) |

---

## 8. Final Formula

```
Solo:    [Annotations] → Request → Assertion(empty)
Multi:   [Annotations] → Request → Assertion(start) → Request → Assertion(continue) → ... → Request → Assertion(stop)
Single:  [Annotations] → Request → Assertion(start,stop)
```

**Remember:**
- **`start` — new case, cleanup, new UUID.**
- **`continue` — append a step to the current case.**
- **`stop` — finalize the case, counters, cleanup.**
- **Empty parameter — solo, handles everything itself.**
- **The next `start` always begins a new case**, even if there was no `stop`.
