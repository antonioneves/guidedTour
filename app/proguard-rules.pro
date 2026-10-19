# ML Kit, Room, CameraX and Maps ship their own consumer rules.
# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable

# ML Kit looks up its components by name and reads internal fields reflectively; without these rules
# the release build crashes in TextRecognition.getClient().
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
