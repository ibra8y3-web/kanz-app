# Kanz (كنز) - open source

Open-source Android app store for FOSS apps. MIT licensed.

- android/  the Android app (Java, no Gradle): app/ sources + build.sh (needs Android SDK build-tools 34, platform android-34, JDK 17, and your own keystore; set KS_PASS)
- crawler/  the catalog crawler (Python 3, stdlib + vendored segno)
- tests/    offline test suite: python3 tests/test_offline.py must print ALL OFFLINE TESTS PASSED
- config.json, README-repo.md  catalog repo configuration and docs
- release/kanz.apk  the current signed app build, and version.txt its version

Automatic Releases: .github/workflows/release.yml publishes release/kanz.apk
to this repo's Releases whenever release/kanz.apk or version.txt changes on
the default branch. Upload once, the Release appears on its own. To publish a
new version, update release/kanz.apk and version.txt together.

No signing key or passwords are included. Generate your own keystore to build.
