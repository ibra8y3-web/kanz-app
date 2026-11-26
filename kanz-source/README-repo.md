<div align="center">
  <img src="docs/icon.png" alt="Kanz icon" width="112">
  <h1>Kanz Catalog · كتالوج كنز</h1>
  <p><strong>The discovery, verification and publishing engine behind the Kanz Android app store.</strong></p>
  <p>
    <a href="https://github.com/ibra8y3-web/kanz-catalog/actions/workflows/crawl.yml"><img src="https://github.com/ibra8y3-web/kanz-catalog/actions/workflows/crawl.yml/badge.svg" alt="Catalog crawler workflow"></a>
    <a href="https://github.com/ibra8y3-web/kanz-catalog/blob/main/LICENSE"><img src="https://img.shields.io/github/license/ibra8y3-web/kanz-catalog" alt="MIT License"></a>
    <a href="https://ibra8y3-web.github.io/kanz-catalog/"><img src="https://img.shields.io/badge/catalog-live-2ea44f" alt="Live catalog"></a>
    <img src="https://img.shields.io/badge/sources-GitHub%20%7C%20GitLab%20%7C%20Codeberg-5865F2" alt="Supported sources">
  </p>
  <p>
    <a href="https://ibra8y3-web.github.io/kanz-catalog/"><strong>Explore the live catalog</strong></a>
    · <a href="https://github.com/ibra8y3-web/kanz-app">Kanz Android app</a>
    · <a href="https://github.com/ibra8y3-web/kanz-app/releases/latest/download/kanz.apk">Download Kanz</a>
  </p>
</div>

Kanz Catalog is the automated catalog pipeline used by [Kanz for Android](https://github.com/ibra8y3-web/kanz-app). It discovers Android projects, checks their repository and release metadata, verifies downloadable APK artifacts, preserves version history, and publishes a compressed catalog together with a browsable static website.

The service runs on **GitHub Actions** and publishes through **GitHub Pages**. It does not require a separately managed server.

## Contents

- [How it works](#how-it-works)
- [Discovery and acceptance rules](#discovery-and-acceptance-rules)
- [Published data](#published-data)
- [Automation](#automation)
- [Run and test locally](#run-and-test-locally)
- [Configuration](#configuration)
- [Submitting an app](#submitting-an-app)
- [Repository layout](#repository-layout)
- [Important limitations](#important-limitations)
- [License](#license)
- [العربية](#العربية)

## How it works

The crawler runs a recurring discovery and publishing cycle:

1. **Discover projects.** Search configured Android topics and seeded owners on GitHub, then search configured topics on GitLab and Codeberg. Incremental searches and periodic sweep jobs help find newly created or updated projects.
2. **Check project eligibility.** Inspect the repository license, archive and fork status, project metadata and configured exclusion rules.
3. **Verify releases.** Look for non-draft releases containing APK files. Accept only files that pass the configured filename and size checks and have a verifiable SHA-256 digest.
4. **Preserve release history.** New versions are appended rather than silently replacing old records. When an upstream asset changes or disappears, the catalog records the relevant status.
5. **Reduce duplicates.** Identical APK hashes are linked to the earlier catalog entry; possible name-based duplicates are marked for review.
6. **Enrich and publish.** Categorize apps, collect available icons and screenshots, generate app pages and QR codes, then publish the full catalog and its incremental update.

## Discovery and acceptance rules

The crawler currently searches **GitHub, GitLab and Codeberg**. Search topics, owner seeds and acceptance rules are maintained in [<code>config.json</code>](config.json).

A project must pass the configured checks before it appears in the public catalog:

- Its license must be in the configured allow-list. An explicitly trusted owner can be configured as an exception when license metadata is absent; such an entry is marked as unspecified.
- The repository must not be archived.
- Forks are excluded unless the owner is explicitly trusted in the configuration.
- Project names, descriptions, topics and APK filenames are checked against the configured exclusion pattern for modified, cracked or otherwise disallowed packages.
- A non-draft release must contain an APK that passes the file checks. The current configured maximum APK size is **150 MB**.
- The APK must have a SHA-256 digest supplied by the source or successfully calculated by the crawler. Files whose integrity hash cannot be verified are not published.
- Debug, unsigned and test APK filenames are skipped according to the current configuration, with the configured trusted-owner exception.

These rules are configurable safeguards, not a manual security audit.

## Published data

The generated files are published under [<code>docs/</code>](docs/):

| File | Purpose |
| --- | --- |
| [<code>catalog.json.gz</code>](docs/catalog.json.gz) | Complete compressed catalog, including app metadata and version records |
| [<code>delta.json.gz</code>](docs/delta.json.gz) | Incremental catalog data covering the configured recent revision window |
| [<code>meta.json</code>](docs/meta.json) | Catalog revision, generation time, app and version counts, and compressed sizes |
| <code>a/</code> and <code>l/</code> | Generated app pages and shared-list pages |
| <code>icons/</code> and <code>shots/</code> | Available app icons and screenshots |
| [<code>index.html</code>](docs/index.html) | Catalog website entry point |
| [<code>kanz.apk</code>](docs/kanz.apk) | APK download mirrored on the catalog website |

The crawler also stores its working state in <code>data/state.json</code>. This includes discovery checkpoints, known versions and rejection records used to avoid repeating unchanged work.

### Live endpoints

- **Catalog website:** https://ibra8y3-web.github.io/kanz-catalog/
- **Catalog data directory:** https://raw.githubusercontent.com/ibra8y3-web/kanz-catalog/main/docs/
- **Full catalog:** https://raw.githubusercontent.com/ibra8y3-web/kanz-catalog/main/docs/catalog.json.gz
- **Delta catalog:** https://raw.githubusercontent.com/ibra8y3-web/kanz-catalog/main/docs/delta.json.gz
- **Catalog metadata:** https://raw.githubusercontent.com/ibra8y3-web/kanz-catalog/main/docs/meta.json
- **Kanz APK:** https://ibra8y3-web.github.io/kanz-catalog/kanz.apk

The Android app checks the small metadata file first and can use the incremental data when its local catalog revision is compatible.

## Automation

The primary workflow is [<code>.github/workflows/crawl.yml</code>](.github/workflows/crawl.yml).

| Trigger | Behavior |
| --- | --- |
| Schedule | Starts at minutes 7 and 37 of each hour |
| Manual | Run <code>crawl</code> from the repository's **Actions** tab |
| Relevant push | Runs when crawler code, <code>config.json</code> or the crawl workflow changes on <code>main</code> |

The workflow uses the repository-provided <code>GITHUB_TOKEN</code>, installs the image-processing dependencies used by the workflow, runs the crawler, and commits generated catalog and site changes. It also includes a guarded self-dispatch step; setting the repository variable <code>KANZ_CHAIN</code> to <code>off</code> disables that additional dispatch.

The app-submission workflow is separate: an issue created with the **App suggestion** template is queued for the crawler to process.

## Run and test locally

Use Python 3.8 or newer.

Run the offline test suite:

~~~bash
python3 tests/test_offline.py
~~~

A successful run prints:

~~~text
ALL OFFLINE TESTS PASSED
~~~

To run the crawler against live services, provide a GitHub token to avoid the much lower unauthenticated API rate limit:

~~~bash
GITHUB_TOKEN="YOUR_GITHUB_TOKEN" python3 crawler/crawl.py
~~~

The GitHub Actions workflow uses Python 3.11 and installs Pillow and CairoSVG for image processing before running the crawler.

## Configuration

Edit [<code>config.json</code>](config.json) to adjust the pipeline without changing its source code. Current settings include:

- GitHub search queries and GitLab / Codeberg topics.
- Seed owners and repositories.
- Accepted license identifiers and trusted-owner exceptions.
- Exclusion patterns and debug APK handling.
- Maximum APK size and per-run download, time and image-processing budgets.
- Discovery sweep ranges, page limits and delta revision window.

Changes to the configuration file on <code>main</code> trigger the crawl workflow automatically.

## Submitting an app

Have an eligible open-source Android project with a public APK release?

1. Open [Issues](https://github.com/ibra8y3-web/kanz-catalog/issues/new/choose).
2. Select **App suggestion** and provide the project details requested by the form.
3. The submission is queued and checked by the same automated acceptance rules as discovered projects.

A submission is a request for review by the crawler, not a guarantee of inclusion.

## Repository layout

| Path | Purpose |
| --- | --- |
| <code>.github/workflows/crawl.yml</code> | Scheduled and manually triggered crawler |
| <code>.github/workflows/submit.yml</code> | App-suggestion queue automation |
| <code>crawler/</code> | Discovery, validation, hashing, enrichment and publishing code |
| <code>config.json</code> | Search topics, eligibility rules and execution budgets |
| <code>data/state.json</code> | Persistent crawler state |
| <code>docs/</code> | Public catalog data, website, app pages and media |
| <code>tests/</code> | Offline regression tests |

## Important limitations

- Automated checks do not prove that an application is harmless or free of vulnerabilities. Review the upstream source, project reputation, permissions and release information before installing an APK.
- A project without a qualifying public APK release, an accepted license (or configured trusted-owner exception), or a verifiable SHA-256 digest will not be published.
- GitHub Actions schedules can be delayed by GitHub. The displayed schedule is the configured trigger, not a promise of exact execution time.
- The catalog is generated from public repository and release metadata; inclusion does not mean Kanz has manually audited or endorsed the upstream software.

For security reports, see [SECURITY.md](SECURITY.md). For code contributions and bug reports, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Kanz Catalog is available under the [MIT License](LICENSE).

---

<div dir="rtl" align="right">

## العربية

**كتالوج كنز (Kanz Catalog)** هو محرك الاكتشاف والتحقق والنشر الذي يمد متجر كنز لتطبيقات أندرويد ببيانات التطبيقات والإصدارات.

يبحث النظام تلقائيًا في **GitHub وGitLab وCodeberg**، ويفحص بيانات المستودعات والإصدارات، ويتحقق من ملفات APK وبصمات SHA-256، ثم ينشر كتالوجًا مضغوطًا وموقعًا يمكن تصفحه.

يعمل النظام باستخدام **GitHub Actions**، وينشر الموقع والبيانات عبر **GitHub Pages**، دون الحاجة إلى خادم مستقل.

### دورة العمل

1. اكتشاف مشروعات أندرويد من مصادر ومستودعات متعددة.
2. فحص الترخيص وحالة المستودع والكلمات المستبعدة.
3. البحث عن إصدارات منشورة تحتوي على ملفات APK صالحة.
4. التحقق من بصمة SHA-256؛ ولا يُنشر الملف إذا تعذر التحقق منها.
5. الاحتفاظ بسجل الإصدارات القديمة وتمييز الملفات التي تغيرت أو اختفت.
6. اكتشاف الملفات المتطابقة وتقليل التكرار.
7. تصنيف التطبيقات وتجهيز الأيقونات والصور وصفحات التطبيقات.
8. نشر الكتالوج الكامل والتحديثات الجزئية وبيانات الإصدار.

### البيانات المنشورة

| الملف | الوظيفة |
| --- | --- |
| <code>docs/catalog.json.gz</code> | الكتالوج الكامل |
| <code>docs/delta.json.gz</code> | التحديثات الجزئية ضمن نافذة المراجعات المحددة |
| <code>docs/meta.json</code> | رقم مراجعة الكتالوج ووقت التوليد والأحجام والإحصائيات |
| <code>docs/a/</code> | صفحات التطبيقات |
| <code>docs/l/</code> | صفحات القوائم المشتركة |
| <code>docs/icons/</code> و<code>docs/shots/</code> | الأيقونات والصور المتاحة |
| <code>docs/kanz.apk</code> | نسخة APK المتاحة من موقع الكتالوج |

### التشغيل التلقائي

يعمل الـ crawler مجدولًا مرتين كل ساعة، عند الدقيقتين 7 و37، ويمكن تشغيله يدويًا من تبويب **Actions**. كما يمكنه العمل عند تحديث ملفات الزاحف أو <code>config.json</code> على الفرع <code>main</code>.

يستخدم الـ workflow الرمز <code>GITHUB_TOKEN</code> الذي يوفره GitHub تلقائيًا، ويحدّث ملفات الكتالوج والموقع عند وجود تغييرات.

### الاختبار والتشغيل المحلي

يتطلب التشغيل Python 3.8 أو أحدث. لاختبار منطق الزاحف دون اتصال بالإنترنت:

~~~bash
python3 tests/test_offline.py
~~~

ولتشغيل دورة الاكتشاف الفعلية، استخدم رمز GitHub:

~~~bash
GITHUB_TOKEN="YOUR_GITHUB_TOKEN" python3 crawler/crawl.py
~~~

### اقتراح تطبيق جديد

افتح [نموذج اقتراح تطبيق](https://github.com/ibra8y3-web/kanz-catalog/issues/new/choose)، واختر **App suggestion**، وأدخل بيانات المشروع. سيُضاف الطلب إلى قائمة الانتظار ويمر على قواعد التحقق نفسها؛ وإرسال الاقتراح لا يضمن قبوله تلقائيًا.

### تنبيه مهم

الفحص الآلي لا يُعد مراجعة أمنية يدوية ولا يضمن خلو التطبيق من الثغرات أو السلوك الضار. راجع مصدر التطبيق وصلاحياته ومعلومات الإصدار قبل التثبيت.

- [موقع كتالوج كنز](https://ibra8y3-web.github.io/kanz-catalog/)
- [مستودع تطبيق كنز](https://github.com/ibra8y3-web/kanz-app)
- [تحميل تطبيق كنز](https://github.com/ibra8y3-web/kanz-app/releases/latest/download/kanz.apk)
- [المساهمة](CONTRIBUTING.md) · [سياسة الأمان](SECURITY.md)
- [رخصة MIT](LICENSE)

</div>
