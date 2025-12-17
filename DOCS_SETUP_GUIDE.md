# Documentation Website Setup Guide

Complete guide to setting up, deploying, and maintaining the Kafka Connect WebSocket connector documentation website.

## Quick Start

### 1. Install Dependencies

```bash
# Install Python dependencies
pip install -r requirements.txt

# Or manually
pip install mkdocs-material mkdocs-minify-plugin mkdocs-redirects mkdocs-git-revision-date-localized-plugin
```

### 2. Start Local Development Server

```bash
cd /Users/sderosiaux/Desktop/claude-projects/kafka-connect-websocket
mkdocs serve
```

Open browser to `http://localhost:8000`

### 3. Deploy to GitHub Pages

```bash
# Automatic deployment (via GitHub Actions)
git add .
git commit -m "Update documentation"
git push origin main

# Manual deployment
mkdocs gh-deploy --force
```

## Project Structure

```
kafka-connect-websocket/
├── mkdocs.yml                 # MkDocs configuration
├── requirements.txt           # Python dependencies
├── docs/                      # Documentation source files
│   ├── index.md              # Homepage
│   ├── getting-started/      # Getting started guides
│   │   ├── index.md
│   │   ├── prerequisites.md
│   │   ├── installation.md   # To be created
│   │   └── quick-start.md    # To be created
│   ├── configuration/        # Configuration guides
│   ├── use-cases/            # Use case examples
│   ├── operations/           # Operations & monitoring
│   ├── architecture/         # Architecture docs
│   ├── reliability/          # Data reliability guides
│   ├── api/                  # API reference
│   ├── development/          # Development guides
│   ├── stylesheets/
│   │   └── extra.css         # Custom CSS
│   ├── javascripts/
│   │   └── extra.js          # Custom JavaScript
│   ├── faq.md                # FAQ
│   ├── changelog.md          # Changelog
│   └── README.md             # Docs development guide
└── .github/
    └── workflows/
        └── docs.yml          # GitHub Actions workflow
```

## Technology Stack

### Core Technologies

- **MkDocs**: Static site generator (Python-based)
- **Material for MkDocs**: Professional documentation theme
- **GitHub Pages**: Free hosting for open-source projects
- **GitHub Actions**: CI/CD for automatic deployment

### Why MkDocs Material?

| Feature | Benefit |
|---------|---------|
| **Fast Setup** | 5 minutes to working site |
| **Built-in Search** | No external dependencies |
| **Mobile-First** | Responsive design out of the box |
| **Code Highlighting** | Java, JSON, bash syntax support |
| **One-Command Deploy** | `mkdocs gh-deploy` |
| **Versioning Support** | Easy version management with Mike |
| **Low Maintenance** | Static files, no server required |

### Alternative Considered

- **Docusaurus**: React-based, more complex setup, heavier
- **Jekyll**: GitHub Pages native, but less features
- **VitePress**: Vue-based, fast but newer ecosystem
- **Docsify**: Runtime rendering, SEO challenges

## Configuration Files

### mkdocs.yml

Main configuration file with:

```yaml
# Site metadata
site_name: Kafka Connect WebSocket Connector
site_url: https://conduktor.github.io/kafka-connect-websocket/
repo_url: https://github.com/conduktor/kafka-connect-websocket

# Theme configuration
theme:
  name: material
  palette:
    - scheme: default      # Light mode
      primary: black
      accent: red
    - scheme: slate        # Dark mode
      primary: black
      accent: red

  features:
    - navigation.instant    # SPA-like instant loading
    - navigation.tabs       # Top-level tabs
    - navigation.sections   # Expandable sections
    - search.suggest        # Search suggestions
    - search.highlight      # Highlight search results
    - content.code.copy     # Copy code button
    - content.tabs.link     # Linked content tabs

# Extensions
markdown_extensions:
  - admonition              # Callout boxes
  - pymdownx.details        # Collapsible sections
  - pymdownx.superfences    # Code blocks
  - pymdownx.tabbed         # Tabbed content
  - pymdownx.highlight      # Code syntax highlighting
  - toc:
      permalink: true       # Anchor links on headings

# Plugins
plugins:
  - search                  # Built-in search
  - minify                  # Minify HTML/CSS/JS
```

### requirements.txt

Python dependencies:

```txt
mkdocs>=1.5.3
mkdocs-material>=9.5.0
mkdocs-minify-plugin>=0.8.0
mkdocs-redirects>=1.2.1
mkdocs-git-revision-date-localized-plugin>=1.2.0
pymdown-extensions>=10.7
```

### .github/workflows/docs.yml

GitHub Actions workflow for automatic deployment:

```yaml
name: Deploy Documentation

on:
  push:
    branches: [main]
    paths:
      - 'docs/**'
      - 'mkdocs.yml'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.x'
      - run: pip install -r requirements.txt
      - run: mkdocs build --strict
      - run: mkdocs gh-deploy --force
```

## Content Organization

### Information Architecture

Organized around user journeys:

1. **Getting Started** (New users)
   - Prerequisites → Installation → Quick Start
   - Goal: Working connector in 30 minutes

2. **Configuration** (Integration developers)
   - Required → Optional → Examples
   - Goal: Configure for specific API

3. **Operations** (Production operators)
   - Deployment → Monitoring → Troubleshooting
   - Goal: Production-ready deployment

4. **Architecture** (Technical deep dive)
   - Components → Data Flow → Limitations
   - Goal: Understand internals

### Navigation Structure

```
Home
├── Getting Started
│   ├── Prerequisites
│   ├── Installation
│   └── Quick Start
├── Configuration
│   ├── Required Parameters
│   ├── Optional Parameters
│   └── Examples
├── Use Cases
│   ├── Binance
│   ├── Coinbase
│   ├── Echo Server
│   └── Authenticated APIs
├── Operations
│   ├── Deployment
│   ├── Monitoring
│   ├── Health Checks
│   └── Troubleshooting
├── Architecture
│   ├── Components
│   ├── Data Flow
│   └── Limitations
├── Data Reliability
│   ├── Semantics
│   ├── Data Loss
│   └── Mitigation
├── API Reference
│   └── Configuration Properties
├── Development
│   ├── Building
│   ├── Testing
│   └── Contributing
├── FAQ
└── Changelog
```

## Content Migration from README

### Mapping README to Documentation

| README Section | Documentation Location |
|----------------|------------------------|
| Quick Start | getting-started/quick-start.md |
| Prerequisites | getting-started/prerequisites.md |
| Installation | getting-started/installation.md |
| Configuration | configuration/index.md + required-parameters.md + optional-parameters.md |
| Examples (Binance, Coinbase) | use-cases/binance.md, use-cases/coinbase.md |
| Deploying | operations/deployment.md |
| Monitoring | operations/monitoring.md |
| Architecture | architecture/index.md + components.md + data-flow.md |
| Data Loss Warning | reliability/index.md + data-loss.md + mitigation.md |
| Troubleshooting | operations/troubleshooting.md |
| Development | development/building.md + testing.md + contributing.md |
| License & Support | Footer links |

### Content Extraction Strategy

1. **Keep Working Examples**: All code examples from README
2. **Expand Context**: Add more explanation and screenshots
3. **Add Visual Elements**: Diagrams, tables, admonitions
4. **Split Long Sections**: Break into digestible pages
5. **Cross-Link**: Link related sections together

## Development Workflow

### Writing Documentation

1. **Create/Edit Markdown Files**

```bash
# Create new page
touch docs/operations/new-page.md

# Edit with your preferred editor
code docs/operations/new-page.md
```

2. **Add to Navigation** (mkdocs.yml)

```yaml
nav:
  - Operations:
    - operations/index.md
    - operations/new-page.md  # Add here
```

3. **Preview Changes**

```bash
mkdocs serve
# Open http://localhost:8000
```

4. **Commit & Push**

```bash
git add docs/operations/new-page.md mkdocs.yml
git commit -m "Add new operations page"
git push origin main
```

### Content Best Practices

#### Use Admonitions for Important Information

```markdown
!!! warning "Data Loss Risk"
    Messages are lost on connector shutdown.

!!! tip "Performance Tip"
    Increase queue size for high throughput.

!!! danger "Security Warning"
    Never commit auth tokens to version control.
```

#### Use Tabbed Content for Alternatives

```markdown
=== "Option 1: Uber JAR"
    ```bash
    mvn clean package shade:shade
    ```

=== "Option 2: Plugin Directory"
    ```bash
    mvn dependency:copy-dependencies
    ```
```

#### Use Code Annotations

```markdown
```java
WebSocketClient client = new WebSocketClient(
    url,           // (1)
    headers,       // (2)
    authToken      // (3)
);
```
1. WebSocket endpoint (ws:// or wss://)
2. Custom headers map
3. Bearer token (optional)
```

#### Use Mermaid Diagrams

```markdown
```mermaid
graph LR
    A[WebSocket] --> B[Queue]
    B --> C[Kafka]
```
```

## Deployment

### Automatic Deployment (Recommended)

**GitHub Actions** automatically deploys on push to `main`:

1. Edit documentation files
2. Commit changes
3. Push to `main` branch
4. GitHub Actions builds and deploys
5. Site live at: `https://conduktor.github.io/kafka-connect-websocket/`

**View deployment status:**
- GitHub → Actions tab
- Check "Deploy Documentation" workflow

### Manual Deployment

```bash
# Build locally
mkdocs build

# Deploy to GitHub Pages
mkdocs gh-deploy --force

# With custom commit message
mkdocs gh-deploy --message "Update documentation for v1.1.0"
```

### Custom Domain Setup (Optional)

1. **Add CNAME file** to `docs/` directory:

```bash
echo "docs.example.com" > docs/CNAME
```

2. **Configure DNS** (at your domain provider):

```
Type: CNAME
Name: docs
Value: conduktor.github.io
```

3. **Enable in GitHub Settings**:
   - Repository → Settings → Pages
   - Custom domain: `docs.example.com`
   - Enforce HTTPS: ✓

## Maintenance

### Regular Updates

**Monthly:**
- [ ] Update examples with latest versions
- [ ] Test all commands and code snippets
- [ ] Check external links (broken link checker)
- [ ] Review and respond to documentation issues

**Quarterly:**
- [ ] Update screenshots if UI changed
- [ ] Review navigation structure
- [ ] Update FAQ with common questions
- [ ] Performance audit (load times, search)

**Annually:**
- [ ] Major documentation restructure if needed
- [ ] Update design/theme if outdated
- [ ] Gather user feedback survey

### Monitoring

**Check These Metrics:**
- Page views (Google Analytics)
- Search queries (identify gaps)
- Bounce rate (< 60% is good)
- Time on page (> 2 min indicates engagement)
- GitHub issues tagged `documentation`

### Performance Optimization

**MkDocs is fast by default**, but optimize further:

1. **Minify Plugin** (already enabled):
   ```yaml
   plugins:
     - minify:
         minify_html: true
   ```

2. **Image Optimization**:
   ```bash
   # Compress images before committing
   optipng docs/images/*.png
   jpegoptim docs/images/*.jpg
   ```

3. **Lazy Loading** (images):
   ```markdown
   ![Image](image.png){ loading=lazy }
   ```

## Troubleshooting

### Common Issues

#### "Module not found" Error

**Problem:** MkDocs dependencies not installed

**Solution:**
```bash
pip install -r requirements.txt
```

#### "Page not found" in Navigation

**Problem:** File path in `mkdocs.yml` doesn't match actual file

**Solution:**
- Check file exists: `ls docs/path/to/file.md`
- Verify path in `nav:` section of `mkdocs.yml`
- File paths are relative to `docs/` directory

#### Styles Not Applying

**Problem:** CSS cache or build issue

**Solution:**
```bash
# Clear build cache
rm -rf site/

# Rebuild
mkdocs build

# Hard refresh browser (Cmd+Shift+R / Ctrl+Shift+F5)
```

#### GitHub Actions Deployment Fails

**Problem:** Various deployment issues

**Solution:**
- Check Actions tab for error logs
- Verify `requirements.txt` is committed
- Ensure GitHub Pages is enabled in Settings
- Check `gh-pages` branch exists

### Getting Help

- **MkDocs Docs**: https://www.mkdocs.org/
- **Material Docs**: https://squidfunk.github.io/mkdocs-material/
- **GitHub Issues**: Open issue with `documentation` label
- **Slack**: #documentation channel in Conduktor Slack

## Next Steps

### Immediate (Week 1)

- [x] Setup MkDocs configuration ✅
- [x] Create directory structure ✅
- [x] Create homepage ✅
- [x] Create Getting Started overview ✅
- [x] Create Prerequisites page ✅
- [x] Setup GitHub Actions ✅
- [ ] Complete Installation page
- [ ] Complete Quick Start page
- [ ] Extract configuration content from README

### Short-term (Weeks 2-3)

- [ ] Create all Use Cases pages (Binance, Coinbase, etc.)
- [ ] Create Operations pages (Deployment, Monitoring, Troubleshooting)
- [ ] Create Architecture pages
- [ ] Create Data Reliability section
- [ ] Create API Reference
- [ ] Create Development guides

### Long-term (Month 2+)

- [ ] Add search analytics
- [ ] Create video tutorials
- [ ] Add interactive examples (CodePen/JSFiddle)
- [ ] Multi-language support (internationalization)
- [ ] Version documentation with Mike
- [ ] Create Grafana dashboard templates
- [ ] Build downloadable PDF version

## Success Metrics

**Target Goals:**

| Metric | Current | Target (3 months) |
|--------|---------|-------------------|
| Time to first success | N/A | < 30 minutes |
| Documentation coverage | 40% | 100% |
| Search satisfaction | N/A | > 80% |
| GitHub docs issues | N/A | < 5 open |
| Page load time | N/A | < 2 seconds |
| Mobile usability | N/A | 95+ score |

## Resources

### Documentation
- [MkDocs Documentation](https://www.mkdocs.org/)
- [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/)
- [Markdown Guide](https://www.markdownguide.org/)
- [Material Icons](https://materialdesignicons.com/)

### Examples
- [FastAPI Docs](https://fastapi.tiangolo.com/) (MkDocs Material)
- [Pydantic Docs](https://docs.pydantic.dev/) (MkDocs Material)
- [Kubernetes Docs](https://kubernetes.io/docs/) (Hugo)
- [Stripe Docs](https://stripe.com/docs) (Design inspiration)

### Tools
- [Broken Link Checker](https://github.com/linkchecker/linkcheck)
- [Vale Linter](https://vale.sh/) (Prose linting)
- [Grammarly](https://www.grammarly.com/) (Grammar checking)
- [Hemingway Editor](http://www.hemingwayapp.com/) (Readability)

## Contact

- **Project Lead**: Conduktor
- **Documentation**: GitHub Issues with `documentation` label
- **Community**: [Conduktor Slack](https://conduktor.io/slack)
- **Email**: support@conduktor.io

---

**Ready to start?** Run `mkdocs serve` and open `http://localhost:8000`
