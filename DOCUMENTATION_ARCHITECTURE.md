# Documentation Website Architecture

## Recommendation: MkDocs with Material Theme

### Why MkDocs Material?

**Primary Choice for Technical Documentation:**

1. **Technical Excellence**
   - Built specifically for technical documentation
   - Superior code highlighting (Java, JSON, bash)
   - Inline code annotations and admonitions
   - Tabbed content for multiple approaches

2. **Performance**
   - Static site generation (fast load times)
   - Instant search with Lunr.js
   - Mobile-first responsive design
   - Minimal JavaScript overhead

3. **Developer Experience**
   - Simple Markdown-based workflow
   - Live preview during development
   - Easy versioning support
   - Git-friendly (plain text files)

4. **GitHub Pages Integration**
   - One-command deployment (`mkdocs gh-deploy`)
   - Automatic build and push
   - Custom domain support
   - Zero configuration required

5. **Search & Navigation**
   - Built-in search with no external dependencies
   - Instant client-side search
   - Section anchors
   - Table of contents auto-generation

6. **Visual Design**
   - Material Design principles
   - Professional, minimalist aesthetic (Apple/Stripe style)
   - Color-coded elements
   - Dark mode support

### Comparison with Alternatives

| Feature | MkDocs Material | Docusaurus | Jekyll | VitePress |
|---------|----------------|------------|--------|-----------|
| Setup Time | ⭐⭐⭐⭐⭐ (5 min) | ⭐⭐⭐ (15 min) | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Build Speed | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Tech Docs | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| Search | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| GitHub Pages | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Maintenance | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| Java Ecosystem | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |

**Decision: MkDocs Material** wins for this Kafka connector project due to optimal balance of features, simplicity, and technical documentation capabilities.

## Information Architecture

### Site Structure

```
/
├── Home (Overview)
├── Getting Started
│   ├── Prerequisites
│   ├── Installation
│   └── Quick Start
├── Configuration
│   ├── Required Parameters
│   ├── Optional Parameters
│   └── Examples
├── Use Cases
│   ├── Binance Crypto Streams
│   ├── Coinbase Order Books
│   ├── WebSocket Echo
│   └── Authenticated APIs
├── Operations
│   ├── Deployment
│   ├── Monitoring & Metrics
│   ├── Health Checks
│   └── Troubleshooting
├── Architecture
│   ├── Components
│   ├── Data Flow
│   └── Limitations
├── Data Reliability
│   ├── At-Most-Once Semantics
│   ├── Data Loss Scenarios
│   └── Mitigation Strategies
├── API Reference
│   └── Configuration Properties
├── Development
│   ├── Building from Source
│   ├── Testing
│   └── Contributing
└── FAQ
```

### User Journey Optimization

**Primary Personas:**

1. **New User Journey** (Platform Engineer setting up first time)
   - Landing → Quick Start → Installation → First Example → Troubleshooting
   - Goal: Working connector in < 30 minutes

2. **Production Operator Journey** (DevOps/SRE managing production)
   - Landing → Monitoring → Health Checks → Troubleshooting → Data Reliability
   - Goal: Understand failure modes and monitoring

3. **Integration Developer Journey** (Data Engineer integrating specific API)
   - Landing → Use Cases → Configuration → API Reference
   - Goal: Find configuration for specific WebSocket API

### Navigation Design

**Top Navigation:**
- Getting Started
- Configuration
- Operations
- Architecture
- API Reference

**Sidebar Navigation:**
- Hierarchical sections (max 3 levels)
- Collapsible sections for long pages
- Search bar (sticky)
- Version selector (future)

**Footer:**
- GitHub repository link
- Issue tracker
- License
- Conduktor contact

### Content Organization Principles

1. **Progressive Disclosure**
   - Quick Start first (minimal configuration)
   - Advanced features later (authentication, custom headers)
   - Deep dive into internals last (architecture, limitations)

2. **Task-Oriented**
   - Each page answers: "How do I..."
   - Configuration examples over parameter tables
   - Troubleshooting by symptom, not by cause

3. **Code-First**
   - Show working examples immediately
   - Explain configuration after showing it works
   - Runnable commands (copy-paste ready)

4. **Visible Warnings**
   - Data loss warnings prominently displayed
   - At-most-once semantics explained early
   - Security considerations highlighted

## Design System

### Color Palette

**Primary Colors** (Kafka/Apache branding):
- Primary: `#231F20` (Kafka black)
- Accent: `#231F20` (Apache red for CTAs)
- Secondary: `#6B7280` (Gray for secondary text)

**Status Colors**:
- Success: `#10B981` (Green - running)
- Warning: `#F59E0B` (Orange - degraded)
- Error: `#EF4444` (Red - failed)
- Info: `#3B82F6` (Blue - informational)

**Code Theme**:
- Light mode: GitHub light
- Dark mode: Dracula or Nord

### Typography

- **Headings**: Inter or Roboto (sans-serif)
- **Body**: Inter or system UI fonts
- **Code**: JetBrains Mono or Fira Code (monospace)

### Component Patterns

**Admonitions** (using MkDocs Material):
```markdown
!!! warning "Data Loss Risk"
    Messages in the queue are lost on connector shutdown.

!!! tip "Performance Tip"
    Increase queue size for high-throughput streams.

!!! danger "Security Warning"
    Never commit auth tokens to version control.
```

**Tabbed Examples**:
```markdown
=== "Binance"
    ```json
    {"method": "SUBSCRIBE", ...}
    ```

=== "Coinbase"
    ```json
    {"type": "subscribe", ...}
    ```
```

**Code Annotations**:
```java
WebSocketClient client = new WebSocketClient(
    url,           // (1)
    headers,       // (2)
    authToken      // (3)
);
```
1. WebSocket endpoint (ws:// or wss://)
2. Custom headers for authentication
3. Bearer token (optional)

## Search Strategy

### Built-in Search (Lunr.js)

**Indexed Content:**
- All page titles and headings
- First paragraph of each section
- Code comments
- Configuration parameter names

**Search Optimizations:**
- Boost page titles (weight: 3x)
- Boost headings (weight: 2x)
- Index configuration property names
- Include common synonyms (e.g., "ws" → "WebSocket")

**No External Dependencies:**
- No Algolia (avoids vendor lock-in)
- No Google Custom Search (privacy concerns)
- Pure client-side search (no backend required)

## Mobile Responsiveness

**Breakpoints:**
- Mobile: < 768px (single column, hamburger menu)
- Tablet: 768-1024px (collapsible sidebar)
- Desktop: > 1024px (full sidebar + content)

**Mobile Optimizations:**
- Sticky header with search
- Collapsible code blocks (long JSON configs)
- Horizontal scroll for tables
- Touch-friendly navigation

## Deployment Strategy

### GitHub Actions Workflow

**Automatic Deployment:**
- Trigger: Push to `main` branch
- Build: MkDocs build process
- Deploy: Push to `gh-pages` branch
- URL: `https://<username>.github.io/kafka-connect-websocket/`

**Custom Domain (Optional):**
- Configure: `docs.conduktor.io/kafka-connect-websocket`
- CNAME file in `docs/` directory
- DNS A record pointing to GitHub Pages IPs

### Build Process

```bash
# Local development
mkdocs serve

# Build for production
mkdocs build

# Deploy to GitHub Pages
mkdocs gh-deploy
```

## Content Migration Plan

### Phase 1: Core Documentation (Week 1)
- [x] Home page (overview, features)
- [x] Quick Start (prerequisites, installation, first connector)
- [x] Configuration reference (all parameters)
- [x] Basic troubleshooting (connection issues)

### Phase 2: Operations & Monitoring (Week 2)
- [ ] Monitoring setup (JMX, Prometheus, Grafana)
- [ ] Health checks and alerting
- [ ] Advanced troubleshooting (queue overflow, SSL, auth)
- [ ] Data reliability guide (data loss scenarios)

### Phase 3: Use Cases & Examples (Week 3)
- [ ] Binance integration guide
- [ ] Coinbase integration guide
- [ ] Generic authenticated API guide
- [ ] Custom WebSocket implementation

### Phase 4: Polish & Launch (Week 4)
- [ ] Architecture deep dive
- [ ] Development guide (building, testing, contributing)
- [ ] FAQ compilation
- [ ] SEO optimization (meta tags, sitemap)

## Analytics & Feedback

### GitHub Issues Integration
- "Report Issue" link on every page
- Pre-filled issue template with page URL
- Label: `documentation`

### User Feedback Widget
- Simple thumbs up/down at page bottom
- "Was this page helpful?" prompt
- Links to GitHub Issues for detailed feedback

### Analytics (Optional)
- Google Analytics or Plausible (privacy-friendly)
- Track most visited pages
- Monitor search queries
- Identify documentation gaps

## Versioning Strategy

**Initial Release (v1.0.0):**
- No version selector initially
- Document version in footer: "Documentation for v1.0.0"
- Prepare for future versions with Mike (MkDocs versioning plugin)

**Future Versions:**
- Use Mike for version management
- URL structure: `/latest/`, `/1.0/`, `/1.1/`
- Version selector in header
- Version warning for outdated docs

## Success Metrics

**Quantitative:**
- Time to first successful connector (target: < 30 min)
- Search success rate (target: > 80% find answer)
- Documentation completeness (target: 100% config coverage)
- Build time (target: < 5 seconds)

**Qualitative:**
- Reduced GitHub issues asking basic questions
- Positive feedback on documentation clarity
- Community contributions to docs
- Adoption by external users

## Maintenance Plan

**Regular Updates:**
- Review and update examples quarterly
- Test all commands and configurations
- Update screenshots if UI changes
- Refresh external links

**Community Contributions:**
- Accept documentation PRs
- Maintain CONTRIBUTING.md for docs
- Style guide for consistency
- Review process for accuracy

## Next Steps

1. **Setup** (Day 1):
   - Install MkDocs Material
   - Create `mkdocs.yml` configuration
   - Set up docs directory structure
   - Configure GitHub Actions workflow

2. **Content Migration** (Days 2-5):
   - Extract content from README.md
   - Organize into logical sections
   - Add code syntax highlighting
   - Create navigation structure

3. **Design & Polish** (Days 6-7):
   - Apply color scheme
   - Add admonitions and callouts
   - Optimize for mobile
   - Test search functionality

4. **Deploy & Launch** (Day 8):
   - Deploy to GitHub Pages
   - Test all links and examples
   - Announce to users
   - Gather initial feedback
