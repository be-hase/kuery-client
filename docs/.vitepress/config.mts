import {execSync} from 'node:child_process'
import {defineConfig} from 'vitepress'
import llmstxt from 'vitepress-plugin-llms'

// Latest release version, used to replace `{{version}}` placeholders in markdown files at
// build time. Resolution order: the DOCS_VERSION env var, the latest git tag
// (e.g. "v1.0.0" -> "1.0.0"), then "latest" for environments where no tag is reachable
// (shallow clones, source archives).
const version = resolveVersion()

function resolveVersion(): string {
    if (process.env.DOCS_VERSION) {
        return process.env.DOCS_VERSION
    }
    try {
        return execSync('git describe --tags --abbrev=0', {stdio: ['ignore', 'pipe', 'ignore']})
            .toString()
            .trim()
            .replace(/^v/, '')
    } catch {
        return 'latest'
    }
}

const hostname = "https://kuery-client.hsbrysk.dev"

// https://vitepress.dev/reference/site-config
export default defineConfig({
    lang: "en-US",
    title: "Kuery Client",
    description: "A Kotlin/JVM database client for those who want to write SQL",
    lastUpdated: true,
    sitemap: {
        hostname,
    },
    head: [
        ["meta", {property: "og:type", content: "website"}],
        ["meta", {property: "og:site_name", content: "Kuery Client"}],
        ["meta", {property: "og:title", content: "Kuery Client"}],
        ["meta", {property: "og:description", content: "A Kotlin/JVM database client for those who want to write SQL"}],
        ["meta", {property: "og:url", content: hostname}],
        ["meta", {property: "og:image", content: `${hostname}/logo.png`}],
        ["meta", {name: "twitter:card", content: "summary"}],
    ],
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
            {text: "Docs", link: "/getting-started"},
        ],

        sidebar: [
            {
                text: "Guide",
                items: [
                    {text: "Introduction", link: '/introduction'},
                    {text: "Getting Started", link: '/getting-started'},
                    {text: "Building SQL", link: '/basics'},
                    {text: "Fetching Results", link: '/fetching-results'},
                    {text: "Row Mapping", link: '/row-mapping'},
                    {text: "Transaction", link: '/transaction'},
                ]
            },
            {
                text: "Safety",
                items: [
                    {text: "Compile-Time Checks", link: '/compiler-safety-check'},
                    {text: "SQL Syntax Check", link: '/sql-syntax-check'},
                ]
            },
            {
                text: "Advanced",
                items: [
                    {text: "Type Conversion", link: '/type-conversion'},
                    {text: "Observation", link: '/observation'},
                    {text: "Helpers", link: '/helpers'},
                ]
            },
            {
                text: "Reference",
                items: [
                    {text: "Configuration", link: '/configuration'},
                    {text: "Supported Platforms", link: '/supported-platforms'},
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
        },

        editLink: {
            pattern: 'https://github.com/be-hase/kuery-client/edit/main/docs/:path',
            text: 'Edit this page on GitHub'
        }
    }
})
