# Building from Source

This document describes how to compile and test NexAuth from source code.

## Requirements

- **Java Development Kit (JDK)**: Version 21 or newer.
- **Git**: Version 2.30 or newer.
- The project includes the Gradle Wrapper (`gradlew`), so a standalone Gradle installation is not required.

## Compilation

Clone the repository and build using the Gradle Wrapper:

```bash
git clone https://github.com/titanxdevz/NexGate.git
cd NexGate

# Build and package all modules
./gradlew shadowJar
```

### Output Artifacts

Compiled JARs will be generated in each module's `build/libs/` directory:
- Velocity Proxy Plugin: `nexgate-velocity/build/libs/NexGate-Velocity-1.0.0.jar`
- Paper Backend Plugin: `nexgate-paper/build/libs/NexGate-Paper-1.0.0.jar`
- Common Library: `nexgate-common/build/libs/NexGate-Common-1.0.0.jar`

## Running Automated Tests

Run the full test suite across all subprojects:

```bash
./gradlew test
```

To run a verification build including style and check tasks:

```bash
./gradlew check
```

## Local Development Deployment

The build script includes a `deployServer` task that builds shadow JARs and deploys them to the local `minecraft-server/` test cluster:

```bash
./gradlew deployServer
```

To start the local three-node test network (Velocity proxy on 25565, Paper auth on 25566, Paper lobby on 25567):

```bash
./minecraft-server/start.sh
```

To stop the test cluster:

```bash
./minecraft-server/stop.sh
```
