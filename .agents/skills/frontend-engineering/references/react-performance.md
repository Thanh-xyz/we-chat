# React performance and correctness rules

Read when a frontend change touches rendering of message or conversation lists, Zustand stores, effects, or the realtime client. Apply a rule only when the change touches its area; prefer measurement (React DevTools Profiler, the browser Performance panel) over speculative memoization.

## State (Zustand)

- **Subscribe narrowly.** Use a selector (`useStore((s) => s.x)`) or a derived boolean, not the whole store. Selecting a new object or array literal on every render causes re-renders unless you use a shallow comparator.
- **Update immutably.** Replace changed arrays and maps instead of mutating them, or subscribers will not see the change.
- **Read lazily.** Read store state inside event handlers with `getState()` when the component does not need to re-render on that value.
- **One owner per kind of state.** Keep server-derived data (messages, conversations) in its existing store and do not copy it into component state.

## Effects and listeners

- Every subscription, timer, STOMP subscription, or window listener created in an effect is cleaned up in that effect's return.
- Effects that depend on a connection must not resubscribe on every render. Check their dependency arrays.
- Deduplicate global listeners. The realtime manager in `src/services/websocket` owns the connection and subscriptions; components subscribe through it rather than opening their own.

## Lists

- Messages need stable keys from the server ID, or the client message ID before acknowledgement. Never use array indexes as keys.
- Merging history pages with realtime events must deduplicate by ID and keep a total order (`created_at`, then `id`).
- For long conversations, consider windowing or virtualization when profiling shows the cost, and preserve scroll position when older pages are prepended.

## Async UI

- Parallelize independent requests (`Promise.all`).
- Cancel or ignore stale responses when the user switches conversation (`AbortController` or a request token).
- Disable submit controls while a send or upload is in flight to prevent double submits, and show pending, failed, and retry states.
- Debounce search input (about 300 ms) and cancel superseded searches.

## Bundle

- Use route-level code splitting (`React.lazy`) for heavy, rarely used screens.
- Import only what is needed from icon packages (`lucide-react` supports per-icon imports).
