MAKEFLAGS += --no-builtin-rules
.SUFFIXES:

# Delegate all targets to the parent btags/GNUmakefile.
# This lets 'cd btags/ftags && make [clean]' work naturally.

.PHONY: ftags clean tags ctags stags mtags

ftags tags ctags stags mtags:
	@$(MAKE) --no-print-directory -C .. $@

clean:
	@$(MAKE) --no-print-directory -C .. clean-ftags
