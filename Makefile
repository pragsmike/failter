.PHONY: test run pack test-e2e clean-e2e

run:
	clj -M:run experiment work/exp2
	clj -M:run evaluate work/exp2
	clj -M:run report work/exp2

test:
	clj -X:test


# Create a pack file to upload to AI assistant
pack:
	(for i in README.md USAGE.md \
				docs/DESIGN.md \
			  deps.edn \
				Makefile \
				model-names.txt \
				copilot/onboard-*.md \
		; do echo $$i; cat $$i; echo ---- ; done ;\
  echo evaluation-prompt.md; echo -----; \
  cat prompts/evaluation-prompt.md ; \
  echo evaluation-prompt-gt.md; echo -----; \
  cat prompts/evaluation-prompt-gt.md ; \
	echo Source files; echo -----; \
	cat src/failter/*.clj ; cat test/failter/*.clj) >~/failter-pack.txt

# --- End-to-End Test Target ---
TEST_E2E_DIR := work/e2e-fm-test
# Use a fast, local model for the test
TEST_E2E_MODEL := ollama/qwen3:1.7b

test-e2e:
	@echo "--- Running End-to-End Test ---"
	# Clean up any previous run
	rm -rf $(TEST_E2E_DIR)
	# 1. Create test directory structure
	@echo "--> Setting up test directory..."
	mkdir -p $(TEST_E2E_DIR)/inputs $(TEST_E2E_DIR)/templates
	# 2. Create test files
	echo "This is a simple test input." > $(TEST_E2E_DIR)/inputs/input1.txt
	echo "$(TEST_E2E_MODEL)" > $(TEST_E2E_DIR)/model-names.txt
	echo "---\nid: P999\ndescription: A test prompt with frontmatter.\n---\nThe prompt body started. The original text was: {{INPUT_TEXT}}" > $(TEST_E2E_DIR)/templates/prompt-with-fm.md
	echo "This is a legacy prompt. The original text was: {{INPUT_TEXT}}" > $(TEST_E2E_DIR)/templates/prompt-no-fm.md
	# 3. Run the full pipeline
	@echo "\n--> Running experiment..."
	clj -M:run experiment $(TEST_E2E_DIR)
	@echo "\n--> Evaluating results..."
	# FIX: Use the same local model as the judge to make the test self-contained
	clj -M:run evaluate $(TEST_E2E_DIR) --judge-model $(TEST_E2E_MODEL)
	@echo "\n--> Generating report..."
	clj -M:run report $(TEST_E2E_DIR)
	# 4. Display the final report for easy verification
	@echo "\n--- Final Report ---"
	@cat $(TEST_E2E_DIR)/report.md
	@echo "\n--- End-to-End Test Complete ---"
	@echo "Run 'make clean-e2e' to remove the test directory."

clean-e2e:
	@echo "--- Cleaning up E2E test directory ---"
	rm -rf $(TEST_E2E_DIR)





