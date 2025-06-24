.PHONY: test
run:
	clojure -M:run

test:
	clj -X:test


pack:
	(for i in README.md config.edn deps.edn Makefile ;\
	   do echo $$i; cat $$i; echo ---- ; done ;\
	echo Source files; echo -----; \
	cat src/failter/*.clj ) >~/failter-pack.txt

