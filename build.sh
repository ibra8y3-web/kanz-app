set -e
export JAVA_HOME=$PWD/jdk-17.0.21.1+2 PATH=$PWD/jdk-17.0.21.1+2/bin:$PATH
BT=$PWD/sdk/build-tools/34.0.0; AJ=$PWD/sdk/platforms/android-34/android.jar
rm -rf out && mkdir -p out/res out/cls
$BT/aapt2 compile --dir app/res -o out/res.zip
$BT/aapt2 link -o out/base.apk -I $AJ --manifest app/AndroidManifest.xml --min-sdk-version 24 --target-sdk-version 34 out/res.zip --java out/gen
javac -encoding UTF-8 -nowarn -source 8 -target 8 -cp $AJ -d out/cls $(find app/src out/gen -name '*.java')
$BT/d8 --min-api 24 --lib $AJ --output out $(find out/cls -name '*.class')
cp out/base.apk out/unsigned.apk
(cd out && zip -q unsigned.apk classes.dex && cd ../app && zip -q -r ../out/unsigned.apk assets)
$BT/zipalign -f 4 out/unsigned.apk out/aligned.apk
# Signing: set KANZ_KS (keystore path, default ks.jks) and KANZ_KS_PASS (password) in your shell. Nothing secret is stored in this repo.
KS=${KANZ_KS:-ks.jks}
[ -n "$KANZ_KS_PASS" ] || { read -r -s -p "Keystore password: " KANZ_KS_PASS; echo; export KANZ_KS_PASS; }
if [ ! -f "$KS" ]; then echo "No keystore at $KS, creating a new one (NEW KEY: it will not update apps signed with another key)"; keytool -genkeypair -keystore "$KS" -alias k -keyalg RSA -keysize 2048 -validity 10000 -storepass:env KANZ_KS_PASS -keypass:env KANZ_KS_PASS -dname "CN=Kanz Store"; fi
$BT/apksigner sign --ks "$KS" --ks-pass env:KANZ_KS_PASS --out almasdar-store.apk out/aligned.apk
$BT/apksigner verify almasdar-store.apk && ls -la almasdar-store.apk
