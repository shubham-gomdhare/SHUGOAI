-keep class com.arm.shugoai.* { *; }
-keep class com.arm.shugoai.gguf.* { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class kotlin.Metadata { *; }
