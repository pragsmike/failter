.PHONY: test run pack test-e2e clean-e2e

# Default run target now uses the new 'run' command with a spec file.
run:
	clj -M:run run --spec work/exp2/spec.yml

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
TEST_E2E_DIR := work/e2e-run-test
# Use a fast, local model for the test
TEST_E2E_MODEL := ollama/qwen3:1.7b

test-e2e:
	@echo "--- Running End-to-End Test ---"
	# Clean up any previous run
	rm -rf $(TEST_E2E_DIR)
	# 1. Create test directory structure
	@echo "--> Setting up test directory..."
	mkdir -p $(TEST_E2E_DIR)/inputs $(TEST_E2E_DIR)/templates $(TEST_E2E_DIR)/artifacts
	# 2. Create test files
	echo "This is a simple test input." > $(TEST_E2E_DIR)/inputs/input1.txt
	echo "The prompt body is: {{INPUT_TEXT}}" > $(TEST_E2E_DIR)/templates/P1.md
	# 3. Create the spec file that defines the run
	@echo "--> Creating spec file..."
	@echo "version: 2" > $(TEST_E2E_DIR)/spec.yml
	@echo "inputs_dir: '$(abspath $(TEST_E2E_DIR)/inputs)'" >> $(TEST_E2E_DIR)/spec.yml
	@echo "templates_dir: '$(abspath $(TEST_E2E_DIR)/templates)'" >> $(TEST_E2E_DIR)/spec.yml
	@echo "artifacts_dir: '$(abspath $(TEST_E2E_DIR)/artifacts)'" >> $(TEST_E2E_DIR)/spec.yml
	@echo "templates: ['P1.md']" >> $(TEST_E2E_DIR)/spec.yml
	@echo "models: ['$(TEST_E2E_MODEL)']" >> $(TEST_E2E_DIR)/spec.yml
	@echo "judge_model: '$(TEST_E2E_MODEL)'" >> $(TEST_E2E_DIR)/spec.yml
	@echo "retries: 1" >> $(TEST_E2E_DIR)/spec.yml
	# 4. Run the full pipeline with the single 'run' command
	@echo "\n--> Running Failter..."
	clj -M:run run --spec $(TEST_E2E_DIR)/spec.yml
	# 5. Display the final report from stdout for easy verification
	@echo "\n--- End-to-End Test Complete ---"
	@echo "Run 'make clean-e2e' to remove the test directory."

clean-e2e:
	@echo "--- Cleaning up E2E test directory ---"
	rm -rf $(TEST_E2E_DIR)
