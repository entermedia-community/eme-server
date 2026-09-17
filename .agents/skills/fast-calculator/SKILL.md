---
name: fast-calculator
description: Use when the user asks to calculate something in the desktop Calculator app, speed up calculator calculations, enter an arithmetic expression into the Calculator GUI, or verify a Calculator result using cua-driver. Prefer one foreground type action plus Enter over per-button clicking.
---

# Fast Calculator Automation

Use this workflow for the local desktop `Calculator` window when the goal is to evaluate an arithmetic expression quickly and reliably.

## Fast path

1. Find the live Calculator target:
   - Call `cua-driver_list_windows` with `on_screen_only: true`.
   - Select the window whose title is `Calculator`.
   - Record its current `pid` and `window_id`; do not reuse stale values from earlier turns.

2. Activate it once:
   - Call `cua-driver_bring_to_front` with that `pid` and `window_id`.
   - This Calculator has been unreliable for background input, so foreground delivery is the default.

3. Take one compact screenshot:
   - Call `cua-driver_get_window_state` with `include_accessibility_tree: false` and a small `max_dimension`.
   - Use the screenshot to confirm the display area and clear button before typing.
   - If the window moved or resized, re-derive coordinates from this screenshot instead of trusting old values.

4. Clear stale input:
   - If the display already contains text, click the red X / current-entry clear control on the right side of the display.
   - Last known coordinate for that control was approximately `(85, 327)` in a `732x589` window at `+990+241`, but verify visually before clicking.

5. Type the whole expression:
   - Use `cua-driver_type_text`.
   - Set `delivery_mode: "foreground"`.
   - Target the display area with `x` and `y`; last known display center was approximately `(326, 240)`.
   - Type digits and operators only, for example `545643455+5433`.
   - Do not include `=` in the typed text; a literal equals sign does not evaluate the expression.

6. Evaluate:
   - Use `cua-driver_press_key` with `key: "Enter"` and `delivery_mode: "foreground"`.
   - Do not use `Return`; it was rejected as an unknown key.

7. Verify once:
   - Call `cua-driver_get_window_state` with `include_accessibility_tree: false`.
   - Read the result from the display screenshot.
   - If the number is long or clipped, zoom into the display region before reporting the answer.

## Known coordinates

These are hints from a previous run, not stable constants:

| Control | Last known coordinate | Notes |
| --- | ---: | --- |
| Display center | `(326, 240)` | Good target for `type_text` |
| Red X / clear current entry | `(85, 327)` | Use when stale input is present |
| Blue equals button | `(362, 490)` | Pixel clicking this was unreliable; prefer `Enter` |

## Error recovery

- If typing inserts an unexpected character, such as `x`, clear the current entry with the red X control and retype the expression.
- If `Enter` does not evaluate the expression:
  1. Click the display area once with foreground delivery to ensure focus.
  2. Press `Enter` again.
  3. If it still fails, take a fresh `get_window_state` with the accessibility tree enabled and click the correct equals element using its current `element_token` or `element_index` plus `snapshot_id`.
- If a click or key action reports a stale token or missing window:
  - Re-run `cua-driver_list_windows`.
  - Re-select the live `Calculator` window.
  - Re-run `bring_to_front` before continuing.
- If multiple Calculator windows exist, prefer the frontmost or most recently active one; if ambiguous, ask the user which window to use.

## Speed rules

- Avoid per-digit button clicking unless keyboard input fails.
- Avoid repeated full accessibility-tree dumps; use screenshot-only state reads for normal verification.
- Batch the workflow into: list windows, bring to front, compact screenshot, type expression, press Enter, verify screenshot.
- Report only the final evaluated result once the display confirms it.
