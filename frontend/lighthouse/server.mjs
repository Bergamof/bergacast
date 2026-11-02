// Serves the SPA build like the production image (headers of docker/static-web-server.toml, precompressed files,
// index.html fallback) together with the fake API, for the Lighthouse audit (lighthouserc.json).
// Usage: node lighthouse/server.mjs <build-dir> <port> <request-log> [--signed-in]
// --signed-in: /config.js stores an admin session, as after a login (sessionStorage is per tab, so Lighthouse
// cannot log in through the login page). Every API request is appended to <request-log> ("METHOD path status"),
// so that CI can check the audited pages really rendered data rather than an error page.
import { appendFileSync, createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, resolve, sep } from 'node:path';
import { COVER_IMAGE, answer } from './fake-api.mjs';

const [buildArgument, portArgument, requestLog, ...flags] = process.argv.slice(2);
if (!buildArgument || !portArgument || !requestLog) {
	console.error('Usage: node lighthouse/server.mjs <build-dir> <port> <request-log> [--signed-in]');
	process.exit(1);
}
const buildDirectory = resolve(buildArgument);
const port = Number(portArgument);
const signedIn = flags.includes('--signed-in');
const SESSION_LIFETIME_MILLISECONDS = 3_600_000;

const CONTENT_TYPES = {
	'.html': 'text/html; charset=utf-8',
	'.js': 'text/javascript',
	'.css': 'text/css',
	'.svg': 'image/svg+xml',
	'.png': 'image/png',
	'.ico': 'image/x-icon',
	'.txt': 'text/plain',
	'.json': 'application/json'
};
const COMMON_HEADERS = {
	'Cache-Control': 'no-cache',
	'X-Content-Type-Options': 'nosniff',
	'Referrer-Policy': 'no-referrer',
	'X-Robots-Tag': 'noindex, nofollow',
	'X-Frame-Options': 'DENY',
	'Strict-Transport-Security': 'max-age=63072000; includeSubDomains; preload',
	'Content-Security-Policy': "frame-ancestors 'self'"
};

function serveApi(request, response, url) {
	const { status, body, headers } = answer(request.method, url);
	appendFileSync(requestLog, `${request.method} ${url.pathname} ${status}\n`);
	response.writeHead(status, {
		'Content-Type': status >= 400 ? 'application/problem+json' : 'application/hal+json',
		...headers
	});
	response.end(JSON.stringify(body));
}

function serveConfig(response) {
	let script = "globalThis.bergacastConfig = { apiUrl: '' };\n";
	if (signedIn) {
		const session = { accessToken: 'lighthouse.fake.jwt', username: 'admin' };
		script += `sessionStorage.setItem('bergacast.session', JSON.stringify({ ...${JSON.stringify(session)}, expiresAt: Date.now() + ${SESSION_LIFETIME_MILLISECONDS} }));\n`;
	}
	response.writeHead(200, { ...COMMON_HEADERS, 'Content-Type': 'text/javascript' });
	response.end(script);
}

function staticFile(pathname) {
	const candidate = join(buildDirectory, decodeURIComponent(pathname));
	const inside = candidate.startsWith(buildDirectory + sep);
	return inside && existsSync(candidate) && statSync(candidate).isFile()
		? candidate
		: join(buildDirectory, 'index.html');
}

function serveStatic(request, response, url) {
	let file = staticFile(url.pathname);
	const headers = {
		...COMMON_HEADERS,
		'Content-Type': CONTENT_TYPES[extname(file)] ?? 'application/octet-stream'
	};
	if (url.pathname.startsWith('/_app/immutable/')) {
		headers['Cache-Control'] = 'public, max-age=31536000, immutable';
	}
	const acceptedEncodings = request.headers['accept-encoding'] ?? '';
	for (const [encoding, suffix] of [
		['br', '.br'],
		['gzip', '.gz']
	]) {
		if (acceptedEncodings.includes(encoding) && existsSync(file + suffix)) {
			Object.assign(headers, { 'Content-Encoding': encoding, Vary: 'Accept-Encoding' });
			file += suffix;
			break;
		}
	}
	response.writeHead(200, headers);
	createReadStream(file).pipe(response);
}

createServer((request, response) => {
	const url = new URL(request.url, `http://localhost:${port}`);
	if (url.pathname.startsWith('/api/')) {
		serveApi(request, response, url);
	} else if (url.pathname.startsWith('/media/') && url.pathname.endsWith('/image.png')) {
		response.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'max-age=3600' });
		response.end(COVER_IMAGE);
	} else if (url.pathname.startsWith('/media/')) {
		response.writeHead(404);
		response.end();
	} else if (url.pathname === '/config.js') {
		serveConfig(response);
	} else {
		serveStatic(request, response, url);
	}
}).listen(port, () =>
	console.log(`listening on http://localhost:${port}${signedIn ? ' (signed in)' : ''}`)
);
