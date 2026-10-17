# PDFBox looks up fonts, CMaps and security handlers by name at run time.
-keep class com.tom_roush.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
