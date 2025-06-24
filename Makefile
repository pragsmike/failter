.PHONY: test
run:
	clj -M:run /tmp/a.md /tmp/b.md && cat /tmp/b.md

test:
	clj -X:test


pack:
	(for i in README.md deps.edn Makefile ;\
	   do echo $$i; cat $$i; echo ---- ; done ;\
  echo cleanup-prompt.md; echo -----; \
  cat prompts/cleanup-prompt.md ; \
	echo Source files; echo -----; \
	cat src/failter/*.clj ) >~/failter-pack.txt

