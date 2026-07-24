# LeafFeed Design Language

LeafFeed is quiet, clear, content-first, and predictable. The interface should help people scan,
choose, and read without competing with the article. This document is the implementation contract
for new and updated UI.

## Visual Language

- Use semantic `MaterialTheme.colorScheme` roles. Do not add page-local brand colors. `primary` is
  for the current or primary action, `error` is only for failures and destructive actions, and
  neutral surface roles carry structure.
- Use `MaterialTheme.typography`. Page titles use headline or title roles, item titles use title
  roles, content uses body roles, and metadata uses label roles with `onSurfaceVariant`.
- Use `LayoutTokens` for shared spacing and touch targets. Keep article content unframed; use cards
  only for repeated, independently actionable items or genuinely bounded tools.
- Use `ShapeTokens`. Controls use `Control`, bounded content uses `Surface`, sheets use `Sheet`, and
  pills are reserved for selection or compact status.
- Icons come from the Material icon set and use a stable touch target. Pair text with an icon only
  when the command is not clear from the icon or accessibility label.

## Motion Language

- Use `MaterialTheme.motionScheme`: effects specs for opacity and color, spatial specs for size,
  position, and scale. Avoid component-local duration values.
- Navigation uses the shared horizontal axis. Forward content enters from the end; back navigation
  reverses the same movement. Sheets enter and leave from their physical edge.
- State feedback is immediate and subtle. Press feedback may reduce opacity and scale to
  `MotionTokens.PressedScale`; content motion must remain interruptible.
- Do not animate decorative elements indefinitely. Indeterminate motion is allowed only while work
  is actively in progress. System animator-duration settings remain authoritative.

## Interaction Language

- Preserve scroll position, filters, and user input when navigating back.
- A screen has one visually dominant action. Secondary actions use tonal, outlined, or icon
  treatments; destructive actions use the error role and require confirmation when irreversible.
- Every asynchronous surface accounts for loading, empty, offline, error, and populated states.
  State messages explain what happened and offer the next useful action.
- Prefer undo for reversible list operations. Never rely on color or animation alone to communicate
  a state change.
- Labels and terminology are stable across feeds, article lists, reading, and settings. User-facing
  concepts should not expose storage, sync, or navigation implementation details.

## Review Checklist

Before merging UI work, verify dynamic light and dark themes, large text, RTL, narrow phones, and an
expanded window. Check that touch targets are at least `LayoutTokens.MinimumTouchTarget`, text does
not overlap or truncate primary information, back navigation restores context, and motion uses the
shared scheme.
