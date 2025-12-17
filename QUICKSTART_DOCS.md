# Quick Start: Documentation Website

Get your documentation site running in 5 minutes.

## Step 1: Install Dependencies

```bash
# Install Python dependencies
pip install -r requirements.txt
```

Expected output:
```
Successfully installed mkdocs-1.5.3 mkdocs-material-9.5.0 ...
```

## Step 2: Start Local Server

```bash
# From project root
mkdocs serve
```

Expected output:
```
INFO    -  Building documentation...
INFO    -  Cleaning site directory
INFO    -  Documentation built in 0.52 seconds
INFO    -  [10:30:45] Watching paths for changes: 'docs', 'mkdocs.yml'
INFO    -  [10:30:45] Serving on http://127.0.0.1:8000/
```

## Step 3: View Documentation

Open your browser to:

**http://localhost:8000**

You should see the documentation homepage with:
- Hero section
- Feature cards
- Quick example with tabbed content
- Call-to-action buttons

## Step 4: Make Changes

Edit any file in `docs/` directory:

```bash
# Example: Edit homepage
open docs/index.md
# or
code docs/index.md
```

**Save the file** → Browser auto-refreshes with changes!

## Step 5: Deploy to GitHub Pages

### Option A: Automatic (Recommended)

```bash
# Commit and push to main branch
git add .
git commit -m "Add documentation website"
git push origin main
```

GitHub Actions will automatically:
1. Build the site
2. Deploy to GitHub Pages
3. Make it live at: `https://[username].github.io/kafka-connect-websocket/`

### Option B: Manual

```bash
# Build and deploy manually
mkdocs gh-deploy --force
```

## What's Included

### ✅ Already Created

- **Configuration**: `mkdocs.yml` (complete MkDocs setup)
- **Workflow**: `.github/workflows/docs.yml` (automatic deployment)
- **Homepage**: `docs/index.md` (hero, features, examples)
- **Getting Started**: `docs/getting-started/index.md` and `prerequisites.md`
- **FAQ**: `docs/faq.md` (comprehensive FAQ)
- **Changelog**: `docs/changelog.md` (v1.0.0 documented)
- **Custom CSS**: `docs/stylesheets/extra.css` (professional styling)
- **Custom JS**: `docs/javascripts/extra.js` (enhanced features)

### ⏳ To Be Created

Extract content from `README.md` into these pages:

**Getting Started**:
- `docs/getting-started/installation.md` (extract from README lines 24-118)
- `docs/getting-started/quick-start.md` (extract from README lines 119-238)

**Configuration**:
- `docs/configuration/index.md` (extract from README lines 119-139)
- `docs/configuration/required-parameters.md`
- `docs/configuration/optional-parameters.md`
- `docs/configuration/examples.md` (extract from README lines 140-207)

**Use Cases**:
- `docs/use-cases/index.md`
- `docs/use-cases/binance.md` (extract from README lines 159-174)
- `docs/use-cases/coinbase.md` (extract from README lines 176-189)
- `docs/use-cases/echo-server.md` (extract from README lines 143-157)
- `docs/use-cases/authenticated-apis.md` (extract from README lines 192-207)

**Operations**:
- `docs/operations/index.md`
- `docs/operations/deployment.md` (extract from README lines 209-238)
- `docs/operations/monitoring.md` (extract from README lines 239-651)
- `docs/operations/health-checks.md` (extract from README lines 609-645)
- `docs/operations/troubleshooting.md` (extract from README lines 875-1331)

**Architecture**:
- `docs/architecture/index.md`
- `docs/architecture/components.md` (extract from README lines 652-660)
- `docs/architecture/data-flow.md` (extract from README lines 661-675)
- `docs/architecture/limitations.md` (extract from README lines 676-684)

**Data Reliability**:
- `docs/reliability/index.md`
- `docs/reliability/semantics.md` (extract from README lines 685-725)
- `docs/reliability/data-loss.md` (extract from README lines 726-828)
- `docs/reliability/mitigation.md` (extract from README lines 829-873)

**API Reference**:
- `docs/api/index.md`
- `docs/api/configuration-properties.md` (extract from README lines 119-139)

**Development**:
- `docs/development/index.md`
- `docs/development/building.md` (extract from README lines 24-30)
- `docs/development/testing.md` (extract from README lines 1332-1345)
- `docs/development/contributing.md` (extract from README lines 1362-1368)

## Directory Structure

```
kafka-connect-websocket/
├── mkdocs.yml                 ← Main configuration
├── requirements.txt           ← Python dependencies
├── docs/                      ← All documentation files
│   ├── index.md              ← Homepage ✅
│   ├── faq.md                ← FAQ ✅
│   ├── changelog.md          ← Changelog ✅
│   ├── getting-started/
│   │   ├── index.md          ← Overview ✅
│   │   ├── prerequisites.md  ← Prerequisites ✅
│   │   ├── installation.md   ← To create ⏳
│   │   └── quick-start.md    ← To create ⏳
│   ├── configuration/        ← To create ⏳
│   ├── use-cases/            ← To create ⏳
│   ├── operations/           ← To create ⏳
│   ├── architecture/         ← To create ⏳
│   ├── reliability/          ← To create ⏳
│   ├── api/                  ← To create ⏳
│   ├── development/          ← To create ⏳
│   ├── stylesheets/
│   │   └── extra.css         ← Custom CSS ✅
│   └── javascripts/
│       └── extra.js          ← Custom JS ✅
└── .github/
    └── workflows/
        └── docs.yml          ← GitHub Actions ✅
```

## Navigation Features

### Search
- Press `/` or click search icon
- Instant search with suggestions
- Highlighted results

### Keyboard Shortcuts
- `Alt + S`: Focus search
- `Alt + H`: Go to home
- `Esc`: Close search

### Mobile
- Responsive design
- Hamburger menu on mobile
- Touch-friendly navigation

### Dark Mode
- Automatic based on OS preference
- Toggle in header (sun/moon icon)

## Customization

### Change Colors

Edit `mkdocs.yml`:

```yaml
theme:
  palette:
    primary: black    # Change to blue, red, green, etc.
    accent: red       # Change accent color
```

### Change Logo

Add logo to `docs/` and update `mkdocs.yml`:

```yaml
theme:
  logo: assets/logo.png
  favicon: assets/favicon.ico
```

### Add Pages

1. Create file: `docs/new-page.md`
2. Add to navigation in `mkdocs.yml`:

```yaml
nav:
  - New Page: new-page.md
```

## Troubleshooting

### "Module not found"

```bash
pip install -r requirements.txt
```

### Port already in use

```bash
# Use different port
mkdocs serve -a localhost:8001
```

### Changes not showing

```bash
# Hard refresh browser
# Mac: Cmd + Shift + R
# Windows/Linux: Ctrl + Shift + F5
```

### Build errors

```bash
# Clean build
rm -rf site/
mkdocs build --verbose
```

## Next Steps

### Immediate
1. Run `mkdocs serve`
2. View at `http://localhost:8000`
3. Explore the existing pages

### Short-term
1. Create missing pages (see list above)
2. Extract content from README.md
3. Add diagrams and screenshots

### Long-term
1. Push to GitHub
2. Enable GitHub Pages
3. Share documentation URL

## Documentation Files

Read these for detailed information:

- **DOCUMENTATION_ARCHITECTURE.md** - Architecture recommendation and rationale
- **DOCS_SETUP_GUIDE.md** - Complete setup and maintenance guide
- **DOCUMENTATION_SUMMARY.md** - Executive summary and status
- **docs/README.md** - Documentation development guide

## Help & Support

- **MkDocs Docs**: https://www.mkdocs.org/
- **Material Theme**: https://squidfunk.github.io/mkdocs-material/
- **GitHub Issues**: Open issue with `documentation` label

---

**Ready?** Run this now:

```bash
pip install -r requirements.txt && mkdocs serve
```

Then open: **http://localhost:8000**
