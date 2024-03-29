# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## 1.0.0 - 2022-03-28

### Added
- Basic web page to use application for simple searches without REST client
- Separate REST API endpoint for raw lucene query text

### Changed
- Previous toplevel result map changed (again) back into list
- Search results should now return in order of relevance once again

## 0.9.0 - 2022-03-25

### Added
- Documentation for quick-start, install, and typical API usage

### Changed
- Migrated from SymSpell to Lucene using Jena's inbuilt fulltext search
- Edit distance no longer configurable

### Removed
- SymSpell dependency
## 0.1.0 - 2020-08-08
### Added
- First rough version.
- Mount/ring/reitit-based stack.
- SymSpell implementation by [@MighTguY ](https://github.com/MighTguY/customized-symspell/)

[Unreleased]: https://github.com/AberystwythSystemsBiology/sog/compare/v0.1.0...HEAD
[0.9.0]: https://github.com/AberystwythSystemsBiology/sog/releases/tag/v0.9.0
[0.1.0]: https://github.com/AberystwythSystemsBiology/sog/releases/tag/v0.1.0
