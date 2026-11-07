# Builds the E.G.O and Furioso sounds (assets/projectminecraft/sounds/arsenal) from the same CC0 packs as Random Slop
# (Kenney "Sci-Fi Sounds" + "Impact Sounds", EZduzziteh "Squish Sounds") plus synthesized layers.
# Usage: tools\build_sounds.ps1 -Ffmpeg <path to ffmpeg.exe> -Src <folder with scifi/, impact/, squish/> [-Out <folder>] [-Slop <Random Slop sounds folder>]
param(
    [string]$Ffmpeg = "$env:LOCALAPPDATA\Temp\esff\x\ffmpeg-9.0.2-essentials_build\bin\ffmpeg.exe",
    [string]$Src = "_snd",
    [string]$Out = "src\main\resources\assets\projectminecraft\sounds",
    # Durandal and the weapon swap layer Random Slop's iai draw and dash whooshes
    [string]$Slop = "..\Effect-Slop\src\main\resources\assets\effectslop\sounds"
)
$ErrorActionPreference = "Stop"
$sci = "$Src\scifi\Audio"; $imp = "$Src\impact\Audio"; $sq = "$Src\squish"
$enc = @("-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5")
$tmp = "$env:TEMP\projectminecraft_snd_tmp"
New-Item -ItemType Directory -Force $tmp | Out-Null
New-Item -ItemType Directory -Force "$Out\arsenal" | Out-Null

function Run([string[]]$a) {
    & $Ffmpeg -hide_banner -loglevel error -y @a
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed: $($a -join ' ')" }
}
function Synth($graph, $dst) { Run (@("-f", "lavfi", "-i", $graph) + $enc + @("$Out\$dst.ogg")) }
function TmpSynth($graph, $name) { Run (@("-f", "lavfi", "-i", $graph) + $enc + @("$tmp\$name.ogg")); return "$tmp\$name.ogg" }
function MixN([string[]]$inputs, [string[]]$filters, $dst, $post = "anull") {
    $args2 = @(); $graph = ""; $labels = ""
    for ($i = 0; $i -lt $inputs.Count; $i++) {
        $args2 += @("-i", $inputs[$i])
        $graph += "[$($i):a]aformat=channel_layouts=mono,$($filters[$i])[s$i];"
        $labels += "[s$i]"
    }
    $graph += "$($labels)amix=inputs=$($inputs.Count):duration=longest:normalize=0,$post,alimiter=limit=0.95"
    Run ($args2 + @("-filter_complex", $graph) + $enc + @("$Out\$dst.ogg"))
}
$verb = "aecho=0.8:0.75:220|470|810|1300:0.45|0.32|0.22|0.14"
$room = "aecho=0.6:0.5:60|130:0.3|0.15"

# ---- shared layers
$crack = TmpSynth "aevalsrc='(random(0)*2-1)*exp(-55*t)+0.7*sin(2*PI*140*t)*exp(-14*t)+0.3*sin(2*PI*2200*t)*exp(-30*t)':d=0.9" "crack"
$boom = TmpSynth "aevalsrc='(random(0)*2-1)*exp(-35*t)+0.9*sin(2*PI*72*t)*exp(-7*t)+0.25*sin(2*PI*1400*t)*exp(-25*t)':d=1.5" "boom"

# ---- E.G.O: the cut-in, coins, and each skill
$hit = TmpSynth "aevalsrc='0.9*sin(2*PI*50*t)*exp(-5*t)+0.5*(random(0)*2-1)*exp(-30*t)':d=1.5" "egohit"
$swell = TmpSynth "aevalsrc='0.25*(sin(2*PI*220*t)+sin(2*PI*277.2*t)+sin(2*PI*329.6*t))*min(1,t*2)':d=0.9,afade=t=out:st=0.7:d=0.2" "egoswell"
MixN @($swell, $hit, "$imp\impactGlass_heavy_002.ogg") @("volume=1.0", "adelay=850,volume=1.5", "adelay=860,asetrate=44100*0.7,aresample=44100,volume=0.9") "arsenal/ego_activate" "$verb"
Synth "aevalsrc='0.55*sin(2*PI*1760*t)*exp(-7*t)+0.4*sin(2*PI*2637*t)*exp(-9*t)+0.3*sin(2*PI*3520*t)*exp(-12*t)':d=0.9,aecho=0.6:0.5:60:0.3,volume=2.2,alimiter=limit=0.9" "arsenal/coin_heads"
Synth "aevalsrc='0.6*sin(2*PI*330*t)*exp(-14*t)+0.4*(random(0)*2-1)*exp(-50*t)':d=0.5,lowpass=f=1500,volume=2.2,alimiter=limit=0.9" "arsenal/coin_tails"
MixN @($crack, "$imp\impactBell_heavy_002.ogg") @("volume=1.3", "asetrate=44100*1.5,aresample=44100,volume=0.5") "arsenal/solemn_shot" "aecho=0.7:0.6:180|380:0.3|0.18"
Synth "anoisesrc=d=2.5:c=pink:a=1:seed=141,bandpass=f=2200:width_type=o:w=2,tremolo=f=22:d=0.9,afade=t=in:d=0.3,afade=t=out:st=1.5:d=1,volume=4,alimiter=limit=0.9" "arsenal/butterflies"
Synth "aevalsrc='0.35*sin(2*PI*1046*t)*exp(-2*t)+0.3*sin(2*PI*1568*t)*exp(-2.5*t)+0.2*sin(2*PI*2093*t)*exp(-3*t)':d=1.4,$verb,volume=1.8,alimiter=limit=0.9" "arsenal/magic_circle"
$dark = TmpSynth "anoisesrc=d=3:c=brown:a=1:seed=142,lowpass=f=500,afade=t=out:st=0.2:d=2.6,volume=4" "dark"
MixN @($boom, "$sci\laserLarge_004.ogg", $dark) @("volume=1.6", "asetrate=44100*0.6,aresample=44100,volume=1.0", "volume=1.0") "arsenal/magic_bullet" "apad=pad_dur=1,$verb,atrim=0:4,afade=t=out:st=2.8:d=1.2"
Synth "anoisesrc=d=0.45:c=pink:a=1:seed=151,bandpass=f=1600:width_type=o:w=2,afade=t=in:d=0.02,afade=t=out:st=0.08:d=0.35,volume=4,alimiter=limit=0.9" "arsenal/match_stroke"
MixN @("$sci\thrusterFire_003.ogg", "$sci\explosionCrunch_002.ogg", "$sci\lowFrequency_explosion_001.ogg") @("atrim=0:2.5,afade=t=out:st=1.6:d=0.9,volume=1.5", "asetrate=44100*0.8,aresample=44100,volume=1.2", "volume=1.0") "arsenal/match_ignite" "$verb"

# ---- Furioso: the title sting, slow motion, every weapon's hit, Durandal and the infinite rotation
$brass = TmpSynth "aevalsrc='(2*(t*98-floor(0.5+t*98))+2*(t*146.8-floor(0.5+t*146.8))+2*(t*196-floor(0.5+t*196))+2*(t*233-floor(0.5+t*233)))*0.12*min(1,t*6)*exp(-0.9*t)':d=2.4,lowpass=f=2400" "brass"
$whoosh = TmpSynth "anoisesrc=d=1:c=pink:a=1:seed=88,bandpass=f=1500:width_type=o:w=3,afade=t=in:d=0.25,afade=t=out:st=0.3:d=0.7,volume=4" "whoosh"
$ring = TmpSynth "aevalsrc='0.5*sin(2*PI*3136*t)*exp(-2.5*t)+0.35*sin(2*PI*4699*t)*exp(-3*t)+0.3*sin(2*PI*6272*t)*exp(-4*t)':d=3" "ring"
$thud = TmpSynth "aevalsrc='sin(2*PI*48*t)*exp(-3*t)+0.5*(random(0)*2-1)*exp(-25*t)':d=2" "thud"
MixN @($brass, $thud, "$imp\impactBell_heavy_003.ogg") @("volume=1.4", "volume=1.5", "adelay=60,asetrate=44100*0.6,aresample=44100,volume=0.8") "arsenal/furioso_sting" "$verb"
Synth "aevalsrc='0.5*sin(2*PI*(180-140*t)*t)*exp(-1.6*t)+0.3*(random(0)*2-1)*exp(-4*t)':d=1.4,lowpass=f=900,$verb,volume=2.6,alimiter=limit=0.9" "arsenal/slowmo"
MixN @("$imp\impactMetal_light_003.ogg", "$Slop\dash\whoosh2.ogg") @("asetrate=44100*1.2,aresample=44100,volume=1.2", "volume=0.8") "arsenal/weapon_swap" "$room"
MixN @("$imp\impactPunch_medium_001.ogg", "$imp\impactPunch_heavy_002.ogg") @("volume=1.3", "asetrate=44100*1.3,aresample=44100,volume=0.7") "arsenal/rush_hit" "anull"
MixN @("$imp\impactPunch_heavy_004.ogg", "$imp\impactPlate_heavy_001.ogg", $thud) @("asetrate=44100*0.75,aresample=44100,volume=1.6", "asetrate=44100*0.7,aresample=44100,volume=1.0", "volume=1.2") "arsenal/blunt" "$room"
MixN @("$Slop\dash\whoosh3.ogg", "$imp\impactMetal_heavy_002.ogg", "$sq\squish_06.mp3") @("volume=1.2", "adelay=80,volume=1.2", "adelay=90,volume=0.9") "arsenal/spear" "$room"
MixN @($boom, "$sci\explosionCrunch_004.ogg", "$imp\impactMetal_heavy_003.ogg") @("volume=1.8", "asetrate=44100*1.1,aresample=44100,volume=1.0", "asetrate=44100*0.8,aresample=44100,volume=0.6") "arsenal/shotgun" "aecho=0.6:0.5:120|260:0.3|0.15"
MixN @("$Slop\arsenal\iai_draw.ogg", $whoosh, $ring, "$sci\lowFrequency_explosion_000.ogg") @("volume=1.0", "adelay=500,volume=1.4", "adelay=600,volume=1.2", "adelay=620,volume=1.2") "arsenal/durandal" "$verb"
$spin = TmpSynth "aevalsrc='0.4*sin(2*PI*(220+400*t)*t)*(0.6+0.4*sin(2*PI*(12+30*t)*t))*min(1,t*2)':d=1.6" "spin"
MixN @($spin, "$sci\lowFrequency_explosion_001.ogg", "$imp\impactPunch_heavy_000.ogg") @("volume=1.2", "adelay=900,volume=1.5", "adelay=880,asetrate=44100*0.6,aresample=44100,volume=1.5") "arsenal/rotation" "apad=pad_dur=1,$verb,atrim=0:4,afade=t=out:st=3:d=1"

# ---- the Claw: a W blink, a claw slash, a serum injection, Serum R's charge, the rift he tears open, the shatter
New-Item -ItemType Directory -Force "$Out\claw" | Out-Null
Synth "aevalsrc='0.6*(random(0)*2-1)*exp(-14*t)*(1+0.5*sin(2*PI*(3200-2600*t)*t))+0.4*sin(2*PI*(1800-1400*t)*t)*exp(-9*t)':d=0.45,highpass=f=600,aecho=0.5:0.4:40:0.25,volume=2.2,alimiter=limit=0.9" "claw/blink"
Synth "aevalsrc='0.9*(random(0)*2-1)*exp(-40*t)+0.35*sin(2*PI*2400*t)*exp(-7*t)+0.25*sin(2*PI*3610*t)*exp(-9*t)':d=0.6,highpass=f=900,aecho=0.6:0.4:50:0.2,volume=2.0,alimiter=limit=0.9" "claw/slash"
Synth "aevalsrc='0.5*(random(0)*2-1)*min(1,t*8)*exp(-2.5*t)+0.6*(random(1)*2-1)*exp(-300*t)':d=0.9,highpass=f=3500,volume=2.4,alimiter=limit=0.9" "claw/inject"
Synth "aevalsrc='0.7*(random(0)*2-1)*min(1,t*3)*exp(-1.2*max(0,t-0.5))+0.6*sin(2*PI*(60+30*t)*t)*exp(-1.5*t)':d=1.6,lowpass=f=1400,aecho=0.7:0.5:120:0.3,volume=2.6,alimiter=limit=0.9" "claw/charge"
Synth "aevalsrc='0.5*(random(0)*2-1)*(0.6+0.4*sin(2*PI*31*t))*min(1,t*2)*exp(-0.8*t)+0.45*sin(2*PI*(90+260*t)*t)*min(1,t*3)*exp(-0.7*t)':d=2.4,bandpass=f=1600:width_type=o:w=3,aecho=0.8:0.6:180|420:0.35|0.2,volume=3.0,alimiter=limit=0.9" "claw/rift"
Synth "aevalsrc='0.4*sin(2*PI*3136*t)*exp(-5*t)+0.35*sin(2*PI*4186*t)*exp(-6*t)+0.3*sin(2*PI*5274*t)*exp(-8*t)+0.25*sin(2*PI*2637*t)*exp(-4*t)+0.8*(random(0)*2-1)*exp(-25*t)':d=1.4,highpass=f=700,aecho=0.7:0.5:90|210:0.3|0.15,volume=2.2,alimiter=limit=0.9" "claw/shatter"

Write-Output "Arsenal sounds built:"
Get-ChildItem "$Out\arsenal" -Filter *.ogg | ForEach-Object { $_.Name }
