# Documentation Development Guide

This directory contains the source files for the Kafka Connect WebSocket connector documentation website.

## Technology Stack

- **Static Site Generator**: [MkDocs](https://www.mkdocs.org/)
- **Theme**: [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/)
- **Deployment**: GitHub Pages via GitHub Actions
- **Search**: Built-in Lunr.js (client-side)

## Local Development

### Prerequisites

- Python 3.7+
- pip (Python package manager)

### Setup

1. **Install dependencies:**

```bash
pip install -r requirements.txt
```

Or install individually:

```bash
pip install mkdocs-material mkdocs-minify-plugin mkdocs-redirects mkdocs-git-revision-date-localized-plugin
```

2. **Start development server:**

```bash
mkdocs serve
```

The documentation will be available at `http://localhost:8000` with live reload.

### Development Workflow

1. Edit Markdown files in the `docs/` directory
2. Save changes (browser auto-refreshes)
3. Preview in browser at `http://localhost:8000`
4. Commit changes when satisfied

## Project Structure

```
docs/
├── index.md                    # Homepage
├── getting-started/
│   ├── index.md               # Getting Started overview
│   ├── prerequisites.md       # System requirements
│   ├── installation.md        # Installation guide
│   └── quick-start.md         # Quick start tutorial
├── configuration/
│   ├── index.md               # Configuration overview
│   ├── required-parameters.md # Required config
│   ├── optional-parameters.md # Optional config
│   └── examples.md            # Configuration examples
├── use-cases/
│   ├── index.md               # Use cases overview
│   ├── binance.md             # Binance integration
│   ├── coinbase.md            # Coinbase integration
│   ├── echo-server.md         # Testing with echo
│   └── authenticated-apis.md  # Auth examples
├── operations/
│   ├── index.md               # Operations overview
│   ├── deployment.md          # Deployment guide
│   ├── monitoring.md          # Monitoring setup
│   ├── health-checks.md       # Health checks
│   └── troubleshooting.md     # Troubleshooting guide
├── architecture/
│   ├── index.md               # Architecture overview
│   ├── components.md          # Component details
│   ├── data-flow.md           # Data flow diagrams
│   └── limitations.md         # Known limitations
├── reliability/
│   ├── index.md               # Reliability overview
│   ├── semantics.md           # At-most-once semantics
│   ├── data-loss.md           # Data loss scenarios
│   └── mitigation.md          # Mitigation strategies
├── api/
│   ├── index.md               # API reference overview
│   └── configuration-properties.md # Config reference
├── development/
│   ├── index.md               # Development overview
│   ├── building.md            # Building from source
│   ├── testing.md             # Testing guide
│   └── contributing.md        # Contribution guide
├── stylesheets/
│   └── extra.css              # Custom CSS
├── javascripts/
│   └── extra.js               # Custom JavaScript
├── faq.md                     # FAQ
└── changelog.md               # Changelog
```

## Writing Guidelines

### Markdown Syntax

Use standard Markdown with [Material extensions](https://squidfunk.github.io/mkdocs-material/reference/):

#### Admonitions

```markdown
!!! note "Optional Title"
    This is a note admonition.

!!! tip
    This is a tip without custom title.

!!! warning
    This is a warning.

!!! danger
    This is a danger alert.
```

#### Code Blocks with Tabs

```markdown
=== "Python"
    ```python
    print("Hello World")
    ```

=== "Java"
    ```java
    System.out.println("Hello World");
    ```
```

#### Code Annotations

```markdown
```java
WebSocketClient client = new WebSocketClient(
    url,           // (1)
    headers,       // (2)
    authToken      // (3)
);
```
1. WebSocket endpoint URL
2. Custom headers map
3. Bearer token for auth
```

#### Cards Grid

```markdown
<div class="grid cards" markdown>

- :material-icon:{ .lg .middle } **Title**

    ---

    Description here

- :material-icon:{ .lg .middle } **Title**

    ---

    Description here

</div>
```

### Content Guidelines

1. **User-Focused**: Write for users, not developers
2. **Task-Oriented**: Structure around "How do I..."
3. **Code-First**: Show working examples immediately
4. **Progressive**: Start simple, add complexity gradually
5. **Searchable**: Use clear headings and keywords

### Voice & Tone

- **Clear**: Use simple, direct language
- **Helpful**: Anticipate user questions
- **Professional**: Technical but approachable
- **Honest**: Acknowledge limitations openly

### Code Examples

- Use real, runnable examples
- Include full context (not just snippets)
- Show expected output
- Provide copy-paste commands

**Good Example:**

```markdown
```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @connector-config.json
```

Expected response:
```json
{
  "name": "websocket-connector",
  "config": {...},
  "tasks": [...]
}
```
```

## Building & Deployment

### Build Locally

```bash
# Build static site
mkdocs build

# Output will be in site/ directory
ls site/
```

### Deploy to GitHub Pages

**Automatic (via GitHub Actions):**

Push to `main` branch triggers automatic deployment.

**Manual:**

```bash
mkdocs gh-deploy --force
```

This builds the site and pushes to the `gh-pages` branch.

### Custom Domain (Optional)

1. Add `CNAME` file to `docs/` directory:
   ```
   docs.example.com
   ```

2. Configure DNS A records:
   ```
   185.199.108.153
   185.199.109.153
   185.199.110.153
   185.199.111.153
   ```

3. Enable in GitHub Settings → Pages → Custom domain

## Configuration

### mkdocs.yml

Main configuration file. Key sections:

```yaml
# Site metadata
site_name: Kafka Connect WebSocket Connector
site_url: https://conduktor.github.io/kafka-connect-websocket/

# Theme configuration
theme:
  name: material
  palette:
    - scheme: default
      primary: black
      accent: red

# Navigation structure
nav:
  - Home: index.md
  - Getting Started:
    - getting-started/index.md
    - ...
```

### Adding New Pages

1. **Create Markdown file** in appropriate directory:
   ```bash
   touch docs/operations/new-page.md
   ```

2. **Add to navigation** in `mkdocs.yml`:
   ```yaml
   nav:
     - Operations:
       - operations/index.md
       - operations/new-page.md  # Add here
   ```

3. **Write content** using Markdown

4. **Preview** with `mkdocs serve`

## Search Configuration

Search is automatic with Material theme. Optimize by:

- Using clear, descriptive headings
- Including keywords in first paragraph
- Adding relevant metadata
- Using consistent terminology

## Analytics (Optional)

Configure in `mkdocs.yml`:

```yaml
extra:
  analytics:
    provider: google
    property: G-XXXXXXXXXX
```

Or use privacy-friendly alternatives like Plausible.

## Versioning (Future)

Use [Mike](https://github.com/jimporter/mike) for version management:

```bash
pip install mike

# Deploy version
mike deploy --push --update-aliases 1.0 latest

# Set default version
mike set-default --push latest
```

## Troubleshooting

### "No module named 'mkdocs'"

Install dependencies:
```bash
pip install -r requirements.txt
```

### "Page not found" errors

Check navigation structure in `mkdocs.yml` matches file paths.

### Styles not applying

Clear browser cache or rebuild:
```bash
rm -rf site/
mkdocs build
```

### Search not working

Ensure `search` plugin is enabled in `mkdocs.yml`:
```yaml
plugins:
  - search
```

## Contributing

See parent [CONTRIBUTING.md](../CONTRIBUTING.md) for contribution guidelines.

For documentation-specific changes:

1. Follow writing guidelines above
2. Test locally with `mkdocs serve`
3. Submit PR with clear description
4. Request review from maintainers

## Resources

- [MkDocs Documentation](https://www.mkdocs.org/)
- [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/)
- [Markdown Guide](https://www.markdownguide.org/)
- [Material Icons](https://materialdesignicons.com/)

## Contact

- **Issues**: [GitHub Issues](https://github.com/conduktor/kafka-connect-websocket/issues)
- **Slack**: [Conduktor Community](https://conduktor.io/slack)
