# OpenMaps

An open source maps app focused on public transit and walking, without Google services.

Maps use [OpenFreeMap](https://openfreemap.org) tiles built from [OpenStreetMap](https://www.openstreetmap.org/copyright) data. Transit routing and search will use [Transitous](https://transitous.org) ([data sources](https://transitous.org/sources/)).

## Development

The dev shell provides the JDK, Gradle and the Android SDK.

```sh
nix develop          # or direnv allow
./gradlew :app:android:assembleDebug
```

The shell sets `LOCAL_COMPOSE_KIT=../compose-kit` so a sibling compose-kit checkout is used instead of the JitPack artifact. Unset it to build against the published version.

## Modules

| Module | Purpose |
|---|---|
| `app/android` | Android application |
| `ui` | Screens, sheets, navigation and theme |
| `map` | Map rendering, the only module using maplibre-compose |
| `core/model` | Domain types |
| `core/platform` | HTTP client and platform services behind interfaces |
| `data/transit` | Transitous client for routing, departures and search |
| `data/geocoding` | Place details |
| `data/storage` | Local database |

## License

[AGPL-3.0](LICENSE)
