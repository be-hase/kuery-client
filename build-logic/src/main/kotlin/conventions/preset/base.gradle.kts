package conventions.preset

import KUERY_CLIENT_VERSION

plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
}

group = "dev.hsbrysk.kuery-client"
version = KUERY_CLIENT_VERSION
