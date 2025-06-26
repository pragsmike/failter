# Clean Blog Text

TASK: Remove promotional content from this blog post. Keep only the main article text.

## DELETE these patterns:

1. **Images**: `![](any-url)` 
2. **Post previews**: Any block with "Read more X ago · Y likes · Z comments"
3. **Subscribe prompts**: "Upgrade to paid", "become a subscriber", etc.
4. **Social stuff**: Lines starting with # or ending with emojis like 🇺🇸🏴‍☠️
5. **Empty links**: `[](url)` with no text
6. **Credits**: "h/t [name]" lines

## KEEP everything else:
- All paragraph text that tells the story
- Links that explain things: `[UFO cult](wikipedia-link)`

## Example of BAD content to DELETE:
```
[](https://link.com)
Publication Name  
Article Title
Preview text here...
Read more 2 years ago · 15 likes · 3 comments · Author Name
```

Return only the cleaned text. No explanations.

---

**INPUT TEXT TO CLEAN:**

{{INPUT_TEXT}}
