# libtorrent4j's native library calls back into its Java classes by name (SWIG/JNI),
# so they must not be renamed or removed.
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
