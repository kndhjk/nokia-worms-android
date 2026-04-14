# Nokia Worms Android

> A fresh Android project recreating the classic Nokia-era Worms feel, using legacy Java ME assets as reference.

## Overview

This is an early-stage Android port of the classic Nokia Worms game. It draws from the legacy Java ME repository `kndhjk/worms-for-javame` for sprite sheets, terrain tiles, and game logic inspiration.

## Features

- **Two-player local**: PvP (two humans) and PvE (vs. AI opponent)
- **Turn-based artillery combat**: aim with an angle/power HUD, fire projectiles
- **Touch controls**:
  - Left oval: move worm left/right
  - Top-right oval: jump
  - Bottom-right oval: cycle weapon
  - Drag zone: aim and charge shot (hold longer = more power)
  - Tap center (title): start game
  - Tap center (game over): restart
- **Three weapons**: Bazooka, Grenade, Missile — each with different speed, blast radius, and damage
- **Destructible terrain**: explosions carve craters into the hill
- **Wind system**: random wind per turn affects projectile arcs
- **Turn timer**: 20-second countdown per turn
- **Fall damage**: worms take HP damage from high drops
- **Legacy graphics**: PNG tiles and sprites imported from the Java ME source

## Project Structure

```
app/src/main/
  java/com/kndhjk/nokiaworms/
    MainActivity.kt      # Minimal Android entry point
    GameView.kt          # All game logic, rendering, and touch handling
  assets/legacy/         # Copied legacy PNG assets (sprite sheets, tiles)
```

## Building

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

If the Gradle wrapper is missing, generate it with a system Gradle installation:

```bash
gradle wrapper --gradle-version=8.4
```

## Legacy Reference

- Source repo: [kndhjk/worms-for-javame](https://github.com/kndhjk/worms-for-javame)
- Legacy PNG/text/data files copied under `app/src/main/assets/legacy/`

## Next Steps

- [ ] Replace placeholder worm rendering with extracted sprite sheets
- [ ] Port terrain generation and weapon logic from Java ME source
- [ ] Add sound effects and title/menu flow
- [ ] Add multiple weapons beyond the three currently stubbed
- [ ] Expand AI with better pathfinding and weapon selection
- [ ] Add wind direction/strength indicators on-screen
- [ ] Implement save/load game state

## License

MIT — see [LICENSE](LICENSE).
