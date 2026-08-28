<!--suppress HtmlDeprecatedAttribute -->
<p align="center"><img src="docs/images/stemcraft.jpg" width="666" height="198" alt="STEMMechanics"></p>

# STEMCraft

This is the core STEMCraft plugin that provides the core functionality and helper methods for the STEMCraft server and other STEMCraft Plugins.

## Requirements

- Java 25
- Paper 26.2 or higher


## Usage

- Players: read the [STEMCraft Player Handbook](docs/pub/README.md).
- Developers and administrators: read the [STEMCraft Wiki](https://github.com/STEMMechanics/stemcraft/wiki).

## Builds & API

Releases for the current default branch are published through GitHub Actions and GitHub Releases.

To include the API in your project, add the repository to your project:

```
repositories {
    maven {
        url = uri("https://maven.stemmechanics.com.au/")
    }
} 
```

Add STEMCraft API codebase as a dependency:

```
dependencies {
    compileOnly("dev.stemcraft:stemcraft-api:1.0.0-SNAPSHOT")
}
```

To access the API in your code:

```
STEMCraftAPI.api(); 
```

`STEMCraftAPI.api();` may be null until after the STEMCraft plugin enables.

## Get in touch!

Learn more about what we're doing at [stemmechanics.com.au](https://stemmechanics.com.au).

👋 [@STEMMechanics](https://twitter.com/STEMMechanics)
