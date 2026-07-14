# eme-server


Instsructions for initializing Project Only (New Client)
---
Add SubModules:
git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-finder.git plugins/finder
---

Deleting Submodules
---
git submodule deinit -f plugins/eme-lib
git rm --cached -r plugins/eme-lib
*Manually delete Plugin entry in .gitmodules
---