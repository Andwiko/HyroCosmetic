param(
    [string]$SourceDir = "$PSScriptRoot/resourcepack",
    [string]$ZipFile = "$PSScriptRoot/HyroCosmetics_Pack.zip"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $SourceDir)) {
    Write-Error "Source directory $SourceDir does not exist!"
}

if (Test-Path $ZipFile) {
    Remove-Item $ZipFile -Force
}

Write-Host "Creating Resource Pack zip from $SourceDir to $ZipFile..."

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$resolvedSource = (Resolve-Path $SourceDir).Path
$zipArchive = [System.IO.Compression.ZipFile]::Open($ZipFile, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $metaPath = Join-Path $resolvedSource "pack.mcmeta"
    if (Test-Path $metaPath) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, $metaPath, "pack.mcmeta", [System.IO.Compression.CompressionLevel]::Optimal)
    }
    $pngPath = Join-Path $resolvedSource "pack.png"
    if (Test-Path $pngPath) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, $pngPath, "pack.png", [System.IO.Compression.CompressionLevel]::Optimal)
    }
    $assetsPath = Join-Path $resolvedSource "assets"
    if (Test-Path $assetsPath) {
        Get-ChildItem -Path $assetsPath -Recurse -File | ForEach-Object {
            $rel = $_.FullName.Substring($resolvedSource.Length).TrimStart('\', '/') -replace '\\', '/'
            $null = [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, $_.FullName, $rel, [System.IO.Compression.CompressionLevel]::Optimal)
        }
    }
}
finally {
    $zipArchive.Dispose()
}

Write-Host "Done! Resource pack successfully created at: $ZipFile"
$file = Get-Item $ZipFile
Write-Host "Size: $([math]::Round($file.Length / 1KB, 2)) KB"

$sha1 = (Get-FileHash -Path $ZipFile -Algorithm SHA1).Hash.ToLower()
Write-Host "=========================================================="
Write-Host "SHA-1 Hash: $sha1"
Write-Host "Pasang di server.properties jika diupload ke hosting:"
Write-Host "resource-pack-sha1=$sha1"
Write-Host "=========================================================="
