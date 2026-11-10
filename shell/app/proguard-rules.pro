# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in E:\Android\sdk/tools/proguard/proguard-android.txt
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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------- 本工程追加（WebView 桥 + 反射相关） ----------
# @JavascriptInterface 的方法名会被 JS 直接调用（window.GJ.review / accent / hash / cityName ...），
# 名字被混淆就等于桥断了，所以整类保留。
-keep class com.wwxiaoqi.gongjiao.MainActivity$Bridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Activity 由系统按名字实例化
-keep class com.wwxiaoqi.gongjiao.MainActivity { public *; }
# 崩溃栈里保留行号（体积代价很小，排障时值钱）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
