Kotlin dex/jvm debug info test generator for [intellij-community #2017](https://github.com/JetBrains/intellij-community/pull/2017)

- update paths and run the script in `data/run_d8.sh`
- run the generator with the path to the kotlin source root, i.e., `./gradlew run --args="path-to-kotlin-repo"`

The existing tests under `generated` were extracted from Kotlin v1.6.21.
The `generated/build.txt` file contains the corresponding commit hash.