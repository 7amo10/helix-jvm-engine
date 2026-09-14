import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Helix JVM Scripting Engine',
  tagline: 'High-Throughput Dynamic Bytecode Compilation, JVM Profiling & Low-Latency Execution',
  favicon: 'img/favicon.ico',

  url: 'https://7amo10.github.io',
  baseUrl: '/helix-jvm-engine/',

  organizationName: '7amo10',
  projectName: 'helix-jvm-engine',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },
  themes: ['@docusaurus/theme-mermaid'],

  plugins: [
    [
      require.resolve('@easyops-cn/docusaurus-search-local'),
      {
        hashed: true,
        language: ['en'],
        docsRouteBasePath: '/docs',
        indexDocs: true,
        indexBlog: false,
        indexPages: true,
      },
    ],
  ],

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: 'docs',
          editUrl: 'https://github.com/7amo10/helix-jvm-engine/tree/main/website/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/helix-social-card.png',
    docs: {
      sidebar: {
        hideable: true,
        autoCollapseCategories: true,
      },
    },
    colorMode: {
      defaultMode: 'dark',
      disableSwitch: false,
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Helix Engine',
      logo: {
        alt: 'Helix Logo',
        src: 'img/logo.png',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Documentation',
        },
        {
          to: '/docs/architecture-and-internals/system-overview',
          label: 'Architecture',
          position: 'left',
        },
        {
          to: '/docs/jvm-experiments/overview',
          label: 'JVM Experiments',
          position: 'left',
        },
        {
          to: '/docs/performance-tuning/hotspot-jvm-matrix',
          label: 'Performance Tuning',
          position: 'left',
        },
        {
          to: '/docs/helix-cortex/overview',
          label: 'Helix Cortex',
          position: 'left',
        },
        {
          href: 'pathname:///api/index.html',
          label: 'Javadoc API',
          position: 'right',
        },
        {
          href: 'https://github.com/7amo10/helix-jvm-engine',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentation',
          items: [
            {
              label: 'Getting Started',
              to: '/docs/getting-started/introduction',
            },
            {
              label: 'System Architecture',
              to: '/docs/architecture-and-internals/system-overview',
            },
            {
              label: 'Performance Tuning',
              to: '/docs/performance-tuning/hotspot-jvm-matrix',
            },
          ],
        },
        {
          title: 'Subsystems',
          items: [
            {
              label: 'Rule Engine API',
              to: '/docs/api-and-cli-reference/java-api-reference',
            },
            {
              label: 'JVM Experiments',
              to: '/docs/jvm-experiments/overview',
            },
            {
              label: 'Interactive TUI',
              to: '/docs/api-and-cli-reference/tui-dashboard',
            },
            {
              label: 'Helix Cortex Control Plane',
              to: '/docs/helix-cortex/overview',
            },
          ],
        },
        {
          title: 'Community & Code',
          items: [
            {
              label: 'GitHub Repository',
              href: 'https://github.com/7amo10/helix-jvm-engine',
            },
            {
              label: 'Releases',
              href: 'https://github.com/7amo10/helix-jvm-engine/releases',
            },
            {
              label: 'Javadoc Site',
              href: 'pathname:///api/index.html',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Helix JVM Scripting Engine. Built with Docusaurus & Apache 2.0 License.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'json', 'bash', 'yaml', 'javadoc', 'markup'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
