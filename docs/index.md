---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Kuery Client"
  tagline: A Kotlin/JVM database client for those who want to write SQL
  actions:
    - theme: brand
      text: Get Started
      link: /getting-started
    - theme: alt
      text: Introduction
      link: /introduction
    - theme: alt
      text: GitHub
      link: https://github.com/be-hase/kuery-client

features:
  - title: Love SQL
    details: ORM libraries are convenient, but they each require learning their own DSL, which we believe is a steep cost. Kuery Client emphasizes writing SQL as it is.
  - title: Based on spring-data-r2dbc and spring-data-jdbc
    details: Kuery Client is implemented on top of spring-data-r2dbc and spring-data-jdbc. Use whichever you prefer. You can keep using Spring's ecosystem as is, such as @Transactional.
  - title: Observability
    details: It supports Micrometer Observation, so you can collect and customize metrics, tracing, and logging.
---
