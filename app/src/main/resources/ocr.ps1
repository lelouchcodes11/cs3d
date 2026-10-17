param([string]$Image, [string]$Langs)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    [void][Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Graphics.Imaging.BitmapDecoder, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Graphics.Imaging.SoftwareBitmap, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Storage.StorageFile, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Storage.Streams.IRandomAccessStream, Windows.Foundation, ContentType = WindowsRuntime]
    [void][Windows.Globalization.Language, Windows.Foundation, ContentType = WindowsRuntime]
    $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
    function Await($op, [Type]$type) {
        $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
        [void]$t.Wait(-1)
        $t.Result
    }
    $engine = $null
    $used = $null
    if ($Langs -eq 'profile') {
        $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
        if ($engine) { $used = $engine.RecognizerLanguage.LanguageTag }
    }
    foreach ($tag in $(if ($engine) { @() } else { $Langs.Split(',') })) {
        try {
            $lang = New-Object Windows.Globalization.Language($tag)
            if ([Windows.Media.Ocr.OcrEngine]::IsLanguageSupported($lang)) {
                $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($lang)
                if ($engine) { $used = $tag; break }
            }
        } catch { }
    }
    if (-not $engine) {
        $avail = ([Windows.Media.Ocr.OcrEngine]::AvailableRecognizerLanguages | ForEach-Object { $_.LanguageTag }) -join ','
        Write-Output (@{ error = 'no-engine'; wanted = $Langs; available = $avail } | ConvertTo-Json -Compress)
        exit 0
    }
    $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($Image)) ([Windows.Storage.StorageFile])
    $stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
    $decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
    $max = [double][Windows.Media.Ocr.OcrEngine]::MaxImageDimension
    $w = [double]$decoder.PixelWidth
    $h = [double]$decoder.PixelHeight
    if ($w -gt $max -or $h -gt $max) {
        $k = [Math]::Min($max / $w, $max / $h)
        $transform = New-Object Windows.Graphics.Imaging.BitmapTransform
        $transform.ScaledWidth = [uint32][Math]::Floor($w * $k)
        $transform.ScaledHeight = [uint32][Math]::Floor($h * $k)
        $bmp = Await ($decoder.GetSoftwareBitmapAsync($decoder.BitmapPixelFormat, $decoder.BitmapAlphaMode, $transform,
            [Windows.Graphics.Imaging.ExifOrientationMode]::IgnoreExifOrientation, [Windows.Graphics.Imaging.ColorManagementMode]::DoNotColorManage)) ([Windows.Graphics.Imaging.SoftwareBitmap])
    } else {
        $bmp = Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
    }
    $result = Await ($engine.RecognizeAsync($bmp)) ([Windows.Media.Ocr.OcrResult])
    $lines = @(foreach ($l in $result.Lines) {
        $x1 = [double]::MaxValue; $y1 = [double]::MaxValue; $x2 = 0.0; $y2 = 0.0
        foreach ($wd in $l.Words) {
            $r = $wd.BoundingRect
            if ($r.X -lt $x1) { $x1 = $r.X }
            if ($r.Y -lt $y1) { $y1 = $r.Y }
            if (($r.X + $r.Width) -gt $x2) { $x2 = $r.X + $r.Width }
            if (($r.Y + $r.Height) -gt $y2) { $y2 = $r.Y + $r.Height }
        }
        @{ t = $l.Text; x = [Math]::Round($x1, 1); y = [Math]::Round($y1, 1); w = [Math]::Round($x2 - $x1, 1); h = [Math]::Round($y2 - $y1, 1) }
    })
    Write-Output (@{ lang = $used; w = $bmp.PixelWidth; h = $bmp.PixelHeight; lines = $lines } | ConvertTo-Json -Compress -Depth 4)
} catch {
    Write-Output (@{ error = $_.Exception.Message } | ConvertTo-Json -Compress)
}
