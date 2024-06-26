val isOnCI = System.getenv()["GITHUB_ACTIONS"] != null

val KUERY_CLIENT_VERSION = "0.4.0" + if (isOnCI) "" else "-SNAPSHOT"
