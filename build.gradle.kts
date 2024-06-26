val isOnCI = System.getenv()["GITHUB_ACTIONS"] != null

allprojects {
    group = "dev.hsbrysk.kuery-client"
    version = "0.4.0" + if (isOnCI) "" else "-SNAPSHOT"
}
