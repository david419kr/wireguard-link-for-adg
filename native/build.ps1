param([string]$Ndk = "$env:LOCALAPPDATA\Android\Sdk\ndk\28.2.13676358")
$ErrorActionPreference = 'Stop'
$toolchain = Join-Path $Ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin'
if (!(Test-Path -LiteralPath $toolchain)) { throw "NDK toolchain missing: $toolchain" }
Push-Location $PSScriptRoot
$saved = @{}
foreach ($name in @('GOOS','GOARCH','GOARM','CGO_ENABLED','CC','CGO_CFLAGS','CGO_LDFLAGS')) { $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
try {
 $env:GOOS='android';$env:CGO_ENABLED='1';$env:CGO_CFLAGS='-O2';$env:CGO_LDFLAGS='-Wl,-z,max-page-size=16384'
 $targets=@(
  @('arm64-v8a','arm64','aarch64-linux-android23-clang.cmd'),
  @('armeabi-v7a','arm','armv7a-linux-androideabi23-clang.cmd'),
  @('x86','386','i686-linux-android23-clang.cmd'),
  @('x86_64','amd64','x86_64-linux-android23-clang.cmd')
 )
 foreach ($target in $targets) {
  $env:GOARCH=$target[1];$env:GOARM='7';$env:CC=Join-Path $toolchain $target[2]
  $output=Join-Path $PSScriptRoot ('out\'+$target[0]);New-Item -ItemType Directory -Force $output | Out-Null
  & go build -trimpath -buildvcs=false -ldflags '-s -w -buildid=' -buildmode=c-shared -o (Join-Path $output 'libwgbridge.so') .
  if ($LASTEXITCODE -ne 0) { throw "Native build failed for $($target[0])" }
  Get-FileHash (Join-Path $output 'libwgbridge.so') -Algorithm SHA256
 }
} finally {
 foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
 Pop-Location
}
