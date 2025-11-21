<!--suppress HtmlDeprecatedAttribute -->
<p align="center"><img src="https://github.com/STEMMechanics/.github/blob/main/stemcraft-sky-logo.jpg?raw=true" width="666" height="198" alt="STEMMechanics"></p>

# STEMCraftLib

This is the core STEMCraft plugin that provides the core functionality and helper methods for the STEMCraft server and other STEMCraft Plugins.

## Requirements

- Java 21
- Paper 1.21.10 or higher


## Usage

You can visit the [Wiki](https://github.com/STEMCraft/STEMCraftLib/wiki) for details on the plugin commands, classes, and using as a dependency.

## Builds & API

We provide an up-to-date plugin build over on our [Jenkins server](https://jenkins.stemmechanics.com.au/job/STEMCraft/).

To include the API in your project, add the repository to your project:

```
repositories {
    maven {
        url = uri("https://repo.stemmechanics.com.au/maven-public/")
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
