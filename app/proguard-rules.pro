# GitCove（码湾）ProGuard 规则
# release 关闭了 minify，此文件作为后续开启混淆时的保底配置

# JGit / JSch 大量使用反射，全量保留
-keep class org.eclipse.jgit.** { *; }
-keep class com.jcraft.jsch.** { *; }
-keep class org.apache.sshd.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.gitcove.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
