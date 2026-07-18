MAKEFLAGS += --no-builtin-rules
.SUFFIXES:

# Delegate all targets to the parent btags/GNUmakefile.
# This lets 'cd btags/ctags && make [clean]' work naturally.

.PHONY: ctags clean tags ftags stags mtags

ctags tags ftags stags mtags:
	@$(MAKE) --no-print-directory -C .. $@

clean:
	@$(MAKE) --no-print-directory -C .. clean-ctags
