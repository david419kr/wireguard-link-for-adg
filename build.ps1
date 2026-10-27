param(
    [string]$Sdk,
    [string]$Ndk,
    [string]$Keystore,
    [string]$PasswordFile,
    [string]$KeyAlias = 'release',
    [switch]$SkipNative,
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
function Check($message) { if ($LASTEXITCODE -ne 0) { throw $message } }
if (-not $Sdk) {
    $Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}
if (-not $Ndk) { $Ndk = Join-Path $Sdk 'ndk/28.2.13676358' }
$bt = Join-Path $Sdk 'build-tools/36.1.0'
$android = Join-Path $Sdk 'platforms/android-35/android.jar'
if (-not (Test-Path -LiteralPath $android)) { throw 'Android platform 35 is required; see docs/BUILD.md' }
if (-not (Test-Path -LiteralPath (Join-Path $bt 'aapt2.exe'))) { throw 'Android build-tools 36.1.0 are required' }
if ($Keystore) {
    $Keystore = (Resolve-Path -LiteralPath $Keystore).Path
    $PasswordFile = (Resolve-Path -LiteralPath $PasswordFile).Path
}
$javaBin = Split-Path (Get-Command javac).Source
python -X utf8 tools/check_ui_translations.py
Check 'UI translation audit failed'
python -X utf8 tools/fetch_dependencies.py
Check 'Dependency verification failed'
if (-not $SkipNative) { & native/build.ps1 -Ndk $Ndk; Check 'Native build failed' }
New-Item -ItemType Directory -Force build/classes,build/dex,artifacts | Out-Null
$generated = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build/classes'))
$boundary = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build')) + [IO.Path]::DirectorySeparatorChar
if (-not $generated.StartsWith($boundary,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected generated output path' }
Remove-Item -LiteralPath $generated -Recurse -Force
New-Item -ItemType Directory -Force $generated | Out-Null
& (Join-Path $bt 'aapt2.exe') compile --dir app/res -o build/resources.zip
Check 'Resource compilation failed'
& (Join-Path $bt 'aapt2.exe') link -I $android --manifest app/AndroidManifest.xml --java build/generated --min-sdk-version 28 --target-sdk-version 35 -o build/resources.apk build/resources.zip
Check 'Resource linking failed'
$classpath = "$android;build/dependencies/wireguard-config.jar;build/dependencies/core-3.5.4.jar"
$sources = Get-ChildItem app/src,build/generated -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& (Join-Path $javaBin 'javac.exe') -encoding UTF-8 -source 8 -target 8 -cp $classpath -d build/classes @sources
Check 'Java compilation failed'
& (Join-Path $javaBin 'jar.exe') cf build/app.jar -C build/classes .
Check 'JAR creation failed'
python -X utf8 tools/package_payload.py prepare
Check 'Configuration library preparation failed'
& java -Xmx768m -cp (Join-Path $bt 'lib/d8.jar') com.android.tools.r8.D8 --min-api 28 --lib $android --output build/dex build/app.jar build/wireguard-config-only.jar build/dependencies/core-3.5.4.jar
Check 'DEX compilation failed'
if (-not $SkipTests) {
    New-Item -ItemType Directory -Force build/test | Out-Null
    $tests = Get-ChildItem app/test -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
    & (Join-Path $javaBin 'javac.exe') -encoding UTF-8 -cp "$classpath;build/classes" -d build/test @tests
    Check 'Test compilation failed'
    foreach ($test in @('ProfileExportTest','IntegrationStateTest','ConnectionVisualsTest','LegacyShortcutContractTest','UiTextTest','ProfileEditTest')) {
        & java -cp "build/test;build/classes;$classpath" "com.adguard.vpn.wireguard.$test"
        Check "Test failed: $test"
    }
}
python -X utf8 tools/package_payload.py assemble
Check 'APK assembly failed'
& (Join-Path $bt 'zipalign.exe') -f -p 4 build/unsigned.apk build/aligned.apk
Check 'ZIP alignment failed'
$version = ([xml](Get-Content -LiteralPath app/AndroidManifest.xml -Raw)).manifest.versionName
$output = Join-Path $PSScriptRoot "artifacts/wireguard-link-for-adg-$version.apk"
if ($Keystore) {
    & (Join-Path $bt 'apksigner.bat') sign --ks $Keystore --ks-type PKCS12 --ks-key-alias $KeyAlias --ks-pass "file:$PasswordFile" --alignment-preserved true --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled false --v4-signing-enabled false --out $output build/aligned.apk
    Check 'APK signing failed'
    & (Join-Path $bt 'apksigner.bat') verify --verbose --print-certs $output > build/signature.txt
    Check 'Signature verification failed'
} else {
    $output = Join-Path $PSScriptRoot "artifacts/wireguard-link-for-adg-$version-unsigned.apk"
    Copy-Item -LiteralPath build/aligned.apk -Destination $output -Force
}
& (Join-Path $bt 'zipalign.exe') -c 4 $output
Check 'Final alignment verification failed'
python -X utf8 tools/package_payload.py verify $output
Check 'Payload verification failed'
Get-FileHash -LiteralPath $output -Algorithm SHA256
