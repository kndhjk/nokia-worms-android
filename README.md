# Nokia Worms Android

A fresh Android project that starts recreating the classic Nokia-era Worms feel, using the legacy Java ME repository `kndhjk/worms-for-javame` as reference material.

## What is included

- Android app module in Kotlin
- Simple landscape prototype with two worms
- Turn-based firing
- Adjustable angle and power with touch controls
- Legacy assets copied into `app/src/main/assets/legacy/`

## Touch controls

- Tap left side: increase angle
- Tap right side: decrease angle
- Tap top: increase power
- Tap bottom: decrease power
- Tap center: fire

## Legacy source/material

Reference repo inspected:
- `git@github.com:kndhjk/worms-for-javame.git`

Legacy PNG/text/data files copied for future extraction and adaptation.

## Next steps

1. Replace placeholder rendering with extracted sprite sheets
2. Port terrain generation and weapon logic from Java ME source
3. Add destructible terrain
4. Add turn timer, wind, and multiple weapons
5. Add sound effects and title/menu flow

## Build

Typical build command:

```bash
./gradlew assembleDebug
```

If Gradle wrapper is missing, generate it with a compatible Gradle installation.
