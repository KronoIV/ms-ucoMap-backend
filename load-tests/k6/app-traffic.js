// Prueba de carga del backend UCO Map con k6.
// Simula el tráfico real de la app móvil (arranque, ping de sesión y recorridos)
// y, en paralelo, a administradores consultando el panel.
//
// Variables:
//   BASE_URL  URL del backend (por defecto http://localhost:8080)
//   TYPE      smoke | load | stress | spike (por defecto load)
//   ADMIN_EMAIL / ADMIN_PASSWORD  admin sembrado por AdminInitializer en el entorno de carga

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const TYPE = __ENV.TYPE || 'load';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@admin.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || '1234';

// Producción tiene rate limit por IP y escribe en la base real: exigir confirmación explícita
if (/onrender\.com|netlify\.app/.test(BASE_URL) && __ENV.ALLOW_PROD !== '1') {
    throw new Error(`BASE_URL apunta a producción (${BASE_URL}). Usa ALLOW_PROD=1 si realmente lo quieres.`);
}

const PROFILES = {
    smoke:  [{ duration: '30s', target: 2 }],
    load:   [{ duration: '1m', target: 50 }, { duration: '3m', target: 50 }, { duration: '30s', target: 0 }],
    stress: [{ duration: '2m', target: 100 }, { duration: '2m', target: 200 }, { duration: '2m', target: 300 },
             { duration: '1m', target: 0 }],
    spike:  [{ duration: '20s', target: 10 }, { duration: '10s', target: 300 }, { duration: '1m', target: 300 },
             { duration: '20s', target: 10 }, { duration: '20s', target: 0 }],
    // Sube sin parar hasta que se rompa un umbral (la prueba se aborta sola)
    breakpoint: [{ duration: '12m', target: Number(__ENV.MAX_VUS || 3000) }],
};
if (!PROFILES[TYPE]) throw new Error(`TYPE desconocido: ${TYPE}. Usa ${Object.keys(PROFILES).join(', ')}`);

const ABORT = TYPE === 'breakpoint';
const limit = (threshold) => (ABORT ? { threshold, abortOnFail: true, delayAbortEval: '30s' } : threshold);

export const options = {
    scenarios: {
        app_users: {
            executor: 'ramping-vus',
            exec: 'appUser',
            startVUs: 0,
            stages: PROFILES[TYPE],
            gracefulRampDown: '30s',
        },
        admin_panel: {
            executor: 'constant-vus',
            exec: 'adminPanel',
            vus: TYPE === 'smoke' ? 1 : 2,
            duration: PROFILES[TYPE].reduce((s, st) => s + parseDuration(st.duration), 0) + 's',
        },
    },
    thresholds: {
        http_req_failed: [limit('rate<0.01')],
        checks: ['rate>0.99'],
        'http_req_duration{kind:read}': [limit('p(95)<800'), 'p(99)<2000'],
        'http_req_duration{kind:write}': [limit('p(95)<1000')],
        app_startup_ms: ['p(95)<2500'],
    },
};

// Tiempo que tarda la app en tener todos los datos para mostrar la pantalla inicial
const appStartup = new Trend('app_startup_ms', true);

function parseDuration(d) {
    const n = parseInt(d, 10);
    return d.endsWith('m') ? n * 60 : n;
}

function uuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
    });
}

const UAS = [
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
    'Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36',
    'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/123.0 Mobile Safari/537.36',
];

function jsonParams(ua, name, kind) {
    return { headers: { 'Content-Type': 'application/json', 'User-Agent': ua }, tags: { name, kind } };
}

function ok(res, label) {
    return check(res, { [`${label} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
}

// ── App móvil ────────────────────────────────────────────────────────────────

export function appUser() {
    const ua = UAS[__VU % UAS.length];
    const deviceId = `load-${__VU}-${uuid().slice(0, 8)}`;
    let rooms = [];

    group('arranque de la app', () => {
        const start = Date.now();
        // La app pide estos recursos en paralelo al abrir
        const res = http.batch([
            ['GET', `${BASE_URL}/api/campus`, null, { headers: { 'User-Agent': ua }, tags: { name: 'GET /api/campus', kind: 'read' } }],
            ['GET', `${BASE_URL}/api/settings`, null, { headers: { 'User-Agent': ua }, tags: { name: 'GET /api/settings', kind: 'read' } }],
            ['GET', `${BASE_URL}/api/rooms`, null, { headers: { 'User-Agent': ua }, tags: { name: 'GET /api/rooms', kind: 'read' } }],
        ]);
        ok(res[0], 'campus');
        ok(res[1], 'settings');
        ok(res[2], 'rooms');
        appStartup.add(Date.now() - start);
        try { rooms = res[2].json('data') || []; } catch (_) { rooms = []; }

        // El navmesh es opcional: 404 cuando no se ha publicado ninguno
        const nav = http.get(`${BASE_URL}/api/navigation/navmesh`,
            { headers: { 'User-Agent': ua }, tags: { name: 'GET /api/navigation/navmesh', kind: 'read' },
              responseCallback: http.expectedStatuses(200, 404) });
        check(nav, { 'navmesh 200/404': (r) => r.status === 200 || r.status === 404 });

        ok(http.post(`${BASE_URL}/api/sessions/ping`,
            JSON.stringify({ deviceId, appVersion: 'load-test', language: 'es-CO', timezone: 'America/Bogota' }),
            jsonParams(ua, 'POST /api/sessions/ping', 'write')), 'ping');
    });

    sleep(1 + Math.random() * 3); // el usuario mira la lista

    // Seis de cada diez usuarios inician un recorrido
    if (Math.random() >= 0.6) return;

    const room = rooms.length ? rooms[Math.floor(Math.random() * rooms.length)] : { id: 'co101', name: 'CO 101', category: 'CO' };
    const tripId = uuid();
    const base = {
        tripId, deviceId, roomId: String(room.roomId || room.id || '').slice(0, 64),
        roomName: String(room.name || 'Salón').slice(0, 120), building: String(room.category || '').slice(0, 60),
        startMode: 'outdoor', startDistanceM: 120, startAccuracyM: 8,
    };
    const tripParams = jsonParams(ua, 'POST /api/trips', 'write');

    group('recorrido', () => {
        const startedAt = Date.now();
        ok(http.post(`${BASE_URL}/api/trips`, JSON.stringify({ ...base, status: 'IN_PROGRESS' }), tripParams), 'trip start');

        // La ruta exterior también sale del grafo
        const graph = http.batch([
            ['GET', `${BASE_URL}/api/graph/nodes`, null, { headers: { 'User-Agent': ua }, tags: { name: 'GET /api/graph/nodes', kind: 'read' } }],
            ['GET', `${BASE_URL}/api/graph/edges`, null, { headers: { 'User-Agent': ua }, tags: { name: 'GET /api/graph/edges', kind: 'read' } }],
        ]);
        ok(graph[0], 'graph nodes');
        ok(graph[1], 'graph edges');

        // Heartbeats acelerados (en la app son cada 60 s)
        for (let i = 0; i < 2; i++) {
            sleep(2 + Math.random() * 2);
            ok(http.post(`${BASE_URL}/api/trips`, JSON.stringify({ ...base, status: 'IN_PROGRESS' }), tripParams), 'trip heartbeat');
        }

        const arrived = Math.random() < 0.8;
        ok(http.post(`${BASE_URL}/api/trips`, JSON.stringify({
            ...base,
            status: arrived ? 'ARRIVED' : 'ABANDONED',
            endReason: arrived ? 'ar-arrival' : 'closed',
            durationMs: Date.now() - startedAt,
            outdoorRouteM: 110, indoorRouteM: 25, modeSwitches: 1, vpsFailures: 0, usedAR: arrived,
        }), tripParams), 'trip end');
    });
}

// ── Panel de administración ──────────────────────────────────────────────────

export function setup() {
    const res = http.post(`${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/auth/login', kind: 'write' } });
    if (res.status !== 200) {
        console.warn(`Login de admin falló (${res.status}); el escenario admin_panel se omitirá`);
        return { token: null };
    }
    return { token: res.json('data.token') };
}

export function adminPanel(data) {
    if (!data.token) { sleep(5); return; }
    const params = (name) => ({ headers: { Authorization: `Bearer ${data.token}` }, tags: { name, kind: 'read' } });

    // GET /api/trips además expira recorridos abandonados: es la consulta más pesada del panel
    ok(http.get(`${BASE_URL}/api/trips`, params('GET /api/trips')), 'admin trips');
    ok(http.get(`${BASE_URL}/api/sessions/stats`, params('GET /api/sessions/stats')), 'admin stats');
    ok(http.get(`${BASE_URL}/api/sessions`, params('GET /api/sessions')), 'admin sessions');
    sleep(5);
}
