# Third-party code and models in the app

- **sherpa-onnx** (Apache-2.0, https://github.com/k2-fsa/sherpa-onnx): `app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt` is its Kotlin API, copied from tag v1.13.8 (the package name must stay: the native library looks the classes up by name). The native libraries are downloaded at build time by the `fetchSherpaLibs` Gradle task and checked against a pinned SHA-256.
- **Kokoro-82M** (Apache-2.0, https://huggingface.co/hexgrad/Kokoro-82M): the natural voice. Not in the APK: the app downloads the sherpa-onnx packaging (`kokoro-multi-lang-v1_0.tar.bz2`) on the owner's request, checks its pinned SHA-256 and unpacks it into private storage.
- **Apache Commons Compress** (Apache-2.0): unpacks that download.
