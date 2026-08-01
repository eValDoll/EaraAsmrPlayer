param(
    [string]$DeviceSerial = "",
    [string]$GradleInstallTask = ":app:installRelease",
    [string[]]$GradleArguments = @()
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$adbPath = (Get-Command adb -ErrorAction Stop).Source

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $connectedDevices = @(
        & $adbPath devices |
            Select-Object -Skip 1 |
            ForEach-Object { ($_ -split "\s+")[0] } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    if ($connectedDevices.Count -ne 1) {
        throw "Exactly one Android device is required; found $($connectedDevices.Count)."
    }
    $DeviceSerial = $connectedDevices[0]
}

$originalStayAwake = ((& $adbPath -s $DeviceSerial shell settings get global stay_on_while_plugged_in 2>$null) -join "").Trim()
& $adbPath -s $DeviceSerial shell settings put global stay_on_while_plugged_in 7 2>$null | Out-Null
& $adbPath -s $DeviceSerial shell input keyevent KEYCODE_WAKEUP 2>$null | Out-Null
& $adbPath -s $DeviceSerial shell wm dismiss-keyguard 2>$null | Out-Null

$confirmJob = Start-Job -ScriptBlock {
    param($Adb, $Serial)

    $installerPackagePattern = "com\.miui\.(securitycenter|packageinstaller)|com\.(google\.)?android\.(packageinstaller|permissioncontroller)|com\.android\.settings"
    # Windows PowerShell 5 interprets UTF-8-without-BOM scripts with the system code page.
    # Build the installer labels from code points so this project can keep its no-BOM rule.
    $confirmLabels = @(
        (-join @([char]0x786E, [char]0x8BA4)),
        (-join @([char]0x786E, [char]0x8BA4, [char]0x5B89, [char]0x88C5)),
        (-join @([char]0x7EE7, [char]0x7EED, [char]0x5B89, [char]0x88C5)),
        (-join @([char]0x4ECD, [char]0x7136, [char]0x5B89, [char]0x88C5)),
        (-join @([char]0x5B89, [char]0x88C5)),
        (-join @([char]0x5141, [char]0x8BB8))
    )
    $remoteDumpPath = "/sdcard/eara_install_window.xml"
    $physicalSize = (& $Adb -s $Serial shell wm size 2>$null) -join "`n"
    $displayWidth = 0
    $displayHeight = 0
    if ($physicalSize -match "Physical size:\s*(\d+)x(\d+)") {
        $displayWidth = [int]$Matches[1]
        $displayHeight = [int]$Matches[2]
    }
    $miuiInstallConfirmationSent = $false

    while ($true) {
        # Android 16 no longer includes mCurrentFocus in `dumpsys window windows`.
        # Query the window service root so the short MIUI ADB-install countdown is detected.
        $focus = (& $Adb -s $Serial shell dumpsys window 2>$null) -join "`n"
        if ($focus -notmatch $installerPackagePattern) {
            $miuiInstallConfirmationSent = $false
            Start-Sleep -Milliseconds 180
            continue
        }

        if (
            $focus -match "com\.miui\.securitycenter/.+AdbInstallActivity" -and
            $displayWidth -gt 0 -and
            $displayHeight -gt 0
        ) {
            if (-not $miuiInstallConfirmationSent) {
                $x = [int]($displayWidth * 0.288)
                $y = [int]($displayHeight * 0.930)
                & $Adb -s $Serial shell input tap $x $y 2>$null | Out-Null
                Write-Output "Auto-confirmed MIUI ADB installer at ($x,$y)."
                $miuiInstallConfirmationSent = $true
            }
            Start-Sleep -Milliseconds 350
            continue
        }

        & $Adb -s $Serial shell uiautomator dump --compressed $remoteDumpPath 2>$null | Out-Null
        $xmlText = (& $Adb -s $Serial shell cat $remoteDumpPath 2>$null) -join ""
        if ([string]::IsNullOrWhiteSpace($xmlText)) {
            Start-Sleep -Milliseconds 180
            continue
        }

        try {
            [xml]$window = $xmlText
        } catch {
            Start-Sleep -Milliseconds 180
            continue
        }

        $target = $null
        foreach ($label in $confirmLabels) {
            $escapedLabel = $label.Replace("'", "&apos;")
            $target = $window.SelectSingleNode(
                "//node[contains(@text,'$escapedLabel') or contains(@content-desc,'$escapedLabel')]"
            )
            if ($null -ne $target) { break }
        }
        if ($null -eq $target) {
            Start-Sleep -Milliseconds 180
            continue
        }

        $targetPackage = [string]$target.package
        if ($targetPackage -notmatch $installerPackagePattern) {
            Write-Output "Ignored confirmation label from non-installer package: $targetPackage"
            Start-Sleep -Milliseconds 180
            continue
        }

        $clickTarget = $target
        while ($null -ne $clickTarget -and $clickTarget.clickable -ne "true") {
            $clickTarget = $clickTarget.ParentNode
        }
        if ($null -eq $clickTarget -or $clickTarget.Name -ne "node") {
            $clickTarget = $target
        }

        $bounds = [string]$clickTarget.bounds
        if ($bounds -match "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
            $x = [int]( ([int]$Matches[1] + [int]$Matches[3]) / 2 )
            $y = [int]( ([int]$Matches[2] + [int]$Matches[4]) / 2 )
            & $Adb -s $Serial shell input tap $x $y 2>$null | Out-Null
            Write-Output "Auto-confirmed installer button at ($x,$y)."
            Start-Sleep -Milliseconds 350
        }
    }
} -ArgumentList $adbPath, $DeviceSerial

try {
    Push-Location $projectRoot
    try {
        & ".\gradlew-local.bat" $GradleInstallTask @GradleArguments --console=plain
        $gradleExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    Stop-Job $confirmJob -ErrorAction SilentlyContinue
    Receive-Job $confirmJob -ErrorAction SilentlyContinue
    Remove-Job $confirmJob -Force -ErrorAction SilentlyContinue
    if ($originalStayAwake -match "^\d+$") {
        & $adbPath -s $DeviceSerial shell settings put global stay_on_while_plugged_in $originalStayAwake 2>$null | Out-Null
    }
}

if ($gradleExitCode -ne 0) {
    exit $gradleExitCode
}
