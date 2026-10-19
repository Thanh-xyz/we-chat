# Accessibility and interaction checklist

Read when designing or reviewing interactive UI. Target WCAG 2.1 AA. Record each relevant item as met, not met, or not verified. A unit test or build does not verify contrast, focus order, or screen-reader output.

## Perception

- Text contrast is at least 4.5:1, or at least 3:1 for large text and for UI component boundaries and focus indicators. Use design tokens from `src/styles-tokens.css` rather than ad-hoc colors.
- Color is never the only signal for unread, error, online, or selected states.
- Icon-only buttons (send, attach, close, menu) have accessible names.
- Images and avatars have meaningful `alt` text, or empty `alt` when they are decorative.

## Operation

- Every action works with the keyboard, in a logical tab order, with a visible focus indicator.
- Dialogs and menus trap focus while open, close on `Escape`, and return focus to the trigger.
- In the message composer, `Enter` sends and `Shift+Enter` adds a new line, or follows the documented project convention; this must be discoverable.
- Touch targets are at least 44×44 px on mobile layouts.
- Motion respects `prefers-reduced-motion`.

## Feedback and states

- Every async view has loading, empty, error, and retry states. A layout does not jump when content arrives.
- Errors are tied to their fields (`aria-describedby`) and announced (`role="alert"` or an `aria-live` region).
- New incoming messages in the open conversation are announced politely (`aria-live="polite"`) without stealing focus. Unread counts update their accessible label.
- Buttons are disabled with a visible pending state during in-flight actions. Toasts are not the only record of a failure.

## Responsive

- Check at 320, 768, 1024, and 1440 px wide. The conversation list and the message pane remain usable, with no horizontal page scroll and no content hidden without an alternative.

## Verification

- Use keyboard-only navigation of the changed flow, a contrast check of the changed tokens, and Playwright or a manual browser check when the environment is available.
- Report unverified items explicitly.
