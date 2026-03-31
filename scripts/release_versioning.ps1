function Read-ReleaseVersionProperties {
    param([string]$VersionStatePath)

    if (-not (Test-Path $VersionStatePath)) {
        throw "Missing release version state file at $VersionStatePath"
    }

    $properties = [ordered]@{}
    foreach ($line in Get-Content $VersionStatePath) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }

        $parts = $trimmed -split '=', 2
        if ($parts.Count -ne 2) {
            continue
        }

        $properties[$parts[0].Trim()] = $parts[1].Trim()
    }

    foreach ($requiredKey in @("VERSION_NAME", "VERSION_CODE", "MINIMUM_ALLOWED_VERSION_CODE")) {
        if (-not $properties.Contains($requiredKey)) {
            throw "Missing $requiredKey in $VersionStatePath"
        }
    }

    return $properties
}

function Get-NextPatchVersionName {
    param([string]$VersionName)

    $parts = $VersionName.Split(".")
    if ($parts.Count -lt 3) {
        throw "VERSION_NAME must use at least three numeric segments, e.g. 1.0.12"
    }

    $lastSegment = 0
    if (-not [int]::TryParse($parts[$parts.Count - 1], [ref]$lastSegment)) {
        throw "VERSION_NAME patch segment must be numeric: $VersionName"
    }

    $parts[$parts.Count - 1] = ($lastSegment + 1).ToString()
    return ($parts -join ".")
}

function Resolve-ReleaseVersionSelection {
    param([string]$VersionStatePath)

    $properties = Read-ReleaseVersionProperties -VersionStatePath $VersionStatePath
    $storedVersionName = $properties["VERSION_NAME"]
    $storedVersionCode = [int]$properties["VERSION_CODE"]
    $minimumAllowedVersionCode = [int]$properties["MINIMUM_ALLOWED_VERSION_CODE"]

    if ($storedVersionCode -le $minimumAllowedVersionCode) {
        throw "Stored VERSION_CODE $storedVersionCode must be greater than MINIMUM_ALLOWED_VERSION_CODE $minimumAllowedVersionCode"
    }

    $resolvedVersionName = if ($env:VERSION_NAME) {
        $env:VERSION_NAME
    } else {
        Get-NextPatchVersionName -VersionName $storedVersionName
    }

    if ($env:VERSION_CODE) {
        $manualVersionCode = 0
        if (-not [int]::TryParse($env:VERSION_CODE, [ref]$manualVersionCode)) {
            throw "VERSION_CODE must be an integer"
        }
        $resolvedVersionCode = $manualVersionCode
    } else {
        $resolvedVersionCode = $storedVersionCode + 1
    }

    if ($resolvedVersionCode -le $storedVersionCode) {
        throw "Resolved VERSION_CODE $resolvedVersionCode must be greater than stored VERSION_CODE $storedVersionCode"
    }
    if ($resolvedVersionCode -le $minimumAllowedVersionCode) {
        throw "Resolved VERSION_CODE $resolvedVersionCode must be greater than MINIMUM_ALLOWED_VERSION_CODE $minimumAllowedVersionCode"
    }

    return [pscustomobject]@{
        StoredVersionName = $storedVersionName
        StoredVersionCode = $storedVersionCode
        VersionName = $resolvedVersionName
        VersionCode = $resolvedVersionCode
    }
}

function Save-ReleaseVersionProperties {
    param(
        [string]$VersionStatePath,
        [string]$VersionName,
        [int]$VersionCode
    )

    $properties = Read-ReleaseVersionProperties -VersionStatePath $VersionStatePath
    $scheme = if ($properties.Contains("SCHEME")) {
        $properties["SCHEME"]
    } else {
        "sequential-semver-patch"
    }
    $minimumAllowedVersionCode = [int]$properties["MINIMUM_ALLOWED_VERSION_CODE"]

    if ($VersionCode -le $minimumAllowedVersionCode) {
        throw "Persisted VERSION_CODE $VersionCode must be greater than MINIMUM_ALLOWED_VERSION_CODE $minimumAllowedVersionCode"
    }

    $lines = @(
        "# Sequential Play release versioning state.",
        "# Stored VERSION_CODE values must stay above the previous date-based ceiling.",
        "SCHEME=$scheme",
        "VERSION_NAME=$VersionName",
        "VERSION_CODE=$VersionCode",
        "MINIMUM_ALLOWED_VERSION_CODE=$minimumAllowedVersionCode",
        "LAST_PUBLISHED_AT=$([DateTimeOffset]::UtcNow.ToString('o'))"
    )

    Set-Content -Path $VersionStatePath -Value $lines
}
