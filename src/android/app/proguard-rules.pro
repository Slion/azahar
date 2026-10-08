# Copyright 2023 Citra Emulator Project
# Licensed under GPLv2 or any later version
# Refer to the license.txt file included.

# To get usable stack traces
-dontobfuscate

# Prevents crashing when using Wini. Wini resolves its parser/builder/formatter
# through ServiceFinder and instantiates them reflectively (no META-INF/services
# file, so the concrete classes themselves are instantiated), which needs their
# no-arg constructors to survive shrinking. A class-only keep rule no longer keeps
# members of library classes under the AGP 9 R8 defaults. Do NOT keep the whole
# org.ini4j package: BeanTool references the desktop-only java.beans classes.
-keep class org.ini4j.spi.IniParser {
    <init>(...);
}
-keep class org.ini4j.spi.IniBuilder {
    <init>(...);
}
-keep class org.ini4j.spi.IniFormatter {
    <init>(...);
}

# AGP 9 R8 no longer implicitly keeps the default constructor of kept classes;
# Room instantiates the generated *Database_Impl subclasses via reflection.
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Suppress warnings for R8
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn java.beans.Introspector
-dontwarn java.beans.VetoableChangeListener
-dontwarn java.beans.VetoableChangeSupport

# Don't include VERBOSE log calls in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
}
