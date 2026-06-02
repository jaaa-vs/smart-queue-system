// Smart Queue v4.0 - Enhanced UX
const API_BASE = '/api';

// DOM elements (lazy loaded to avoid race conditions)
let $currentDisplay = null;
let $serveBtn = null;
let $status = null;
let currentServingQueueNum = null;
let isFocusMode = false;

function initDOMElements() {
    if (!$currentDisplay) {
        $currentDisplay = document.getElementById('current-display');
        $serveBtn = document.getElementById('serve-btn');
        $status = document.getElementById('status');
    }
}

function fitCurrentDisplayText() {
    initDOMElements();
    if (!$currentDisplay) return;

    const maxSize = Math.min(window.innerWidth * 0.12, window.innerHeight * 0.2);
    const minSize = 32;
    let size = Math.max(minSize, maxSize);

    $currentDisplay.style.fontSize = `${size}px`;

    while (($currentDisplay.scrollWidth > $currentDisplay.clientWidth - 8 || $currentDisplay.scrollHeight > $currentDisplay.clientHeight - 8) && size > minSize) {
        size -= 2;
        $currentDisplay.style.fontSize = `${size}px`;
    }
}

function startLiveClock() {
    const clockEl = document.getElementById('live-clock');
    if (!clockEl) return;

    const render = () => {
        const now = new Date();
        clockEl.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    };

    render();
    setInterval(render, 1000);
}

function updateInsights(waiting, nextBatch, stats, insights) {
    const loadEl = document.getElementById('insight-load');
    const flowEl = document.getElementById('insight-flow');
    const modeEl = document.getElementById('insight-mode');

    const waitingCount = (waiting || []).length;
    const nextCount = (nextBatch || []).length;
    const served = Number(stats?.servedToday || 0);

    if (loadEl) {
        if (waitingCount >= 5) loadEl.textContent = 'High';
        else if (waitingCount >= 3) loadEl.textContent = 'Moderate';
        else loadEl.textContent = 'Stable';
    }

    if (flowEl) {
        if (served >= 30) flowEl.textContent = 'Excellent';
        else if (served >= 10) flowEl.textContent = 'Strong';
        else if (waitingCount > 0 || nextCount > 0) flowEl.textContent = 'Active';
        else flowEl.textContent = 'Ready';
    }

    if (modeEl) modeEl.textContent = isFocusMode ? 'Focus' : 'Normal';

    if (insights) {
        renderSmartCoach(insights);
    }
}

function renderSmartCoach(insights) {
    const throughputEl = document.getElementById('coach-throughput');
    const slotEl = document.getElementById('coach-slot');
    const recommendationEl = document.getElementById('coach-recommendation');
    const etasEl = document.getElementById('coach-etas');
    const container = document.getElementById('smart-coach');

    if (!throughputEl || !slotEl || !recommendationEl || !etasEl || !container) return;

    const throughput = Number(insights.throughputPerHour || 0);
    const slotSeconds = Number(insights.slotSeconds || 120);
    const recommendation = String(insights.recommendation || 'Flow is healthy. Keep current staffing.');
    const level = String(insights.recommendationLevel || 'low').toLowerCase();
    const estimates = Array.isArray(insights.waitEstimates) ? insights.waitEstimates : [];

    throughputEl.textContent = throughput.toFixed(1);
    slotEl.textContent = `${slotSeconds}s`;
    recommendationEl.textContent = recommendation;

    container.classList.remove('coach-low', 'coach-medium', 'coach-high');
    container.classList.add(`coach-${level}`);

    if (!estimates.length) {
        etasEl.innerHTML = '<span class="eta-chip">No waiting tickets</span>';
        return;
    }

    etasEl.innerHTML = estimates.slice(0, 5).map(item => {
        const num = escapeHtml(item.num || '-');
        const eta = escapeHtml(item.etaLabel || 'soon');
        return `<span class="eta-chip"><strong>${num}</strong> ${eta}</span>`;
    }).join('');
}

function toggleFocusMode() {
    isFocusMode = !isFocusMode;
    document.body.classList.toggle('focus-mode', isFocusMode);
    updateInsights([], [], { servedToday: document.getElementById('served-count')?.textContent || 0 });
    fitCurrentDisplayText();
}

// Config cache
let systemConfig = null;

// Load config on startup
async function loadConfig() {
    try {
        const res = await fetch(API_BASE + '/config');
        if (res.ok) {
            systemConfig = await res.json();
            applyConfig();
        } else {
            console.error('Config HTTP error:', res.status);
            showConfigError('Server returned ' + res.status);
        }
    } catch (e) {
        console.error('Config load failed', e);
        showConfigError('Cannot connect to server');
    }
}

function showConfigError(msg) {
    const settingsContainer = document.getElementById('settings-content');
    if (settingsContainer) {
        settingsContainer.innerHTML = `<div style="color:#ff6b6b;padding:1rem;"><i class="fas fa-exclamation-triangle"></i> Failed to load config: ${msg}</div>`;
    }
}

function applyConfig() {
    if (!systemConfig) return;
    // Update header title
    const org = systemConfig.organization;
    if (org) {
        document.title = org.org_name + ' - Queue Dashboard';
        const logo = document.querySelector('.logo');
        if (logo) {
            logo.innerHTML = escapeHtml(org.org_name) + ' <span style="font-size:0.7em">v4.0</span>';
        }
    }
    populateDropdowns();
    renderSettingsEditable();
}

function renderSettingsEditable() {
    const settingsContainer = document.getElementById('settings-content');
    if (!settingsContainer || !systemConfig) return;

    const org = systemConfig.organization || {};
    const svcs = systemConfig.services || [];
    const wins = systemConfig.windows || [];

    let html = `
    <div class="settings-section">
        <h3><i class="fas fa-building"></i> Organization</h3>
        <div class="edit-form">
            <input type="text" id="org-name" value="${escapeHtml(org.org_name || '')}" placeholder="Organization Name">
            <input type="text" id="org-address" value="${escapeHtml(org.address || '')}" placeholder="Address">
            <input type="text" id="org-tagline" value="${escapeHtml(org.tagline || '')}" placeholder="Tagline">
            <button class="btn btn-primary btn-small" onclick="saveOrg()">Save Organization</button>
        </div>
    </div>

    <div class="settings-section">
        <h3><i class="fas fa-list"></i> Services</h3>
        <div class="edit-form">
            <div class="input-row">
                <input type="text" id="svc-code" placeholder="Code (PAY)" maxlength="10">
                <input type="text" id="svc-name" placeholder="Service Name">
                <input type="text" id="svc-desc" placeholder="Description">
                <button class="btn btn-success btn-small" onclick="addService()">Add Service</button>
            </div>
            <div id="services-list" class="settings-grid"></div>
        </div>
    </div>

    <div class="settings-section">
        <h3><i class="fas fa-desktop"></i> Windows</h3>
        <div class="edit-form">
            <div class="input-row">
                <input type="number" id="win-num" placeholder="Window #">
                <input type="text" id="win-name" placeholder="Window Name">
                <input type="text" id="win-services" placeholder="Services (PAY,REG)">
                <button class="btn btn-success btn-small" onclick="addWindow()">Add Window</button>
            </div>
            <div id="windows-list" class="settings-grid"></div>
        </div>
    </div>

    <div class="settings-actions">
            <button class="btn btn-warning" onclick="showStartupConfigModal()">Test Startup Config</button>
            <button class="btn btn-primary" onclick="loadConfig()">Reload from Server</button>
            <button class="btn btn-success" onclick="saveAll()">Save All Changes</button>
        </div>`;

    settingsContainer.innerHTML = html;
    
    // Re-render lists
    renderServicesList(svcs);
    renderWindowsList(wins);
}

function renderServicesList(svcs) {
    const container = document.getElementById('services-list');
    container.innerHTML = svcs.length === 0 ? '<div class="settings-card">No services</div>' : '';
    svcs.forEach((s, index) => {
        container.innerHTML += `
            <div class="settings-card editable">
                <strong>${escapeHtml(s.service_code)}</strong> - ${escapeHtml(s.service_name)}
                <small>${escapeHtml(s.description || '')}</small>
                <div class="edit-actions">
                    <button class="btn btn-small" onclick="editService(${index})">Edit</button>
                    <button class="btn btn-danger btn-small" onclick="deleteService(${index})">Delete</button>
                </div>
            </div>`;
    });
}

function renderWindowsList(wins) {
    const container = document.getElementById('windows-list');
    container.innerHTML = wins.length === 0 ? '<div class="settings-card">No windows</div>' : '';
    wins.forEach((w, index) => {
        container.innerHTML += `
            <div class="settings-card editable">
                <strong>${escapeHtml(w.window_name)}</strong> (Win #${w.window_number})
                <small>${escapeHtml(w.service_ids || 'No services')}</small>
                <div class="edit-actions">
                    <button class="btn btn-small" onclick="editWindow(${index})">Edit</button>
                    <button class="btn btn-danger btn-small" onclick="deleteWindow(${index})">Delete</button>
                </div>
            </div>`;
    });
}

// Save functions (stubs - will POST to /api/config)
async function saveOrg() {
    const data = {
        organization: {
            org_name: document.getElementById('org-name').value,
            address: document.getElementById('org-address').value,
            tagline: document.getElementById('org-tagline').value
        },
        services: systemConfig ? systemConfig.services || [] : [],
        windows: systemConfig ? systemConfig.windows || [] : []
    };
    await saveFullConfig(data);
    showToast('Organization saved!', 'success');
}

async function addService() {
    const code = document.getElementById('svc-code').value.trim().toUpperCase();
    const name = document.getElementById('svc-name').value.trim();
    const desc = document.getElementById('svc-desc').value.trim();
    
    if (code && name) {
        if (!systemConfig.services) systemConfig.services = [];
        systemConfig.services.push({
            service_code: code,
            service_name: name,
            description: desc
        });
        renderServicesList(systemConfig.services);
        await saveFullConfig(systemConfig);
        document.getElementById('svc-code').value = '';
        document.getElementById('svc-name').value = '';
        document.getElementById('svc-desc').value = '';
        showToast(`Service ${code} added!`, 'success');
        populateDropdowns();
    } else {
        showToast('Code and name required', 'warning');
    }
}

async function deleteService(index) {
    if (confirm('Delete service?')) {
        systemConfig.services.splice(index, 1);
        renderServicesList(systemConfig.services);
        await saveFullConfig(systemConfig);
        showToast('Service deleted!', 'success');
        populateDropdowns();
    }
}

async function addWindow() {
    const num = document.getElementById('win-num').value.trim();
    const name = document.getElementById('win-name').value.trim();
    const services = document.getElementById('win-services').value.trim();
    
    if (num && name) {
        if (!systemConfig.windows) systemConfig.windows = [];
        systemConfig.windows.push({
            window_number: num,
            window_name: name,
            service_ids: services
        });
        renderWindowsList(systemConfig.windows);
        await saveFullConfig(systemConfig);
        document.getElementById('win-num').value = '';
        document.getElementById('win-name').value = '';
        document.getElementById('win-services').value = '';
        showToast(`Window ${num} added!`, 'success');
        populateDropdowns();
    } else {
        showToast('Window # and name required', 'warning');
    }
}

async function deleteWindow(index) {
    if (confirm('Delete window?')) {
        systemConfig.windows.splice(index, 1);
        renderWindowsList(systemConfig.windows);
        await saveFullConfig(systemConfig);
        showToast('Window deleted.', 'success');
        populateDropdowns();
    }
}

async function saveFullConfig(config) {
    try {
        const res = await fetch(API_BASE + '/config', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        showToast('Configuration saved.', 'success');
        loadConfig(); // Reload to verify
    } catch(e) {
        showToast('Save failed: ' + e.message, 'error');
    }
}

let configModal = null;
function showStartupConfigModal() {
    showToast('Setup complete - use Settings tab.', 'info');
}

function setupWizard() {
    showToast('Setup wizard coming soon.', 'info');
}

async function saveAll() {
    await saveOrg();
    await saveFullConfig(systemConfig);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Audio context for beeps
let audioCtx;
try {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
} catch(e) {
    console.log('Audio not supported');
}

// Beep sound
function playBeep(frequency = 800, duration = 200) {
    if (!audioCtx) return;
    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();
    
    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);
    
    oscillator.frequency.value = frequency;
    oscillator.type = 'sine';
    
    gainNode.gain.setValueAtTime(0.3, audioCtx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + duration / 1000);
    
    oscillator.start(audioCtx.currentTime);
    oscillator.stop(audioCtx.currentTime + duration / 1000);
}

// Success celebration
function celebrate() {
    playBeep(523, 150);
    playBeep(659, 150);
    playBeep(784, 300);
    
    // Confetti
    confetti({
        particleCount: 100,
        spread: 70,
        origin: { y: 0.6 }
    });
}

// Toast notification
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    
    setTimeout(() => toast.classList.add('show'), 100);
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 400);
    }, 3500);
}

// API wrapper with loading
async function apiCall(endpoint, options = {}) {
    const btn = options.btn;
    const method = options.method || 'GET';

    // Build fetch options from provided options but exclude our custom props
    const fetchOptions = { method };
    if (options.headers) fetchOptions.headers = options.headers;
    if (options.body) fetchOptions.body = options.body;
    if (options.credentials) fetchOptions.credentials = options.credentials;
    // Copy any other fetch-relevant options
    for (const k of Object.keys(options)) {
        if (k !== 'btn' && k !== 'method' && k !== 'headers' && k !== 'body' && k !== 'credentials') {
            fetchOptions[k] = options[k];
        }
    }

    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="spinner"></span> Loading...`;
    }

    try {
        const res = await fetch(API_BASE + endpoint, fetchOptions);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        // Only parse JSON if response has content (not 204 No Content)
        if (res.status === 204 || res.headers.get('content-length') === '0') {
            return null;
        }
        return await res.json();
    } catch (error) {
        showToast('API error: ' + error.message, 'error');
        throw error;
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = btn.dataset.original || btn.textContent;
        }
    }
}

function editService(index) {
    const s = systemConfig.services[index];
    document.getElementById('svc-code').value = s.service_code;
    document.getElementById('svc-name').value = s.service_name;
    document.getElementById('svc-desc').value = s.description || '';
    systemConfig.services.splice(index, 1);
    renderServicesList(systemConfig.services);
    showToast('Edit mode: modify and Add Service', 'info');
}

async function deleteService(index) {
    const service = systemConfig.services[index];
    if (confirm('Delete service ' + (service?.service_code || index) + '?')) {
        systemConfig.services.splice(index, 1);
        renderServicesList(systemConfig.services);
        await saveFullConfig(systemConfig);
        showToast('Service deleted!', 'success');
        populateDropdowns();
    }
}

function editWindow(index) {
    const w = systemConfig.windows[index];
    document.getElementById('win-num').value = w.window_number;
    document.getElementById('win-name').value = w.window_name;
    document.getElementById('win-services').value = w.service_ids || '';
    systemConfig.windows.splice(index, 1);
    renderWindowsList(systemConfig.windows);
    showToast('Edit mode: modify and Add Window', 'info');
}

async function deleteWindow(index) {
    const win = systemConfig.windows[index];
    if (confirm('Delete window ' + (win?.window_number || index) + '?')) {
        systemConfig.windows.splice(index, 1);
        renderWindowsList(systemConfig.windows);
        await saveFullConfig(systemConfig);
        showToast('Window deleted!', 'success');
        populateDropdowns();
    }
}





// Load queue data
async function refreshData() {
    try {
        initDOMElements();
        const [waiting, calling, nextbatch, stats, insights] = await Promise.all([
            fetch(API_BASE + '/waiting').then(r => r.json()),
            fetch(API_BASE + '/calling').then(r => r.json()),
            fetch(API_BASE + '/nextbatch').then(r => r.json()),
            fetch(API_BASE + '/stats').then(r => r.json()),
            fetch(API_BASE + '/insights').then(r => r.json())
        ]);

        // Update current
        if ($currentDisplay) {
            if (calling.data?.length) {
                const cur = calling.data[0];
                if (typeof cur === 'object' && cur !== null) {
                    currentServingQueueNum = cur.num || null;
                    $currentDisplay.textContent = `${cur.num} — Window ${cur.window} (${cur.service})`;
                } else {
                    currentServingQueueNum = String(cur || '').trim() || null;
                    $currentDisplay.textContent = String(cur);
                }
                $currentDisplay.classList.add('serving', 'pulse-glow');
                if ($serveBtn) $serveBtn.disabled = false;
            } else {
                currentServingQueueNum = null;
                $currentDisplay.textContent = '-';
                $currentDisplay.classList.remove('serving', 'pulse-glow');
                if ($serveBtn) $serveBtn.disabled = true;
            }
            fitCurrentDisplayText();
        }

        // Update lists
        updateList('waiting-list', waiting.data || []);
        updateList('next-list', nextbatch.data || []);

        // Update stats
        updateStats(stats);
        updateInsights(waiting.data || [], nextbatch.data || [], stats, insights);

        const historyTab = document.getElementById('history');
        if (historyTab && historyTab.classList.contains('active')) {
            refreshHistory();
        }

        showStatus('Live', 'online');
    } catch (error) {
        showStatus('Connection Error', 'offline');
        showToast('Server unavailable', 'error');
    }
}

// Update list
function updateList(id, data) {
    const ul = document.getElementById(id);
    if (!ul) return;
    ul.innerHTML = '';
    data.slice(0, 5).forEach((item, index) => {
        const li = document.createElement('li');
        li.style.animationDelay = `${index * 0.1}s`;
        if (typeof item === 'object' && item !== null) {
            const svc = item.service || '';
            const win = item.window || '';
            li.innerHTML = `<strong class="queue-num">${escapeHtml(item.num)}</strong> <span class="badge">W${escapeHtml(String(win))} ${escapeHtml(svc)}</span>`;
            li.title = `${item.num} — Window ${win} (${svc})`;
            li.onclick = () => showToast(`${item.num} • Window ${win} • ${svc}`, 'info');
        } else {
            li.textContent = String(item);
        }
        ul.appendChild(li);
    });
}

// Update stats
function updateStats(stats) {
    const waitingEl = document.getElementById('waiting-count');
    const nextEl = document.getElementById('next-count');
    const servedEl = document.getElementById('served-count');
    if (waitingEl) waitingEl.textContent = String(stats.waiting || 0);
    if (nextEl) nextEl.textContent = String(stats.nextBatch || 0);
    if (servedEl) servedEl.textContent = String(stats.servedToday || 0);
}

async function refreshHistory() {
    const body = document.querySelector('#history-table tbody');
    if (!body) return;

    try {
        const history = await apiCall('/history');
        const rows = history?.data || [];

        body.innerHTML = rows.map(row => {
            const status = String(row.status || 'WAITING').toLowerCase();
            return `
                <tr>
                    <td><strong>${escapeHtml(row.num || '-')}</strong></td>
                    <td>${escapeHtml(row.gen || '-')}</td>
                    <td>${escapeHtml(row.called || '-')}</td>
                    <td><span class="status-badge status-${escapeHtml(status)}">${escapeHtml(row.status || '-')}</span></td>
                </tr>`;
        }).join('');

        if (rows.length === 0) {
            body.innerHTML = '<tr><td colspan="4" style="text-align:center; opacity:0.8;">No queue history yet</td></tr>';
        }
    } catch (e) {
        body.innerHTML = '<tr><td colspan="4" style="text-align:center; color:#ff6b6b;">Unable to load history</td></tr>';
    }
}

// Status
function showStatus(msg, type) {
    if (!$status) initDOMElements();
    if ($status) {
        $status.textContent = msg;
        $status.className = `status-${type}`;
    }
}

// Button actions
async function generateNumber() {
    const windowId = document.getElementById('window-select').value;
    const serviceCode = document.getElementById('service-select').value;
    
    if (!windowId || !serviceCode) {
        showToast('Select window and service first!', 'warning');
        return;
    }
    
    const btn = event?.target || document.querySelector('[onclick*="generateNumber"]');
    if (!btn) return;
    
    const originalHtml = btn.dataset.original || btn.innerHTML;
    try {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner"></span> Issuing ticket...';
        
        await fetch(API_BASE + '/generate', {
            method: 'POST',
            headers: {
                'service': serviceCode,
                'window': windowId
            }
        });
        showToast('Ticket issued for ' + serviceCode + ' — Window ' + windowId + '.', 'success');
        refreshData();
        populateDropdowns(); // Refresh lists
    } catch (e) {
        showToast('Failed to issue ticket: ' + e.message, 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = originalHtml;
        }
    }
}

function populateDropdowns() {
    if (!systemConfig) return;
    
    const windows = systemConfig.windows || [];
    const services = systemConfig.services || [];
    
    // Windows dropdown
    const windowSelect = document.getElementById('window-select');
    if (!windowSelect) return;
    windowSelect.innerHTML = '<option value="">Select Window</option>';
    windows.forEach(w => {
        const option = document.createElement('option');
        option.value = w.window_number;
        option.textContent = `Window ${w.window_number}: ${w.window_name} [${w.service_ids}]`;
        windowSelect.appendChild(option);
    });
    
    // Services dropdown
    const serviceSelect = document.getElementById('service-select');
    if (!serviceSelect) return;
    serviceSelect.innerHTML = '<option value="">Select Service</option>';
    services.forEach(s => {
        const option = document.createElement('option');
        option.value = s.service_code;
        option.textContent = `${s.service_code} - ${s.service_name}`;
        serviceSelect.appendChild(option);
    });
}

async function callNext() {
    try {
        playBeep();
        await apiCall('/callnext', { method: 'POST', btn: event?.target });
        showToast('Next ticket called.', 'success');
        refreshData();
    } catch {}
}

async function serveCurrent() {
    const current = (currentServingQueueNum || $currentDisplay?.textContent || '').trim();
    if (!current || current === '-') return;
    const queueNum = current.split(' ')[0];
    const btn = event?.target || $serveBtn;
    
    try {
        await apiCall(`/serve/${encodeURIComponent(queueNum)}`, { method: 'POST', btn });
        showToast(`${queueNum} served.`, 'success');
        refreshData();
    } catch {}
}

async function resetQueue() {
    if (!confirm('Clear all waiting queues? This cannot be undone.')) return;
    
    try {
        await apiCall('/reset', { method: 'POST', btn: event?.target });
        showToast('Queue has been reset.', 'warning');
        refreshData();
    } catch {}
}

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    switch(e.code) {
        case 'KeyG': generateNumber(); break;
        case 'KeyN': callNext(); break;
        case 'KeyS': serveCurrent(); break;
        case 'KeyR': resetQueue(); break;
    }
});

// Init
document.addEventListener('DOMContentLoaded', async () => {
    // Save original button text
    document.querySelectorAll('.btn').forEach(btn => {
        btn.dataset.original = btn.innerHTML;
    });

    await loadConfig();
    
    // BYPASS: Config popup DISABLED - UI always shows (no blank screen)
    console.log('UI Bypass: Config popup disabled');
    const configModalEl = document.getElementById('config-modal');
    if (configModalEl) {
        configModalEl.style.display = 'none';
    }
    
    startLiveClock();
    refreshHistory();
    refreshData();
    setInterval(refreshData, 2000);
    window.addEventListener('resize', fitCurrentDisplayText);
});

// Confetti (CDN)
function confetti(options) {
    // Simple canvas confetti
    const canvas = document.createElement('canvas');
    canvas.id = 'confetti-canvas';
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    document.body.appendChild(canvas);
    
    const ctx = canvas.getContext('2d');
    const particles = [];
    
    for (let i = 0; i < (options.particleCount || 50); i++) {
        particles.push({
            x: Math.random() * canvas.width,
            y: -10,
            size: Math.random() * 8 + 4,
            speed: Math.random() * 3 + 1,
            color: ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b'][Math.floor(Math.random()*5)],
            rotation: Math.random() * 360,
            rotSpeed: Math.random() * 10 - 5
        });
    }
    
    function animate() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        
        particles.forEach(p => {
            ctx.save();
            ctx.translate(p.x, p.y);
            ctx.rotate(p.rotation * Math.PI / 180);
            ctx.fillStyle = p.color;
            ctx.fillRect(-p.size/2, -p.size/2, p.size, p.size);
            ctx.restore();
            
            p.y += p.speed;
            p.rotation += p.rotSpeed;
            p.x += Math.sin(p.y * 0.01) * 2;
        });
        
        if (particles.some(p => p.y < canvas.height)) {
            requestAnimationFrame(animate);
        } else {
            canvas.remove();
        }
    }
    
    animate();
}

// Service Worker for offline (future)
if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js');
}
