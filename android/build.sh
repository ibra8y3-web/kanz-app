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
[ -f ks.jks ] || keytool -genkeypair -keystore ks.jks -alias k -keyalg RSA -keysize 2048 -validity 10000 -storepass ${KS_PASS:?set KS_PASS to your keystore password} -keypass ${KS_PASS:?set KS_PASS to your keystore password} -dname "CN=Almasdar Store"
$BT/apksigner sign --ks ks.jks --ks-pass pass:${KS_PASS:?set KS_PASS to your keystore password} --out almasdar-store.apk out/aligned.apk
$BT/apksigner verify almasdar-store.apk && ls -la almasdar-store.apk
