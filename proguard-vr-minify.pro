# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/kanawish/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
#-keep class com.google.vr.** {*;}
#-keep interface com.google.vr.** {*;}

#-keep enum com.google.protobuf.** {*;}
#-keep class com.google.protobuf.** {*;}
#-keep interface com.google.protobuf.** {*;}

# Avoid obfuscation entirely
-dontobfuscate

# https://github.com/square/okio/issues/60
-dontwarn okio.**


###################
## Below copied from GVR proguard, some things are not pertinent given above -dontobfuscate
###################

# Don't obfuscate any NDK/SDK code. This makes the debugging of stack traces
# in release builds easier.
-keepnames class com.google.vr.ndk.** { *; }
-keepnames class com.google.vr.sdk.** { *; }

# These are part of the Java <-> native interfaces for GVR.
-keepclasseswithmembernames,includedescriptorclasses class com.google.vr.** {
    native <methods>;
}

# The SDK configuration class member names are useful for debugging client logs.
-keepclasseswithmembernames,allowoptimization class com.google.common.logging.nano.Vr$VREvent$SdkConfigurationParams** {
    *;
}

-keep class com.google.vr.cardboard.UsedByNative
-keep @com.google.vr.cardboard.UsedByNative class *
-keepclassmembers class * {
    @com.google.vr.cardboard.UsedByNative *;
}

-keep class com.google.vr.cardboard.annotations.UsedByNative
-keep @com.google.vr.cardboard.annotations.UsedByNative class *
-keepclassmembers class * {
    @com.google.vr.cardboard.annotations.UsedByNative *;
}

-keep class com.google.vr.cardboard.annotations.UsedByReflection
-keep @com.google.vr.cardboard.annotations.UsedByReflection class *
-keepclassmembers class * {
    @com.google.vr.cardboard.annotations.UsedByReflection *;
}

-dontwarn com.google.protobuf.nano.NanoEnumValue

