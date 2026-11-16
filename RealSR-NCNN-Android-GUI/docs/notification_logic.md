# Notification Channels & Foreground Service Behavior

RealScale-Pro uses a bound foreground `ProcessingService` so multi-tile GPU/CPU upscales complete reliably when the user taps **Continue in background**, switches apps, or locks the screen.

## Notification Channels

| Channel ID | Name | Importance | Sound / Vibration | Purpose |
| --- | --- | --- | --- | --- |
| `channel_progress` | Processing Progress | `IMPORTANCE_LOW` | Silent | Ongoing live progress bar, percentage/stage summary, and **Cancel** action; tapping reopens the active `ProcessingScreen` (`EXTRA_OPEN_PROCESSING`). Throttled to 500 ms updates. |
| `channel_service_alive` | Background Service | `IMPORTANCE_LOW` | Silent | Minimal ongoing indicator used in *Silent* and *Result Only* notification modes. |
| `channel_result` | Processing Result | `IMPORTANCE_HIGH` | Default alert | Auto-canceling `BigPictureStyle` completion or failure notification with direct **Share** and **Open** result actions. |

## Session Lifecycle

1. **Permission Primer:** On Android 13+ (API 33+), onboarding prompts for `POST_NOTIFICATIONS` through `MediaIntentsHost`. If declined, background processing still works normally using the in-app floating `RunningJobCard`.
2. **Active Processing:** Updates progress on `channel_progress` using the monochrome status-bar silhouette (`ic_stat_realscale`) and exposes an immediate `ACTION_STOP_TASK` cancel action.
3. **Completion / Cancellation:** `ProcessingService.finishSession()` clears the ongoing foreground notification and stops the service cleanly once the batch completes or is canceled, preventing stale progress rows.
