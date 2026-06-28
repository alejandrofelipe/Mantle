![Mantle logo](https://raw.github.com/SlimeKnights/Mantle/master/src/main/resources/Mantle.png)
# Mantle
**Shared library for NeoForge mods — Minecraft 1.21.1 / NeoForge 21.1.234**

> This is the [`alejandrofelipe`](https://github.com/alejandrofelipe/Mantle) fork, ported to **MC 1.21.1 / NeoForge** from the [SlimeKnights](https://github.com/SlimeKnights/Mantle) 1.20.1/Forge original. It provides the shared code (registration helpers, a JSON/network serialization framework, fluids, recipes, a book system, and more) that mods like Tinkers' Construct build on.

## Documentation

- **[Documentation index](docs/README.md)** — start here.
- **API guides** — how to use each subsystem:
  [registration](docs/api/registration.md) ·
  [data & loadables](docs/api/data-loadables.md) ·
  [predicates](docs/api/predicates.md) ·
  [fluids](docs/api/fluids.md) ·
  [recipes](docs/api/recipes.md) ·
  [networking](docs/api/networking.md) ·
  [client & models](docs/api/client-models.md) ·
  [books](docs/api/books.md) ·
  [utilities](docs/api/utilities.md)
- **[1.20 → 1.21.1 migration guide](docs/migration/1.20-to-1.21.1.md)** — every API change, before → after, for porting a Mantle-dependent mod.

## Build from source

Requires **JDK 21** (Minecraft 1.21.1). The build toolchain is **NeoGradle 7.1.38 / Gradle 9.2.1**.

```bash
./gradlew build        # compiles + builds the jar
./gradlew runClient    # launch the client
./gradlew runData      # run data generators
```

See **[docs/COMMANDS.md](docs/COMMANDS.md)** for the canonical, copy-paste commands (and the Windows/PowerShell + `JAVA_HOME` notes that avoid failed runs).

## Using Mantle as a dependency

This fork is not published to a public Maven repository. To depend on it, build it locally and consume it via one of:

```groovy
// Option A: publish to your local Maven (~/.m2) and depend on it
//   ./gradlew publishToMavenLocal
repositories { mavenLocal() }
dependencies { implementation "slimeknights.mantle:Mantle:1.21.1-<version>" }

// Option B: JitPack (point at this fork + the 1.21.1 branch/commit)
repositories { maven { url = 'https://jitpack.io' } }
dependencies { implementation "com.github.alejandrofelipe:Mantle:1.21.1-SNAPSHOT" }
```

## Issue reporting

Please include:

- Minecraft version (1.21.1)
- NeoForge version (e.g. 21.1.234)
- Mantle version
- Versions of any Mantle-dependent or potentially related mods
- For crashes: steps to reproduce and the `logs/latest.log` (and the crash report from `crash-reports/`)
- Any relevant screenshots

## License

The MIT License (MIT)
Copyright (c) 2013-2022 Slime Knights (mDiyo, fuj1n, Sunstrike, progwml6, pillbox, alexbegt, KnightMiner)

1.21.1/NeoForge port: this `alejandrofelipe` fork. All original copyright and the MIT terms below are retained; the port is provided under the same license.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Any alternate licenses are noted where appropriate.
