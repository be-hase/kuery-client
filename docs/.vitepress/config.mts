import {execSync} from 'node:child_process'
import {defineConfig} from 'vitepress'
import llmstxt from 'vitepress-plugin-llms'

// Latest release version, derived from the latest git tag (e.g. "v1.0.0" -> "1.0.0").
// Used to replace `{{version}}` placeholders in markdown files at build time.
const version = execSync('git describe --tags --abbrev=0').toString().trim().replace(/^v/, '')

// https://vitepress.dev/reference/site-config
export default defineConfig({
    lang: "en-US",
    title: "Kuery Client",
    description: "A Kotlin/JVM database client for those who want to write SQL",
    vite: {
        plugins: [
            {
                name: 'replace-kuery-client-version',
                enforce: 'pre',
                transform(code, id) {
                    if (id.endsWith('.md') && code.includes('{{version}}')) {
                        return code.replaceAll('{{version}}', version)
                    }
                },
            },
            llmstxt(),
        ],
    },
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
                    {text: "Basics", link: '/basics'},
                    {text: "Transaction", link: '/transaction'},
                    {text: "Type Conversion", link: '/type-conversion'},
                    {text: "Observation", link: '/observation'},
                    {text: "Detekt Custom Rules", link: '/detekt'},
                    {text: "Helpers", link: '/helpers'},
                    {text: "Examples", link: '/examples'},
                    {text: "Compatibility", link: '/compatibility'},
                ]
            }
        ],

        socialLinks: [
            {icon: "github", link: "https://github.com/be-hase/kuery-client"}
        ],

        search: {
            provider: 'local'
        }
    }
})
