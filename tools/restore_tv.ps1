param(
    [string]$Serial,
    [string]$ExpectedModel = 'MiTV4-ANSM0',
    [switch]$EnableNewLauncher
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Pass an explicit -Serial (ADB serial or TV_IP:PORT)' }
$model = (& adb -s $Serial shell getprop ro.product.model).Trim()
if ($LASTEXITCODE -ne 0 -or $model -ne $ExpectedModel) {
    throw "Device identity mismatch: $Serial / $model"
}
if ($EnableNewLauncher) {
    & adb -s $Serial shell am start -n local.mitv.launcher/.MainActivity
} else {
    # This firmware denies shell pm disable-user. The app disables only its own bridge.
    $restoreResult = & adb -s $Serial shell am broadcast -a local.mitv.launcher.RESTORE_FACTORY -n local.mitv.launcher/.RestoreReceiver
    $restoreResult | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($restoreResult -join ' ') -notmatch 'result=1') {
        throw 'The recovery receiver did not confirm restoration'
    }
    $serviceRead = & adb -s $Serial shell settings get secure enabled_accessibility_services
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read existing services; leaving the list unchanged' }
    $existingServices = ($serviceRead -join '').Trim()
    if ($existingServices -ne 'null' -and $existingServices -notmatch '^[A-Za-z0-9._$]+/[A-Za-z0-9._$]+(:[A-Za-z0-9._$]+/[A-Za-z0-9._$]+)*$') {
        throw 'Unexpected service list; leaving it unchanged'
    }
    $remainingServices = @($existingServices -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -notlike 'local.mitv.launcher/*' })
    if ($remainingServices.Count -gt 0) {
        & adb -s $Serial shell settings put secure enabled_accessibility_services ($remainingServices -join ':')
        if ($LASTEXITCODE -ne 0) { throw 'Cannot remove the custom service entry' }
        $expectedServices = $remainingServices -join ':'
    } else {
        & adb -s $Serial shell settings delete secure enabled_accessibility_services
        if ($LASTEXITCODE -ne 0) { throw 'Cannot remove the custom service entry' }
        & adb -s $Serial shell settings put secure accessibility_enabled 0
        if ($LASTEXITCODE -ne 0) { throw 'Cannot disable the empty accessibility service list' }
        $expectedServices = 'null'
    }
    $readBack = & adb -s $Serial shell settings get secure enabled_accessibility_services
    if ($LASTEXITCODE -ne 0 -or ($readBack -join '').Trim() -ne $expectedServices) {
        throw 'Service list verification failed; check the TV state'
    }
    & adb -s $Serial shell appops set local.mitv.launcher GET_USAGE_STATS default
    if ($LASTEXITCODE -ne 0) { throw 'Cannot reset foreground fallback access' }
}
if ($LASTEXITCODE -ne 0) { throw 'Cannot start selected launcher' }
