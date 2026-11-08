# Generates assets/ferrum/icon.png: an original pixel-art iron ingot (drawn here, not Mojang's
# texture) above the word FERRUM. Run from the repo root:  powershell -File tools/make-icon.ps1
Add-Type -AssemblyName System.Drawing

$size = 128
$bmp = New-Object System.Drawing.Bitmap $size, $size
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.Clear([System.Drawing.Color]::Transparent)

# 16x10 ingot sprite, 6 px per cell, isometric-ish slab like a smelted bar.
# 0 = empty, 1 = outline, 2 = dark side, 3 = mid face, 4 = light face, 5 = highlight
$sprite = @(
  "0000011111111000",
  "0000144444444100",
  "0001455555544310",
  "0014444444443310",
  "0144444444433210",
  "1333333333332210",
  "1333333333322100",
  "1222222222221000",
  "1111111111110000",
  "0000000000000000"
)
$palette = @{
  '1' = [System.Drawing.Color]::FromArgb(255, 54, 54, 58)
  '2' = [System.Drawing.Color]::FromArgb(255, 110, 110, 116)
  '3' = [System.Drawing.Color]::FromArgb(255, 168, 168, 172)
  '4' = [System.Drawing.Color]::FromArgb(255, 214, 214, 216)
  '5' = [System.Drawing.Color]::FromArgb(255, 250, 250, 250)
}
$cell = 6
$offsetX = [int](($size - 16 * $cell) / 2)
$offsetY = 14
for ($y = 0; $y -lt $sprite.Count; $y++) {
  $row = $sprite[$y]
  for ($x = 0; $x -lt $row.Length; $x++) {
    $c = [string]$row[$x]
    if ($palette.ContainsKey($c)) {
      $brush = New-Object System.Drawing.SolidBrush $palette[$c]
      $g.FillRectangle($brush, $offsetX + $x * $cell, $offsetY + $y * $cell, $cell, $cell)
      $brush.Dispose()
    }
  }
}

# "FERRUM" in a 5x7 pixel font, 3 px per pixel, drawn by hand so no system font is required.
$font = @{
  'F' = @("11111","10000","10000","11110","10000","10000","10000")
  'E' = @("11111","10000","10000","11110","10000","10000","11111")
  'R' = @("11110","10001","10001","11110","10100","10010","10001")
  'U' = @("10001","10001","10001","10001","10001","10001","01110")
  'M' = @("10001","11011","10101","10101","10001","10001","10001")
}
$word = "FERRUM"
$px = 3
$glyphW = 5 * $px
$gap = 2 * $px
$textW = $word.Length * $glyphW + ($word.Length - 1) * $gap
$tx = [int](($size - $textW) / 2)
$ty = 88
$shadow = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 40, 40, 44))
$ink = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 226, 226, 228))
foreach ($ch in $word.ToCharArray()) {
  $glyph = $font[[string]$ch]
  for ($y = 0; $y -lt 7; $y++) {
    for ($x = 0; $x -lt 5; $x++) {
      if ($glyph[$y][$x] -eq '1') {
        $g.FillRectangle($shadow, $tx + $x * $px + $px, $ty + $y * $px + $px, $px, $px)
        $g.FillRectangle($ink, $tx + $x * $px, $ty + $y * $px, $px, $px)
      }
    }
  }
  $tx += $glyphW + $gap
}

$g.Dispose()
foreach ($out in @("src/main/resources/assets/ferrum/icon.png", "mc1.21.11/src/main/resources/assets/ferrum/icon.png")) {
  $bmp.Save((Join-Path (Get-Location) $out), [System.Drawing.Imaging.ImageFormat]::Png)
  Write-Output "wrote $out"
}
$bmp.Dispose()
