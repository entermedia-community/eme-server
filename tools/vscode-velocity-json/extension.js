// Render-and-parse diagnostics for Velocity JSON templates.
//
// Runs tools/velocity-json-check/check.sh (found by walking up from the file) as a long-lived process per
// eme-server checkout, sends it a file path on open/save, and shows what it reports. The checker reads the
// file from disk, so results reflect the last save.
'use strict';

const vscode = require('vscode');
const cp = require('child_process');
const fs = require('fs');
const path = require('path');

const CHECKER = path.join('tools', 'velocity-json-check', 'check.sh');

/** @type {vscode.DiagnosticCollection} */
let diagnostics;
/** @type {vscode.OutputChannel} */
let output;
/** server root -> CheckerProcess */
const checkers = new Map();
/** file path -> pending timer */
const timers = new Map();

function activate(context) {
	diagnostics = vscode.languages.createDiagnosticCollection('velocity-json');
	output = vscode.window.createOutputChannel('Velocity JSON');
	context.subscriptions.push(diagnostics, output);

	context.subscriptions.push(
		vscode.workspace.onDidOpenTextDocument((doc) => schedule(doc, 0)),
		vscode.workspace.onDidSaveTextDocument((doc) => schedule(doc, 150)),
		vscode.workspace.onDidCloseTextDocument((doc) => diagnostics.delete(doc.uri)),
		vscode.workspace.onDidChangeConfiguration((e) => {
			if (e.affectsConfiguration('velocityJson')) {
				vscode.workspace.textDocuments.forEach((doc) => schedule(doc, 0));
			}
		}),
		vscode.commands.registerCommand('velocityJson.checkAll', checkAll),
		{ dispose: () => checkers.forEach((c) => c.dispose()) }
	);

	vscode.workspace.textDocuments.forEach((doc) => schedule(doc, 0));
}

function deactivate() {
	checkers.forEach((c) => c.dispose());
	checkers.clear();
}

function config() {
	const c = vscode.workspace.getConfiguration('velocityJson.renderCheck');
	return { enabled: c.get('enabled', true), hints: c.get('showUnescapedHints', true) };
}

/** The eme-server checkout containing this file (the folder that has tools/velocity-json-check/check.sh). */
function findRoot(file) {
	let dir = path.dirname(file);
	while (true) {
		if (fs.existsSync(path.join(dir, CHECKER))) {
			return dir;
		}
		const parent = path.dirname(dir);
		if (parent === dir) {
			return null;
		}
		dir = parent;
	}
}

function schedule(doc, delay) {
	if (doc.languageId !== 'velocity-json' || doc.uri.scheme !== 'file') {
		return;
	}
	if (!config().enabled) {
		diagnostics.delete(doc.uri);
		return;
	}
	const file = doc.uri.fsPath;
	clearTimeout(timers.get(file));
	timers.set(
		file,
		setTimeout(() => {
			timers.delete(file);
			check(doc.uri);
		}, delay)
	);
}

async function check(uri) {
	const root = findRoot(uri.fsPath);
	if (!root) {
		return;
	}
	let checker = checkers.get(root);
	if (!checker || checker.dead) {
		checker = new CheckerProcess(root);
		checkers.set(root, checker);
	}
	try {
		const result = await checker.check(uri.fsPath);
		if (result.fatal) {
			output.appendLine(`${uri.fsPath}: ${result.fatal}`);
		}
		diagnostics.set(uri, toDiagnostics(uri, result.diagnostics));
	} catch (err) {
		output.appendLine(`${uri.fsPath}: ${err.message}`);
	}
}

function toDiagnostics(uri, items) {
	const doc = vscode.workspace.textDocuments.find((d) => d.uri.toString() === uri.toString());
	const hints = config().hints;
	const out = [];
	for (const item of items || []) {
		if (item.severity === 'info' && !hints) {
			continue;
		}
		const line = Math.max(0, item.line - 1);
		const col = Math.max(0, item.column - 1);
		let range = new vscode.Range(line, col, line, col + 1);
		if (doc && line < doc.lineCount) {
			// Underline the reference / directive / token that starts at the reported column
			const word = doc.getWordRangeAtPosition(new vscode.Position(line, col), /[#$!]*\{?[\w.]+(\([^)]*\))?\}?/);
			range = word || new vscode.Range(line, col, line, Math.min(col + 1, doc.lineAt(line).text.length));
		}
		const severity =
			item.severity === 'error'
				? vscode.DiagnosticSeverity.Error
				: item.severity === 'warning'
					? vscode.DiagnosticSeverity.Warning
					: vscode.DiagnosticSeverity.Information;
		const d = new vscode.Diagnostic(range, item.message, severity);
		d.source = 'velocity-json';
		out.push(d);
	}
	return out;
}

/** Long-lived `check.sh --server`: one path per stdin line, one JSON result per stdout line, in order. */
class CheckerProcess {
	constructor(root) {
		this.root = root;
		this.dead = false;
		this.queue = [];
		this.buffer = '';
		this.stderr = '';
		this.ready = new Promise((resolve, reject) => {
			this.resolveReady = resolve;
			this.rejectReady = reject;
		});
		output.appendLine(`Starting checker in ${root}`);
		this.proc = cp.spawn(path.join(root, CHECKER), ['--server'], { cwd: root });
		this.proc.stdout.setEncoding('utf8');
		this.proc.stdout.on('data', (chunk) => this.onData(chunk));
		this.proc.stderr.on('data', (chunk) => {
			this.stderr += chunk;
			output.append(String(chunk));
		});
		this.proc.on('error', (err) => this.fail(err.message));
		this.proc.on('exit', (code) => this.fail(`checker exited (${code})${this.stderr ? ': ' + this.stderr.trim().split('\n').pop() : ''}`));
	}

	onData(chunk) {
		this.buffer += chunk;
		let nl;
		while ((nl = this.buffer.indexOf('\n')) >= 0) {
			const line = this.buffer.slice(0, nl).trim();
			this.buffer = this.buffer.slice(nl + 1);
			if (!line) {
				continue;
			}
			let msg;
			try {
				msg = JSON.parse(line);
			} catch {
				output.appendLine(line);
				continue;
			}
			if (msg.ready) {
				this.resolveReady();
				continue;
			}
			const pending = this.queue.shift();
			if (pending) {
				pending.resolve(msg);
			}
		}
	}

	fail(message) {
		if (this.dead) {
			return;
		}
		this.dead = true;
		output.appendLine(message);
		this.rejectReady(new Error(message));
		this.queue.splice(0).forEach((p) => p.reject(new Error(message)));
		vscode.window.showWarningMessage(`Velocity JSON checker stopped: ${message}`, 'Show Output').then((pick) => {
			if (pick) {
				output.show();
			}
		});
	}

	async check(file) {
		await this.ready;
		return new Promise((resolve, reject) => {
			this.queue.push({ resolve, reject });
			this.proc.stdin.write(file + '\n');
		});
	}

	dispose() {
		this.dead = true;
		try {
			this.proc.kill();
		} catch {
			// already gone
		}
	}
}

/** Command: check every template in each eme-server checkout of the workspace into the Problems panel. */
async function checkAll() {
	const roots = new Set();
	for (const folder of vscode.workspace.workspaceFolders || []) {
		const root = fs.existsSync(path.join(folder.uri.fsPath, CHECKER)) ? folder.uri.fsPath : null;
		if (root) {
			roots.add(root);
		}
	}
	if (roots.size === 0) {
		vscode.window.showInformationMessage(`No workspace folder contains ${CHECKER}.`);
		return;
	}
	await vscode.window.withProgress(
		{ location: vscode.ProgressLocation.Notification, title: 'Velocity JSON: checking all templates' },
		() =>
			Promise.all(
				[...roots].map(
					(root) =>
						new Promise((resolve) => {
							cp.execFile(path.join(root, CHECKER), ['--json'], { cwd: root, maxBuffer: 64 * 1024 * 1024 }, (err, stdout, stderr) => {
								if (stderr) {
									output.append(stderr);
								}
								let errors = 0;
								let warnings = 0;
								for (const line of stdout.split('\n')) {
									if (!line.trim()) {
										continue;
									}
									try {
										const result = JSON.parse(line);
										const uri = vscode.Uri.file(path.resolve(root, result.file));
										const diags = toDiagnostics(uri, result.diagnostics);
										errors += diags.filter((d) => d.severity === vscode.DiagnosticSeverity.Error).length;
										warnings += diags.filter((d) => d.severity === vscode.DiagnosticSeverity.Warning).length;
										diagnostics.set(uri, diags);
									} catch {
										output.appendLine(line);
									}
								}
								vscode.window.showInformationMessage(`Velocity JSON: ${errors} error(s), ${warnings} warning(s). See the Problems panel.`);
								resolve();
							});
						})
				)
			)
	);
}

module.exports = { activate, deactivate };
