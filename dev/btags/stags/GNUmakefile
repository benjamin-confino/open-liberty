MAKEFLAGS += --no-builtin-rules
.SUFFIXES:

# Delegate all targets to the parent btags/GNUmakefile.
# This lets 'cd btags/stags && make [clean]' work naturally.

.PHONY: stags btags clean tags ftags ctags mtags

stags btags tags ftags ctags mtags:
	@$(MAKE) --no-print-directory -C .. $@

clean:
	@$(MAKE) --no-print-directory -C .. clean-stags
