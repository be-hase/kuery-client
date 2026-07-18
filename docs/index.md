---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Kuery Client"
  text: "Write SQL as it is"
  tagline: A Kotlin/JVM database client built on spring-data — string interpolation becomes bind parameters via a compiler plugin
  actions:
    - theme: brand
      text: Introduction
      link: /introduction
    - theme: alt
      text: GitHub
      link: https://github.com/be-hase/kuery-client

features:
  - icon: ♥️
    title: Love SQL
    details: ORM libraries are convenient, but they each require learning their own DSL, which we believe is a steep cost. Kuery Client emphasizes writing SQL as it is.
  - icon: 🛡️
    title: Safe by design
    details: String interpolation is converted into bind parameters by the compiler plugin — never concatenated into the SQL text. SQL injection is prevented by construction.
  - icon: ✅
    title: Compile-time checks
    details: The compiler plugin warns when a SQL string cannot be converted safely, and can optionally validate SQL syntax — mistakes surface at compile time, not in production.
    link: /compiler-safety-check
  - icon: 🍃
    title: Built on Spring Data
    details: Kuery Client is implemented on top of spring-data-r2dbc and spring-data-jdbc. Use whichever you prefer. You can keep using Spring's ecosystem as is, such as @Transactional.
  - icon: 🔭
    title: Observability
    details: It supports Micrometer Observation, so you can collect and customize metrics, tracing, and logging.
  - icon: 🧩
    title: Extensible
    details: When dealing with complex data schemas, you often want to share common query logic. Kotlin's extension functions make this easy.
---
