# LibreOfficeKit Native Binaries Directory

This directory is designated for precompiled LibreOfficeKit shared libraries (`.so`).

### Supported ABIs:
- `arm64-v8a/` (64-bit ARM devices - standard for modern phones)
- `armeabi-v7a/` (32-bit ARM legacy devices)
- `x86_64/` (64-bit Emulators & ChromeOS)

### Required Native Libraries per ABI:
- `liblo-native-code.so` (or `libsofficeapp.so`)
- Supporting dependencies (ICU, fontconfig, harfbuzz, uno, boost if unmerged)

When placed here, Gradle automatically bundles them into the APK, and `LOKitBridge` dynamically resolves them via JNI `dlopen` / `dlsym`.
