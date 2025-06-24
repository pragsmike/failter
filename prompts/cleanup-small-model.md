# Markdown Blog Cleanup Task

TASK: Remove blog pollution from markdown text. Keep YAML frontmatter and main article text. Remove promotional content.

SIMPLE RULES:

## KEEP:
- YAML frontmatter (between --- markers) - NEVER remove
- Main article paragraphs
- Links that explain things in the article

## DELETE:
1. Images: `![](any-url)`
2. Post previews with "Read more X ago · Y likes"
3. "Upgrade to paid" or "Subscribe" text
4. Hashtags (#hashtag)
5. Lines with just `[](url)`
6. "h/t [name]" attribution lines

EXAMPLE of what to DELETE:
```
[](https://example.com)
Publication Name
Article Title
Some preview text...
Read more 3 years ago · 23 likes · 2 comments · Author
```

OUTPUT: Just return the cleaned text. No explanations.

---

**INPUT TEXT TO CLEAN:**

{{INPUT_TEXT}}
