plugins {
    id("inmc.paper-plugin")
}

group = "com.inmc.worlds"
version = "1.0.0"

inmc {
    paper = "26.2"
    pluginName = "inmc-worlds"
}

dependencies {
    compileOnly(libs.placeholderapi) { isTransitive = false }
}
