import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import styles from './index.module.css';

function HeroHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className="container">
        <h1 className={styles.heroTitle}>Helix JVM Scripting Engine</h1>
        <p className={styles.heroSubtitle}>
          High-Throughput Dynamic Bytecode Compilation, JVM Mechanical Sympathy & Sub-12ns Rule Evaluation.
        </p>
        <div className={styles.buttons}>
          <Link
            className={clsx('button button--primary button--lg', styles.primaryBtn)}
            to="/docs/getting-started/introduction">
            Get Started →
          </Link>
          <Link
            className={clsx('button button--secondary button--lg', styles.secondaryBtn)}
            to="/docs/architecture-and-internals/system-overview">
            Explore Architecture
          </Link>
        </div>

        <div className={styles.statsBar}>
          <div className={styles.statItem}>
            <div className={styles.statValue}>&lt; 8 ns</div>
            <div className={styles.statLabel}>Sync Rule Evaluation</div>
          </div>
          <div className={styles.statItem}>
            <div className={styles.statValue}>&lt; 12 ns</div>
            <div className={styles.statLabel}>Tier 1 Cache Hit</div>
          </div>
          <div className={styles.statItem}>
            <div className={styles.statValue}>450k+</div>
            <div className={styles.statLabel}>Ops/Sec Batch Throughput</div>
          </div>
          <div className={styles.statItem}>
            <div className={styles.statValue}>0 B</div>
            <div className={styles.statLabel}>Sync Heap Allocations</div>
          </div>
        </div>
      </div>
    </header>
  );
}

const FeatureList = [
  {
    title: 'On-the-Fly Bytecode Compilation',
    description: (
      <>
        Compiles declarative JSON business rules directly into raw JVM bytecode in-memory via ByteBuddy or ASM. Zero interpretation overhead.
      </>
    ),
  },
  {
    title: 'Multi-Tiered Reference Caching',
    description: (
      <>
        3-level cache hierarchy (L1 Strong Caffeine, L2 SoftReference, L3 WeakReference) delivering sub-12ns lookups while preventing heap exhaustion.
      </>
    ),
  },
  {
    title: 'Metaspace Safety & Dynamic ClassLoaders',
    description: (
      <>
        Isolated and hierarchical ClassLoader topologies allowing seamless dynamic rule hot-reloading with guaranteed Metaspace GC unloading.
      </>
    ),
  },
  {
    title: 'Deep JVM Observability & Experiments',
    description: (
      <>
        Built-in research suites measuring HotSpot JIT compilation tiers, GC reference pressure, Safepoint TTSP delays, and JOL memory headers.
      </>
    ),
  },
  {
    title: 'Interactive TUI & Automation CLI',
    description: (
      <>
        Full-featured Picocli command-line interface paired with a Lanterna terminal UI dashboard displaying live CPU, GC, and cache metrics.
      </>
    ),
  },
  {
    title: 'Continuous JMH Benchmarking Gates',
    description: (
      <>
        Multi-OS GitHub Actions CI/CD workflows enforcing automated JMH throughput regression gates on every pull request.
      </>
    ),
  },
];

function FeaturesSection() {
  return (
    <section className={styles.featuresSection}>
      <div className="container">
        <div className={styles.grid}>
          {FeatureList.map((props, idx) => (
            <div key={idx} className={styles.featureCard}>
              <h3 className={styles.cardTitle}>{props.title}</h3>
              <p className={styles.cardDesc}>{props.description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export default function Home(): React.JSX.Element {
  return (
    <Layout
      title="Helix JVM Engine - Documentation"
      description="Production-grade JVM dynamic bytecode compilation and profiling engine.">
      <HeroHeader />
      <main>
        <FeaturesSection />
      </main>
    </Layout>
  );
}
