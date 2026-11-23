// Build file a nivel de proyecto. No declara dependencias propias, solo los
// plugins que los módulos (app/) pueden aplicar.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
