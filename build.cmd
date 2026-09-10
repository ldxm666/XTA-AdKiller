@echo off
setlocal
set SDK=C:\Users\Administrator\AppData\Local\Android\Sdk
set BT=%SDK%\build-tools\36.1.0
set PLAT=%SDK%\platforms\android-34\android.jar
set ROOT=%~dp0
set OUT=%ROOT%build
set APK=%OUT%\xtakiller.apk

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\classes" 2>nul

echo [1/6] javac...
javac -encoding UTF-8 -source 11 -target 11 -Xlint:-options ^
  -cp "%PLAT%;%ROOT%libs\libxposed-api-102.jar" ^
  -d "%OUT%\classes" ^
  "%ROOT%java\io\github\ldxm666\xtaadkiller\ModuleEntry.java" || exit /b 1

echo [2/6] d8...
jar cf "%OUT%\classes.jar" -C "%OUT%\classes" . || exit /b 1
call "%BT%\d8.bat" --release --lib "%PLAT%" --min-api 29 ^
  --output "%OUT%" "%OUT%\classes.jar" || exit /b 1

echo [3/6] aapt2 compile+link...
"%BT%\aapt2.exe" compile --dir "%ROOT%res" -o "%OUT%\res.zip" || exit /b 1
"%BT%\aapt2.exe" link -o "%OUT%\base.apk" -I "%PLAT%" ^
  --manifest "%ROOT%AndroidManifest.xml" "%OUT%\res.zip" || exit /b 1

echo [4/6] inject dex + xposed meta...
python "%ROOT%pack.py" "%OUT%\base.apk" "%OUT%\classes.dex" "%ROOT%resources" || exit /b 1

echo [5/6] zipalign...
"%BT%\zipalign.exe" -f 4 "%OUT%\base.apk" "%OUT%\aligned.apk" || exit /b 1

echo [6/6] sign...
if not exist "%ROOT%debug.keystore" (
  keytool -genkeypair -v -keystore "%ROOT%debug.keystore" -storepass android ^
    -keypass android -alias dshdebug -keyalg RSA -keysize 2048 -validity 10950 ^
    -dname "CN=DSH,O=DSH,C=CN" || exit /b 1
)
"%BT%\apksigner.bat" sign --ks "%ROOT%debug.keystore" --ks-pass pass:android ^
  --key-pass pass:android --out "%APK%" "%OUT%\aligned.apk" || exit /b 1
"%BT%\apksigner.bat" verify --print-certs "%APK%"
echo BUILD OK: %APK%
endlocal
