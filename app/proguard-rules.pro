# Tink (used by androidx.security-crypto) references compile-time-only annotations that are not
# shipped at runtime. They have no effect on behaviour, so R8 can safely ignore them.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
