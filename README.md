# Commas

A Scala compiler plugin that adds support for trailing commas.

### Usage

_coming soon_

### Why?

This is currently a proof of concept, for fun.

### How?

The plugin hijacks the parser phase and replaces it with
a customized parser that allows trailing commas in some
scenarios. The hijacking is done with a mixture of reflection
and carefully overridden fields.
Paul Phillips' [fork of Scala][policy] provided the basis for
the parser changes.

[policy]: https://github.com/paulp/policy/commit/ead099046c6d2ad2544e494d6cfd091ff7fa33ec

