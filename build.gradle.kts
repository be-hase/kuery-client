val isOnCI = System.getenv()["GITHUB_ACTIONS"] != null

allprojects {
    group = "dev.hsbrysk.kuery-client"
    version = "0.2.1" + if (isOnCI) "" else "-SNAPSHOT"
}
