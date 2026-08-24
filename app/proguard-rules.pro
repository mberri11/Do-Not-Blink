# DO NOT BLINK — R8 rules.
#
# Release builds minify and shrink resources. Almost nothing here needs a rule: the game logic is
# reached from Kotlin, Compose ships its own consumer rules, and DataStore Preferences has no
# reflection in it. The two exceptions are the ad SDKs, which are reached reflectively.

# ---- crash deobfuscation ------------------------------------------------------------------------
# Without these, every stack trace in Play Console is line-number-free and useless. The mapping
# file is uploaded automatically with the bundle; these attributes are what it maps back onto.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Google Mobile Ads ---------------------------------------------------------------------------
# MobileAdsInitProvider is named in the manifest, the mediation and ad-format classes are loaded by
# name at runtime, and the SDK reads its own annotations.
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ---- UMP (user messaging platform) ---------------------------------------------------------------
# The consent form is a WebView driven by JS interfaces resolved by name.
-keep class com.google.android.ump.** { *; }
-keep interface com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# ---- annotations the SDKs read at runtime --------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
