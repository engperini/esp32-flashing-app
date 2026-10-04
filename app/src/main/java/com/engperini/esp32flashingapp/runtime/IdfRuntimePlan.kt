package com.engperini.esp32flashingapp.runtime

/**
 * Version-pinned production build environment. UI code must not assemble
 * provisioning shell commands; this is the policy boundary for ESP-IDF setup.
 */
object IdfRuntimePlan {
    const val GUEST_ALIAS = "idf-base"
    const val ESP_IDF_VERSION = "v5.5"
    const val IDF_PATH = "/opt/esp/idf/v5.5"
    const val IDF_TOOLS_PATH = "/opt/esp/tools/v5.5"

    val debianPackages = listOf(
        "ca-certificates", "git", "wget", "flex", "bison", "gperf",
        "python3", "python3-pip", "python3-venv", "cmake", "ninja-build",
        "ccache", "libffi-dev", "libssl-dev", "dfu-util", "libusb-1.0-0"
    )

    fun targetInstallCommand(target: String): String {
        require(target.matches(Regex("esp32[a-z0-9]*"))) { "Unsupported ESP target: $target" }
        return "export IDF_TOOLS_PATH='$IDF_TOOLS_PATH' && cd '$IDF_PATH' && ./install.sh '$target'"
    }

    fun commonSetupCommand(): String =
        "export DEBIAN_FRONTEND=noninteractive && " +
        "apt-get update && apt-get install -y --no-install-recommends ${debianPackages.joinToString(" ")} && " +
        "mkdir -p /opt/esp/idf /opt/esp/tools && " +
        "if [ ! -d '$IDF_PATH/.git' ]; then git clone --branch '$ESP_IDF_VERSION' --depth 1 --recursive " +
        "https://github.com/espressif/esp-idf.git '$IDF_PATH'; fi && " +
        "cd '$IDF_PATH' && git describe --tags --always"
}
