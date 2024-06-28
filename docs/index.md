---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Kuery Client"
  tagline: A Kotlin/JVM database client for those who want to write SQL
  actions:
    - theme: brand
      text: Introduction
      link: /introduction

features:
  - title: Love SQL
    details: While ORM libraries in the world are convenient, they often require learning their own DSL, which we believe has a high learning cost. Kuery Client emphasizes writing SQL as it is.
  - title: Based on spring-data-r2dbc and spring-data-jdbc
    details: Kuery Client is implemented based on spring-data-r2dbc and spring-data-jdbc. Use whichever you prefer. You can use Spring's ecosystem as it is, such as @Transactional.
  - title: Observability
    details: It supports Micrometer Observation, so Metrics/Tracing/Logging can also be customized.
---
