// Dumps TextMate tokens using the engine bundled inside VS Code (no npm install needed).
// Usage: test/run.sh, or: ELECTRON_RUN_AS_NODE=1 "/Applications/Visual Studio Code.app/Contents/MacOS/Code" test/tokenize.js <grammar.json> <file> [--errors]
const fs = require('fs');
const APP = '/Applications/Visual Studio Code.app/Contents/Resources/app/';
const tm = require(APP + 'node_modules.asar/vscode-textmate');
const onig = require(APP + 'node_modules.asar/vscode-oniguruma');
const [grammarPath, file, flag] = process.argv.slice(2);

(async () => {
	const wasm = fs.readFileSync(APP + 'node_modules.asar.unpacked/vscode-oniguruma/release/onig.wasm').buffer;
	await onig.loadWASM(wasm);
	const registry = new tm.Registry({
		onigLib: Promise.resolve({ createOnigScanner: (p) => new onig.OnigScanner(p), createOnigString: (s) => new onig.OnigString(s) }),
		loadGrammar: async () => tm.parseRawGrammar(fs.readFileSync(grammarPath, 'utf8'), grammarPath),
	});
	const grammar = await registry.loadGrammar('source.velocity-json');
	let state = tm.INITIAL;
	const lines = fs.readFileSync(file, 'utf8').split('\n');
	let suspicious = 0;
	lines.forEach((line, i) => {
		const r = grammar.tokenizeLine(line, state);
		state = r.ruleStack;
		for (const t of r.tokens) {
			const text = line.substring(t.startIndex, t.endIndex);
			const scopes = t.scopes.slice(1).join(' ');
			// "suspicious" = non-whitespace text that only got the root scope (nothing recognized it)
			const bad = text.trim() && t.scopes.length === 1;
			if (bad) suspicious++;
			if (flag !== '--errors' || bad) console.log(`${i + 1}\t${JSON.stringify(text)}\t${scopes}`);
		}
	});
	// Depth of the rule stack at EOF: >1 means a begin/end rule never closed
	let depth = 0; for (let s = state; s && s.parent; s = s.parent) depth++;
	console.error(`unscoped tokens: ${suspicious}, open rules at EOF: ${depth}`);
})();
