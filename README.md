# Blind Assist AI

An experimental offline Android visual assistant. Camera frames stay on the phone; a bundled Ultralytics COCO detector runs through ONNX Runtime Android and presents boxes with brief, cooldown-limited speech alerts. The initial model is YOLO11n because it has a stable ONNX export path in the pinned Ultralytics release. A class list and verified tensor contract are recorded alongside the model after export.

## Safety and privacy

This is an experimental assistive tool, not a mobility aid. Keep using a white cane, guide dog, and mobility training. Object detection cannot tell reliably whether an object is near, whether a route is clear, or whether it is safe to pass. This app does not estimate distance, give turn instructions, upload camera data, record video, require an account, or request Internet permission. Inference runs locally after model installation.

## Project and data flow

CameraX emits latest-only RGBA frames on one background executor. The app accounts for row and pixel stride, rotates each frame using CameraX metadata, letterboxes RGB input to 640×640, runs the bundled ONNX model, decodes the output tensor, filters confidence, applies class-aware NMS, maps boxes back to normalized preview coordinates, and sends concise alerts through Android TextToSpeech. The app validates model input dimensions at load time and displays actionable model errors.

## Requirements

- Windows 10/11, PowerShell 7 or Windows PowerShell 5.1, VS Code (Android Studio is not required).
- JDK 17, Python 3.10 or later, Android SDK command-line tools, SDK platform 35, build-tools 35.0.0, and platform-tools/ADB.
- Gradle Wrapper (Gradle 8.9 through the checked-in wrapper; Android Gradle Plugin 8.7.3).
- Model export pins: Ultralytics 8.3.40, ONNX 1.17.0, ONNX Runtime 1.20.1, NumPy 1.26.4, Pillow 11.0.0, ONNX Script 0.5.6, ONNX IR 0.1.12.

## Command-line setup

Install JDK 17 and the Android command-line tools from the official Android developer site. In SDK Manager, install the packages below, accepting licenses:

```powershell
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Set `JAVA_HOME`, `ANDROID_SDK_ROOT`, and `PATH` for the current shell or through Windows Environment Variables. Typical SDK path is `%LOCALAPPDATA%\Android\Sdk`; no environment values are changed by project scripts. Check `java -version`, `adb version`, and `sdkmanager --list`. Run the setup check with `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup.ps1`.

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements-export.txt
```

## Export and validate the pretrained model

The first export downloads official pretrained `yolo11n.pt` weights through Ultralytics. This needs network access once. Do not create a placeholder asset. The export checks ONNX integrity, input contract, output metadata, performs inference using a generated sample image, copies the ONNX file to app assets, and writes the actual tensor contract to `app/src/main/assets/model_manifest.json`.

```powershell
.\.venv\Scripts\python.exe scripts\export_model.py
.\.venv\Scripts\python.exe scripts\validate_model.py
```

Generated `yolo.onnx` is intentionally not committed to source control; run the export before building. The export script enforces a `[1,3,640,640]` float RGB NCHW input and saves actual output names/shapes. The Android decoder supports the exported non-NMS YOLO detection layout. If the exporter changes that contract, export validation or runtime initialization fails visibly instead of silently claiming detections.

## Tests

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s scripts -p "test_*.py"
python scripts\validate_model.py
.\gradlew.bat testDebugUnitTest lintDebug
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test_all.ps1
```

Python unit tests cover preprocessing, tensor decoding, confidence filtering, invalid output, and coordinate transforms. Kotlin unit tests cover relative position, alert generation, NMS, and cooldown. Instrumentation launch test is included but requires an emulator/device. Camera, speech and hardware behavior are not asserted by host unit tests.

## Build and install

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build_apk.ps1
# equivalent basic Gradle build after setup/model export:
.\gradlew.bat assembleDebug
adb install -r .\output\BlindAssistAI-debug.apk
adb shell am start -n org.blindassist.ai/.MainActivity
```

The script runs prerequisite checks, model validation, tests, lint and debug assembly, verifies APK package metadata with `aapt`, and copies the APK to `output/BlindAssistAI-debug.apk`. Release builds require a user-owned signing key and explicit Gradle signing configuration; this project does not call an unsigned APK distribution-ready.

## Troubleshooting

- **Java not found:** install JDK 17, set `JAVA_HOME`, reopen PowerShell, verify `java -version`.
- **SDK/platform missing:** set `ANDROID_SDK_ROOT`; use `sdkmanager` to install platform 35, build-tools 35.0.0, and platform-tools.
- **No wrapper:** the wrapper JAR and scripts must be present; regenerate with Gradle 8.9 (`gradle wrapper --gradle-version 8.9`) if the distribution is available.
- **Model export/network error:** activate `.venv`, rerun pip install and export with working network. The app cannot build without the actual model asset; never substitute an empty file.
- **ONNX load error:** inspect `model_manifest.json`; confirm input is float `[1,3,640,640]` and output is YOLO detection format. Re-export with the pinned toolchain.
- **Camera denied:** grant Camera permission in Android app settings and reopen the app. No frame is sent off-device.
- **No connected device:** enable Developer Options and USB debugging, authorize the host, then verify `adb devices`. Instrumentation tests require an available Android device or emulator.
- **Speech unavailable:** check Android TTS engine/language settings and ensure the Spoken alerts switch is on.

## Current execution report

The exported pretrained YOLO11n model is present and validated. Python unit tests (4/4) and model validation pass, including inference on the bundled reference image (46 decoded detections). Java, Android SDK/ADB, and a connected Android device are unavailable in this workspace, so Gradle compilation, Android unit/instrumentation tests, APK generation, installation, and launch have not been run. Install the documented JDK and Android SDK prerequisites, then run the build command above.
