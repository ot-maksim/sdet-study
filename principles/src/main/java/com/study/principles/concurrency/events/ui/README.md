# UI events: arm the waiter before the click

A browser page is a concurrent program of its own: after `click` returns, the page's scripts keep running on
**its** thread. Requests are answered, dialogs pop up, toasts appear and hide while the test is busy taking a
screenshot. The rule of `events` holds here too, and a good UI API is built around it: **arm the waiter before
the action** (`waitForResponse(predicate, action)`, a dialog handler before the click), **assert with retries**,
not with snapshots, and for things that are meant to vanish, **control the page's clock** instead of racing it.

Part of [`concurrency/events`](../README.md). Here the "client" is the browser automation API, so the kata
has no client of its own: `FakePage` has the **shape of Playwright's `Page`**, and `bad`/`good` are page objects
written against it. Every pattern carries over to real Playwright one to one.

| Package | What to look at |
|---|---|
| `ui` | `FakePage` (scripts on `browser-main`; `click`, snapshot `textContent`/`isVisible`, `waitForResponse(predicate, action)`, `onDialog`/`onceDialog` with auto-dismiss, `clock()`, `waitForCondition`, a 100 ms `screenshot`), `PageClock` (`install` → fake paused timers, `runFor`), `Expect` (web-first assertions: retry until the timeout), `CheckoutApp` (the SUT: confirm dialog → `POST /api/payments` → `PAID` + a toast that hides itself), `UiEventsDemo` |
| `bad` | `CheckoutPage`: dialog handler after the click; response waiter armed after the click and the step screenshot; status as a snapshot; toast found by polling every 500 ms |
| `good` | `CheckoutPage`: `onceDialog` before the click, click inside `waitForResponse`, state as retrying assertions, the dialog's message recorded and asserted in the test |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.events.ui.UiEventsDemo
mvn -q -pl principles test -Dtest='com.study.principles.concurrency.events.ui.**.*Test'
```

`UiEventsDemo`, 20 runs each (the API answers in 0–150 ms, a screenshot takes 100 ms, the toast lives 300 ms):
```
bad: click, screenshot, waitForResponse       7/20 ok
good: waitForResponse(predicate, click)      20/20 ok
bad: other checks, then expect the toast      1/20 ok
good: clock installed, same order            20/20 ok
```

## 1. Dialogs: the handler goes first

Playwright **dismisses dialogs automatically** when there is no handler. `click(PAY)` then
`onceDialog(Dialog::accept)` reads naturally and does the opposite: the `confirm` came **during** the click,
nobody listened, it was dismissed, and the payment was cancelled
(`dialogHandlerAfterTheClick_dialogWasAutoDismissed_paymentCancelled`). There is no error: the page simply
did what a user pressing "Cancel" would get.

- Register the handler **before** the action. Prefer `onceDialog`: a persistent `onDialog(Dialog::accept)` from
  one step silently accepts the "Delete everything?" of a later step.
- The handler runs on the browser's thread: **record** the message there, **assert** it in the test
  (`good.CheckoutPage.lastDialogMessage`). A handler that neither accepts nor dismisses hangs a real page.

## 2. Responses: run and wait

`waitForResponse(predicate, action)` arms the waiter, runs the action, then waits. Its signature is the lesson:
the API does not let you wait for "the next response" without saying which action causes it.

The bad page object does `click`, then a step-reporter `screenshot()` (100 ms), then
`waitForResponse(pred, () -> {})`. Any response faster than the screenshot (cached, stubbed with `page.route`,
or just a fast stand) has already passed, and the wait times out **although the payment went through**
(`responseWaiterArmedAfterTheScreenshot_missesAFastResponse_thoughThePaymentWentThrough`). The faster the
environment, the flakier the test: the opposite of what people expect. Put everything that follows the action
**inside** the callback, or after the wait.

The same shape exists for other one-off events: `waitForPopup`, `waitForDownload`, `waitForRequest`,
`waitForNavigation`/`waitForURL`, `waitForConsoleMessage`. They all take the action as a callback.

## 3. Snapshots vs retrying assertions

`textContent`, `isVisible`, `getAttribute` are **snapshots**: they answer for this instant
(`snapshotRightAfterTheClick_seesTheIntermediateState` sees `PROCESSING`). `assertThat(snapshot).isEqualTo("PAID")`
is the `sleep`-free version of the bad wait in the Kafka kata: right after the action, nothing is there yet.

Web-first assertions (`Expect.that(page, STATUS).hasText("PAID")`, Playwright's
`assertThat(locator).hasText(..)`) retry until the condition holds or the timeout runs out, and on failure say
what they saw last (`retryingAssertion_failsWithWhatItSawLast`). Page objects should expose **locators or
assertions**, not `String getStatus()`.

## 4. Transient elements: race them or stop the clock

A toast, a spinner, a "Saved" badge live for a moment by design. Three ways to check one:

| Way | Outcome |
|---|---|
| poll `isVisible` every 500 ms (Selenium `WebDriverWait`'s default interval) | misses anything shorter than the interval (`pollingEvery500ms_missesAToastShorterThanTheInterval`) |
| a retrying assertion **right after** the action | works while nothing slow comes first (`retryingAssertionRightAfterTheAction_catchesAShortToast`); a screenshot, a DB check or a slower CI agent in between, and it is too late (`slowChecksBeforeTheToast_evenARetryingAssertionComesTooLate`) |
| **install the page clock** before the action | the toast's `setTimeout` waits for the test; the test checks in any order, then moves time and asserts the hide **exactly** (`installedClock_..._andItsTimeoutIsTestedExactly`: visible at 299 ms, hidden at 300 ms) |

Controlling the clock is the same move as injecting a `Clock` (`principles/time`): do not race time, own it. In
Playwright: `page.clock().install()` **before** the page sets its timers (often before `navigate`), then
`pauseAt` / `runFor` / `fastForward`. Timers set before `install` keep real time, in the fake as in the browser.

The fake's `Expect` wakes up on every DOM change, as a `MutationObserver` would. Real assertions retry at short
intervals, so a very brief element can still slip between two checks. One more reason to stop the clock instead of
tuning timeouts.

## UI vs the other transports

| | Kafka / WebSocket / webhook | UI |
|---|---|---|
| who listens | a client the test owns | the automation API (Playwright, Selenium) |
| arm before the action | subscribe, wait for the ack, register the URL | handler or waiter **wraps** the action |
| state that disappears | messages with no replay | DOM elements with timers: stop the clock |
| an assertion on a foreign thread | listener, io thread, HTTP handler | dialog / event handlers on the browser's thread |

## Pros / cons

| Pros | Cons |
|---|---|
| Run-and-wait makes the ordering impossible to get wrong | Callbacks nest: an action that itself waits inside a `waitForResponse` reads worse |
| Retrying assertions remove `sleep` from UI tests | A retrying assertion on the wrong condition waits the full timeout before failing |
| Clock control makes timer-driven UI deterministic and fast | Must be installed before the page sets timers; affects every timer on the page (polling, animations) |
| `onceDialog` scopes a decision to one step | One more thing a page object method must arrange before acting |

## When NOT to apply

- **The response is not the point.** If the test is "the order is paid", assert the status on the page or
  through the API; waiting for `POST /api/payments` couples the UI test to the front end's network calls.
- **Do not wait for a response the action does not cause.** `waitForResponse(any, click)` on a page that also
  polls in the background catches the poll. Match on URL **and** method, or on a field of the body.
- **Do not install the clock in every test.** Pages with polling, animations or token refresh behave differently
  under fake time. Use it for the tests about timers; elsewhere a retrying assertion right after the action is enough.
- **Do not assert a toast that is not the contract.** If the product only promises "status becomes PAID", a toast
  check adds flakiness and no coverage.

## Try it yourself

1. Make `CheckoutApp` show a second dialog ("Save card for later?") after the payment. Which test breaks with
   `onceDialog`, which with `onDialog(Dialog::accept)`, and which way is correct for each step?
2. Add `waitForPopup(Runnable action)` to `FakePage` in the same run-and-wait shape. What does it have to return,
   and what happens to a popup opened before the waiter is armed?
3. Change the toast timer to start **before** the response (a "Processing…" toast replaced by "Paid"). With the
   clock installed, which `runFor` values expose the replacement, and what does that test now prove?
4. Write `bad.CheckoutPage.waitForStatus(String)` with `while (!status().equals(x)) Thread.sleep(200)`. List
   everything it lacks compared with `Expect` (deadline, message, wake-up), then delete it.
5. Smell check on your framework: `grep -rn 'onDialog\|waitForResponse(.*() -> { })\|isVisible()\|textContent()' --include=*.java`.
   For each hit: is the listener armed before the action, is the check a snapshot or a retrying assertion?
