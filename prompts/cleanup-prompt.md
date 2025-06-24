# Markdown Blog Cleanup Task

You are a text processing specialist. Your task is to clean up markdown blog posts by removing non-narrative "pollution" while preserving the core article content and YAML frontmatter.

## PRESERVE THESE ELEMENTS:
- **YAML frontmatter** (everything between the opening and closing `---` markers) - NEVER remove this
- **Main narrative text** - the actual article content
- **Legitimate hyperlinks** that are part of the narrative flow (like references to external sources, Wikipedia links, etc.)
## REMOVE THESE POLLUTION ELEMENTS:

### 1. Embedded Images
- Remove all markdown image references: `![](any-url-here)`
- These typically have long AWS bucket URLs or similar

### 2. Substack/Blog Post Embeds (PRIORITY REMOVAL)
- Remove embedded post previews that follow this pattern:
  ```
  [](link-url)
  Publication Name
  Post Title
  Post excerpt text...
  Read more [time] ago · [number] likes · [number] comments · Author Name
  ```
- These are the largest source of pollution
- Look for text blocks that contain "Read more", engagement metrics (likes/comments), and author attributions

### 3. Subscription/Monetization Elements
- "Upgrade to paid" calls-to-action
- Reader-supported publication footers
- Any "consider becoming a subscriber" text

### 4. Social Media Elements
- Hashtags (lines starting with #)
- Social media endings like "LFG. 🇺🇸🏴‍☠️"
- Attribution lines like "h/t [Name](url)"

### 5. Empty or Standalone Links
- Lines with just `[](url)` and no descriptive text
- Lines containing only URLs

## FORMATTING CLEANUP:
- Remove excessive whitespace (more than 2 consecutive newlines)
- Remove trailing spaces on lines
- Ensure clean paragraph separation

## DECISION GUIDELINES:
- **When in doubt about a link**: If it adds context to the narrative, keep it. If it's promotional/cross-referential to other blog posts, remove it.
- **For embedded content**: If it reads like a preview/summary of another article with engagement metrics, remove it entirely.
- **For subscription text**: Remove anything that asks readers to subscribe, upgrade, or support the publication.

## OUTPUT FORMAT:
Provide only the cleaned markdown text. Do not explain what you removed or provide commentary - just output the clean version.

---

**INPUT TEXT TO CLEAN:**

{{INPUT_TEXT}}
