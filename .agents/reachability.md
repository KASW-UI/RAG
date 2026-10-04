# Task guide — proving a change is reached

How to show that something reaches the code you landed. The rule is
[`AGENTS.md`](../AGENTS.md) `## Nothing lands dead`; this is the method. Nothing
here is binding on its own, and nothing here may weaken the rule there.

## The case this exists for

A business capability can be implemented correctly, have a green unit test, and
still be unreachable from the real application.

For example, a new service method may have a complete test and correct
implementation, while no controller, RPC handler, message consumer, scheduled
job, or other production entry point actually calls it.

The feature is real, the test is real, and the test is honest. No user can arrive
at it.

The normal test suite does not necessarily catch this. A unit test that directly
constructs or invokes the changed class proves that the class works when called.
It does not prove that the application can arrive there.

The reachability check exists to ask that second question.

## The shapes

Dead is one defect wearing different clothes. Each of these can occur in an
ordinary Java application:

| **Shape**                         | **What it looks like**                                                                                                                                                                    |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **The unpassed dependency**       | A method, service, or handler gains a new parameter or dependency, but every production caller continues using a default, fallback, or old path.                                          |
| **The unselected branch**         | A code path is keyed on a configuration value, profile, feature flag, request field, or business state that no deployed configuration or real request can select.                         |
| **The flag with no enabled path** | A capability exists behind a property or feature flag, but no production configuration enables it.                                                                                        |
| **The unregistered component**    | A controller, handler, listener, consumer, scheduler, Bean, or other component exists but is not actually registered or discovered by the runtime.                                        |
| **The private surface**           | A capability can only be reached from an internal helper, test fixture, example, or package-local path, while no real application entry point can reach it.                               |
| **The test-only driver**          | The only thing that ever calls the new code is the test written for it.                                                                                                                   |
| **The bypassed path**             | The intended production entry point exists, but routing, configuration, dependency injection, or another branch causes real requests to use an older or different implementation instead. |

The last two are the hardest to see, because the gate is green and the
coverage is real. Coverage measures whether a test reached the code. It does
not measure whether the application can reach it through the intended
production path.

## Proving it

Answer both questions. They are not the same question.

**Does a production entry point reach this?** Name the entry point and the
relevant production path.

A production entry point is one a real user, service, event, or scheduled
operation can arrive through, such as:

* an HTTP controller endpoint;
* an RPC handler;
* a message or event consumer;
* a scheduled job;
* a command-line entry point;
* a public application service called by one of the above;
* another explicitly supported production integration point.

Follow the chain by hand from that entry point down to the changed behavior.

For a Spring application, this may require checking:

* controller or RPC mapping;
* dependency injection;
* Bean registration and discovery;
* configuration and profiles;
* feature flags;
* routing or dispatch;
* message listener registration;
* transaction boundaries;
* the actual service implementation selected at runtime.

An intermediate component that is itself unreachable does not carry reachability.

**Does a test enter through it?** The smallest integration or end-to-end test that
proves the intended production path starts at that production entry point rather
than directly at the changed class.

Keep the unit test as well — it localizes a failure, and that is worth having.
It is not the proof of production reachability.

For example:

```text
Unit test:

RegisterLogic.register()
        ↓
assert result
```

proves the logic works when directly invoked.

An appropriate reachability test is closer to:

```text
HTTP request
    ↓
Controller
    ↓
Service
    ↓
RegisterLogic
    ↓
assert externally observable result
```

The exact entry point depends on the application. Do not manufacture an
HTTP-level test when the capability is actually entered through a message
consumer or scheduled job.

Searching for the symbol answers neither question on its own. A call inside a
test, example, unused Bean, disabled configuration branch, or another
unreachable component is not production reach. Read what the matches actually
are.

## The reachability mutation

The mutation pass breaks the claimed production path. Perform it in a scratch
copy, never in the reviewed worktree.

Identify the production connection that is supposed to carry the request to the
changed behavior.

Then:

1. Remove, disable, or bypass the production connection that reaches the
   changed behavior. This may be the service invocation, controller-to-service
   call, handler registration, Bean wiring, route mapping, consumer
   registration, or selected dispatch branch.
2. Rerun the focused integration or end-to-end gate through the same production
   entry point.
3. A red gate proves that the test enters through the claimed production path.
   Record it as the reachability evidence.
4. A green gate is the finding. The test did not depend on the claimed path, or
   the changed behavior is not actually required by the production flow.
   Investigate and report it.
5. Restore the tree and test configuration to the original state before
   continuing.

The mutation should remove the connection to the changed behavior, not merely
delete an internal implementation detail.

For example, if the production path is:

```text
POST /users
    ↓
UserController
    ↓
UserService.register()
    ↓
RegisterLogic
```

do not prove reachability by deleting an assertion inside `RegisterLogic`.

Temporarily remove the production call or wiring that connects the controller
path to `RegisterLogic`, then rerun the endpoint-level test.

If the endpoint test remains green, it was not proving that production path.

A change that has no production entry point to identify has already answered the
question: it is not yet production-reachable.

## Landing a slice that is not reached yet

Staged work is legitimate. Landing a layer before the wave that wires it is a
normal shape in a multi-step Java change, and the rule does not forbid it. It
forbids doing it silently.

Put the reason in the commit body and pull request body. Name three things:

* what is not reached yet, in the specific — the service, branch, Bean,
  controller route, consumer, configuration, or entry point that does not exist
  yet;
* the task or row that owns the wiring;
* the issue that tracks it.

Then list it under `## Owed` in the row's spec.

A staged capability is different from dead code because the missing production
connection is explicitly owned by a later change.

A wave that lands its implementation without naming the change that wires it has
not made a staging decision. It has left unreachable code, and the next agent
has no way to tell the two apart.

## Why no checker enforces this

A checker that could reliably separate a live production path from a dead one
would have to understand the application's runtime wiring.

In a Java application, that may include dependency injection, component
scanning, Bean registration, proxying, controller mappings, conditional
configuration, profiles, feature flags, reflection, message listener
registration, scheduled tasks, and configuration-dependent dispatch.

A coarse checker such as:

```text
"the symbol appears outside its own test"
```

is not enough. It passes exactly the cases that matter.

A service method may appear in several production files and still be unreachable
because those callers are themselves unused, disabled, incorrectly wired, or
never selected by the application's actual configuration.

Likewise, a controller class may exist without the intended route being
registered, or a message consumer may exist without the relevant listener being
enabled.

So reachability is a review decision, like change size. This is recorded here
so that a later reader treats it as a decision with a reason rather than as a
gap somebody forgot to close.
