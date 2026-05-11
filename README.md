# WithingsScaleTracker Android

Native Android version of the Withings scale tracker.

## Data Source

- Scale data comes directly from Withings Cloud.
- Sleep and a daily Apple Health snapshot can be imported from the Mac bridge.
  Send Apple Health data from the iPhone app to the Mac app first, then use
  `Import from Mac` in Android.
- Credentials and OAuth tokens are stored in Android Keystore-backed local storage.
- No Withings client secret, OAuth token, signing key, or personal health export is committed.

## Withings Redirect URL

Add this registered URL in the Withings developer dashboard:

`withings-scale-tracker-android://oauth/callback`

Then open the app Settings screen, enter the Withings `Client ID` and `Client Secret`, save, and connect.

## Apple Health Import

1. Keep `WithingsScaleBar` running on the Mac.
2. In the iPhone app, go to `Sleep`, allow the requested Health categories, and send Apple Health data to the Mac.
3. In Android, go to `Health` or `Sleep`.
4. Enter the Mac bridge host or Tailscale IP if it is not already saved.
5. Tap `Import from Mac`.

The Health tab shows the latest cached Apple Health snapshot: steps, active
energy, walking/running distance, exercise minutes, flights, heart rate, resting
heart rate, respiratory rate, and blood oxygen when those values are available.
It is not live Apple Health access on Android; it is the last iPhone snapshot
that was sent through the bridge.

The Android app also exposes a direct receiver on port `8766`, but direct
iPhone-to-Pixel LAN delivery can fail while VPN routing is active.
