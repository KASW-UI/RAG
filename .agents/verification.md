# Task guide — gates, evidence, and review

How to prove something, and how to review someone else's proof. The rules are in
[`AGENTS.md`](../AGENTS.md); this is the method.

## Running a gate

Start with the smallest deterministic test that can falsify the spec. Preserve
the red result — a test that was never seen failing has proven nothing. Make it
green, run the declared focused gate, then run the full project verification.

The focused gate should exercise the smallest complete path that can establish
the behavior being changed. Do not substitute a test of an isolated helper for
a test of the actual business path when the behavior depends on the interaction
between components.

A gate report records the commit, the exact command, the relevant environment,
the exit status, and the evidence path when the project requires recorded
evidence. Do not replace these with a summary such as "tests passed".

A green test is evidence only for the behavior it actually exercised. Verify
that the test:

* reaches the production code whose behavior is being changed;
* supplies inputs that exercise the relevant branch or state;
* asserts the business result, state transition, side effect, or contract that
  the spec requires;
* would fail if the important behavior were removed or incorrectly implemented.

A test that passes while the changed behavior is absent is not evidence for that
behavior.

When a test depends on mocks, stubs, fixtures, test containers, embedded
services, or generated data, verify that they do not bypass the behavior being
proved.

## Baseline and environment

Before spending time proving that a failing check is caused by your change,
reproduce the relevant check against the appropriate baseline when practical.

Do not assume that a failure belongs to the current change merely because it
appeared after the change. Likewise, do not assume that a green result proves
the whole project is healthy when tests were skipped, excluded, disabled, or
allowed to fail.

When a build or test result is suspicious, remove the simplest environmental
source of false confidence first:

* clean and rebuild when stale compiled classes or generated sources may matter;
* rerun a flaky or parallel-sensitive test under the appropriate execution mode;
* verify that the intended profile, configuration, database, cache, or external
  dependency was actually used;
* verify that no test-specific mock or stub removed the behavior under test.

Do not impose a clean rebuild or isolated execution on every ordinary change.
Use it when the result could otherwise be misleading.

## Reviewing

Review happens only after the implementation's own gates pass, and only on the
same commit that was tested.

**Static pass:** review the spec, the diff, the tests, the error paths, ownership
boundaries, existing business rules, and whether the implementation actually
supports the claims made by the change.

Check in particular:

* Does the implementation satisfy the stated business behavior?
* Does the changed path preserve existing behavior outside the intended scope?
* Are validation and error conditions handled where the business rule requires
  them?
* Are authentication and authorization decisions enforced at the correct
  boundary?
* Are transaction boundaries consistent with the business operation?
* Are persistence, cache, RPC, and messaging side effects consistent with the
  intended state transition?
* Are retries, idempotency, concurrency, or locking relevant to the behavior?
* Are external API, RPC, event, database, or error contracts preserved?

Do not infer that these concerns are correct merely because the general test
suite is green.

**Test adequacy pass:** for each important business rule or critical guard,
identify what would happen if the corresponding implementation were removed,
bypassed, inverted, or weakened.

Where practical, verify the test by temporarily removing or corrupting that
behavior in a scratch copy and rerunning the focused gate. The test should fail
for the intended reason.

Do not require mutation testing for every ordinary Java change. Use this check
when the correctness of a critical rule cannot otherwise be established from
the existing tests.

The same principle applies to reachability: if the test is supposed to prove
that a particular production path is used, verify that the test actually
reaches that path rather than merely exercising a nearby class or mocked
interface.

Report `PASS` only after both the implementation's declared gates and the
reviewer's checks have passed on the same commit.

Every finding carries:

* the violated requirement;
* the affected business behavior;
* the reproduction or triggering condition;
* the expected behavior;
* the observed behavior.

Do not take another agent's report at face value; rerun the relevant gate when
the result matters.

## Make the test say what it is measuring, in its own output, in words

A criterion committed in advance protects against changing the expected result
after seeing the implementation. It does not protect against a test pointed at
the wrong behavior.

A test can return a perfectly valid `PASS` while proving something other than
what the spec requires.

For every important test, make the relationship between the test and the
business behavior auditable:

* **What** behavior is being tested?
* **What** input, state, or precondition is being established?
* **What** production path is expected to execute?
* **What** business result or side effect is expected?
* **What** assertion distinguishes the correct behavior from the incorrect one?
* **What** change would make the test fail?

Prefer explicit inputs, assertions, and named fixtures over conventions that
hide what the test is actually verifying.

When a test depends on a particular configuration, mock, database state,
permission, transaction, or external service, make that dependency visible
rather than relying on an implicit assumption.

A reviewer reading the test should be able to determine what the test proved
without reconstructing the entire implementation first.

## Evidence

Separate what you observed from what you inferred. Name relevant source files,
test names, commands, commit references, artifacts, and limitations when they
matter to the conclusion.

For an ordinary business change, the normal test result is usually sufficient
evidence. Additional evidence is required when the behavior cannot be
established by the normal test suite.

Record:

* the behavior that was being proved;
* the test or verification command used;
* the important result;
* relevant failures before the fix;
* important cases that were not verified.

A negative result is a result: record refuted hypotheses and failed attempts
when they materially affect the diagnosis. "Not established" is not the same
as "passed".

Do not record evidence merely for the sake of producing a report. The purpose
of evidence is to let another developer understand why the conclusion follows
from the verification that was actually performed.

## Before you call it done

Run the focused gate.

Then run the project's normal verification.

Confirm that the tests actually exercise the behavior required by the spec.

Confirm that important business rules and failure paths are represented in the
verification.

Confirm that the implementation did not change an existing contract or
business behavior outside the intended scope.

If an important behavior remains unverified, say so rather than treating a
general green build as proof that it is correct.

Record the relevant result in the task spec's `## Outcome`, especially when the
actual cause differed from the initial hypothesis or when an expected failure
mode could not be reproduced.
