import {defineConfig} from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
    lang: "en-US",
    title: "Kuery Client",
    description: "A Kotlin/JVM database client for those who want to write raw queries",
    themeConfig: {
        // https://vitepress.dev/reference/default-theme-config
        nav: [
            {text: "Home", link: "/"},
            {text: "Docs", link: "/introduction"},
        ],

        sidebar: [
            {
                text: "Docs",
                items: [
                    {text: "Introduction", link: '/introduction'},
                    {text: "Getting Started", link: '/getting-started'},
                    {text: "Transaction", link: '/transaction'},
                    {text: "Type Conversion", link: '/type-conversion'},
                    {text: "Observation", link: '/observation'},
                    {text: "Detekt Custom Rules (Experimental)", link: '/detekt'},
                ]
            }
        ],

        socialLinks: [
            {icon: "github", link: "https://github.com/be-hase/kuery-client"}
        ]
    }
})
