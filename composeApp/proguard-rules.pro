# This is an opensource project
# obfuscating code only makes debugging harder
-dontobfuscate
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

-optimizationpasses 5

-dontpreverify

-repackageclasses ''

## Rules for NewPipeExtractor
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.ScriptEngineFactory

## Rules for OkHttp3 - Added since 5.1.0
-dontwarn okhttp3.internal.Util
