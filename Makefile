.PHONY: test
run:
	clj -M:run experiment work/exp2
	clj -M:run evaluate work/exp2
	clj -M:run report work/exp2

test:
	clj -X:test


pack:
	(for i in README.md deps.edn Makefile model-names.txt ;\
	   do echo $$i; cat $$i; echo ---- ; done ;\
  echo evaluation-prompt.md; echo -----; \
  cat prompts/evaluation-prompt.md ; \
	echo Source files; echo -----; \
	cat src/failter/*.clj ) >~/failter-pack.txt

