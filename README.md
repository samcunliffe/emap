# Emap

A monorepo for all core Emap functions

# Setup

The EMAP project follows this structure, for deploying a live instance of EMAP follow the instructions
in [docs/core.md](docs/core.md).

```
EMAP [your root emap directory]
├── config [config files passed to docker containers, not in any repo]
├── hoover [different repo]
├── emap [this repo]
│   ├── emap-star         [ formerly Inform-DB repo ]
│   ├── emap-interchange  [ formerly Emap-Interchange repo ]
│   ├── hl7-reader        [ formerly emap-hl7-processor repo ]
│   ├── core              [ formerly Emap-Core repo ]
│   ├── [etc.]
```

## Developer onboarding

- How to [configure IntelliJ](docs/dev/intellij.md) to build emap and run tests.
- [Onboarding](docs/dev/onboarding.md) gives details on how data items are processed and the test strategies used.


# Monorepo migration

How were [old repos migrated into this repo?](docs/dev/migration.md)
